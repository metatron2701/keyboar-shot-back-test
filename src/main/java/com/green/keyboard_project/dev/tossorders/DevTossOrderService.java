package com.green.keyboard_project.dev.tossorders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 재시작 시 초기화됩니다. 실제 주문/결제 Repository를 호출하지 않습니다. */
public class DevTossOrderService {
    private static final Duration TTL = Duration.ofMinutes(30);
    private final ConcurrentHashMap<String, Entry> orders = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Supplier<String> ids;
    private final int capacity;
    private record Entry(DevTossOrder order, Instant expiresAt) {}

    // 실제 시간과 무작위 개발 주문번호를 사용하며 저장 수에 제한을 둡니다.
    public DevTossOrderService() {
        this(Clock.systemUTC(), () -> "dev_" + UUID.randomUUID().toString().replace("-", ""), 1000);
    }
    // 테스트에서는 시간과 주문번호를 교체해 만료·충돌 상황을 실제 대기 없이 재현합니다.
    DevTossOrderService(Clock clock, Supplier<String> ids, int capacity) {
        this.clock = clock;
        this.ids = ids;
        this.capacity = capacity;
    }
    // 만료 주문을 정리한 뒤 서버가 정한 테스트 금액으로 주문을 만듭니다. 실제 주문 연동은 11단계 대상이며 DB 주문을 생성하지 않습니다.
    public synchronized DevTossOrder createDevTestOrder() {
        var now = clock.instant();
        // 만료된 주문을 먼저 제거하여 공간을 확보한 뒤 최대 보관 개수를 검사합니다.
        orders.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        if (orders.size() >= capacity) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "개발 주문 저장 한도에 도달했습니다. 만료 후 다시 시도하세요.");
        // 주문번호가 겹쳐도 기존 주문을 덮어쓰지 않고 새 번호로 최대 10번 시도합니다.
        for (int attempt = 0; attempt < 10; attempt++) {
            String id = ids.get();
            var order = new DevTossOrder(id, "개발 테스트 키보드", 10000, "KRW", "PENDING");
            if (orders.putIfAbsent(id, new Entry(order, now.plus(TTL))) == null) return order;
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "테스트 주문번호 생성에 실패했습니다.");
    }
    // 주문번호 형식과 만료 시간을 검사하고 유효한 메모리 주문만 반환합니다. 서버를 재시작하면 이전 주문은 조회할 수 없습니다.
    public DevTossOrder getDevTestOrder(String id) {
        if (id == null || !id.matches("dev_[a-f0-9]{32}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 테스트 주문번호입니다.");
        }
        var entry = orders.get(id);
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            // 확인한 만료 항목과 같은 항목일 때만 제거하여 동시에 바뀐 값을 잘못 지우지 않습니다.
            if (entry != null) orders.remove(id, entry);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "테스트 주문이 없거나 만료되었습니다.");
        }
        return entry.order();
    }
}
