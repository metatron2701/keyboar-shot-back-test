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

    public DevTossOrderService() {
        this(Clock.systemUTC(), () -> "dev_" + UUID.randomUUID().toString().replace("-", ""), 1000);
    }
    DevTossOrderService(Clock clock, Supplier<String> ids, int capacity) {
        this.clock = clock;
        this.ids = ids;
        this.capacity = capacity;
    }
    public synchronized DevTossOrder create() {
        var now = clock.instant();
        orders.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        if (orders.size() >= capacity) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "개발 주문 저장 한도에 도달했습니다. 만료 후 다시 시도하세요.");
        for (int attempt = 0; attempt < 10; attempt++) {
            String id = ids.get();
            var order = new DevTossOrder(id, "개발 테스트 키보드", 10000, "KRW", "PENDING");
            if (orders.putIfAbsent(id, new Entry(order, now.plus(TTL))) == null) return order;
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "테스트 주문번호 생성에 실패했습니다.");
    }
    public DevTossOrder find(String id) {
        if (id == null || !id.matches("dev_[a-f0-9]{32}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 테스트 주문번호입니다.");
        }
        var entry = orders.get(id);
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            if (entry != null) orders.remove(id, entry);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "테스트 주문이 없거나 만료되었습니다.");
        }
        return entry.order();
    }
}
