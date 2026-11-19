package com.green.keyboard_project.service.payment;

import com.green.keyboard_project.dev.tossorders.DevTossOrder;
import com.green.keyboard_project.dto.payment.PaymentConfirmationRequest;
import com.green.keyboard_project.dto.payment.PaymentConfirmationResponse;
import com.green.keyboard_project.exceptions.PaymentConfirmationException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;

/** 한 프로세스에서 사용하는 개발 승인 기록이며 외부 결과가 미확정인 기록은 임의로 제거하지 않습니다. */
public final class InMemoryPaymentConfirmationStore {
    private final Map<String, Entry> orders = new HashMap<>();
    private final Map<String, String> keyOwners = new HashMap<>();
    private final int capacity;

    private static final class Entry {
        final String keyDigest;
        final long amount;
        final String idempotencyKey;
        boolean busy = true;
        String status = "CONFIRMING";
        PaymentConfirmationResponse result;
        PaymentConfirmationException failure;
        // 키 원문 대신 비교용 해시를 저장하고 주문별로 고정된 멱등키를 만듭니다.
        Entry(PaymentConfirmationRequest request) {
            keyDigest = digest(request.paymentKey());
            amount = request.amount();
            idempotencyKey = "dev-confirm-" + digest(request.orderId());
        }
    }

    public static final class Lease {
        private final Entry entry;
        private final boolean lookupOnly;
        private final PaymentConfirmationResponse immediate;
        // 현재 요청이 외부 승인·조회·기존 결과 반환 중 어느 경로를 사용할지 묶습니다.
        private Lease(Entry entry, boolean lookupOnly, PaymentConfirmationResponse immediate) {
            this.entry = entry; this.lookupOnly = lookupOnly; this.immediate = immediate;
        }
        // 미확정 결제의 결과 조회만 허용되는 요청인지 알려줍니다.
        public boolean lookupOnly() { return lookupOnly; }
        // 동일 요청의 저장된 결과나 진행 상태를 즉시 반환할 수 있는지 알려줍니다.
        public PaymentConfirmationResponse immediate() { return immediate; }
        // 같은 주문의 외부 승인에 사용할 고정된 중복 처리 방지 값을 반환합니다.
        public String idempotencyKey() { return entry.idempotencyKey; }
        // 처음 서버 주문과 대조해 확정한 금액을 재확인에도 사용합니다.
        public long serverAmount() { return entry.amount; }
    }

    // 제한된 개수만 저장하되 미확정 결제를 공간 확보 때문에 임의 삭제하지 않습니다.
    public InMemoryPaymentConfirmationStore(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Invalid confirmation capacity");
        this.capacity = capacity;
    }

    // 동시에 같은 주문을 처리해도 하나만 외부로 진행시키고, 다른 키·금액이나 주문 간 키 재사용은 충돌로 차단합니다.
    public synchronized Lease acquire(PaymentConfirmationRequest request, Supplier<DevTossOrder> source) {
        Entry existing = orders.get(request.orderId());
        String fingerprint = digest(request.paymentKey());
        // 원래 개발 주문이 만료돼도 이미 시작한 승인은 기록된 금액과 키를 기준으로 조회합니다. 서버 재시작 시 이 기록도 사라집니다.
        if (existing != null) {
            if (!existing.keyDigest.equals(fingerprint) || existing.amount != request.amount()) throw conflict();
            if (existing.result != null) return new Lease(existing, false, existing.result);
            if (existing.failure != null) throw existing.failure;
            if (existing.busy) return new Lease(existing, false, PaymentConfirmationResponse.pending(request, existing.status));
            existing.busy = true;
            return new Lease(existing, true, null); // 미확정 결과는 조회로만 확인하며 승인 요청을 다시 보내지 않습니다.
        }
        // 다른 주문에 이미 사용된 결제 키를 재사용하면 승인 대상이 섞이므로 차단합니다.
        if (keyOwners.containsKey(fingerprint)) throw conflict();
        if (orders.size() >= capacity) throw new PaymentConfirmationException(429,
                "PAYMENT_CONFIRM_CAPACITY_EXCEEDED", "개발 승인 저장 한도에 도달했습니다. 미확정 결과를 먼저 확인하세요.");
        DevTossOrder saved;
        try { saved = source.get(); }
        catch (ResponseStatusException error) {
            throw new PaymentConfirmationException(404, "DEV_ORDER_NOT_FOUND", "개발 주문이 없거나 만료되었습니다.");
        }
        if (!saved.orderId().equals(request.orderId()) || !"KRW".equals(saved.currency())
                || !"PENDING".equals(saved.status())) throw conflict();
        if (saved.amount() != request.amount()) throw new PaymentConfirmationException(409,
                "PAYMENT_AMOUNT_MISMATCH", "서버 주문금액과 승인 요청 금액이 일치하지 않습니다.");
        Entry entry = new Entry(request);
        orders.put(request.orderId(), entry);
        keyOwners.put(fingerprint, request.orderId());
        return new Lease(entry, false, null);
    }

    // 확인된 완료 또는 입금 대기 결과를 저장해 동일 요청이 외부 승인을 반복하지 않게 합니다.
    public synchronized void complete(Lease lease, PaymentConfirmationResponse result) {
        lease.entry.result = result; lease.entry.status = result.paymentStatus(); lease.entry.busy = false;
    }
    // 확정적으로 구분한 오류를 기록하여 같은 실패 요청을 무작정 다시 승인하지 않게 합니다.
    public synchronized void fail(Lease lease, PaymentConfirmationException error) {
        lease.entry.failure = error; lease.entry.status = "FAILED"; lease.entry.busy = false;
    }
    // 미확정 상태에서는 진행 잠금만 해제하고 다음 요청이 조회만 하도록 상태를 남깁니다.
    public synchronized void unknown(Lease lease) {
        lease.entry.status = "UNKNOWN"; lease.entry.busy = false;
    }

    // 서로 다른 승인 시도가 한 주문에 섞였음을 일관된 충돌 오류로 알립니다.
    private static PaymentConfirmationException conflict() {
        return new PaymentConfirmationException(409, "PAYMENT_REQUEST_CONFLICT", "기존 승인 요청과 일치하지 않습니다.");
    }
    // 결제 키를 직접 저장하지 않고 비교할 수 있도록 단방향 해시를 만듭니다.
    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
