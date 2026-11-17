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

/** Single-process development ledger. Never evict an unresolved external payment. */
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
        private Lease(Entry entry, boolean lookupOnly, PaymentConfirmationResponse immediate) {
            this.entry = entry; this.lookupOnly = lookupOnly; this.immediate = immediate;
        }
        public boolean lookupOnly() { return lookupOnly; }
        public PaymentConfirmationResponse immediate() { return immediate; }
        public String idempotencyKey() { return entry.idempotencyKey; }
        public long serverAmount() { return entry.amount; }
    }

    public InMemoryPaymentConfirmationStore(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Invalid confirmation capacity");
        this.capacity = capacity;
    }

    public synchronized Lease acquire(PaymentConfirmationRequest request, Supplier<DevTossOrder> source) {
        Entry existing = orders.get(request.orderId());
        String fingerprint = digest(request.paymentKey());
        if (existing != null) {
            if (!existing.keyDigest.equals(fingerprint) || existing.amount != request.amount()) throw conflict();
            if (existing.result != null) return new Lease(existing, false, existing.result);
            if (existing.failure != null) throw existing.failure;
            if (existing.busy) return new Lease(existing, false, PaymentConfirmationResponse.pending(request, existing.status));
            existing.busy = true;
            return new Lease(existing, true, null); // UNKNOWN is reconciled by GET, never by a second POST.
        }
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

    public synchronized void complete(Lease lease, PaymentConfirmationResponse result) {
        lease.entry.result = result; lease.entry.status = result.paymentStatus(); lease.entry.busy = false;
    }
    public synchronized void fail(Lease lease, PaymentConfirmationException error) {
        lease.entry.failure = error; lease.entry.status = "FAILED"; lease.entry.busy = false;
    }
    public synchronized void unknown(Lease lease) {
        lease.entry.status = "UNKNOWN"; lease.entry.busy = false;
    }

    private static PaymentConfirmationException conflict() {
        return new PaymentConfirmationException(409, "PAYMENT_REQUEST_CONFLICT", "기존 승인 요청과 일치하지 않습니다.");
    }
    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
