package com.green.keyboard_project.dev.tossorders;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;

class DevTossOrderServiceTests {
    private static final String ID = "dev_" + "a".repeat(32);
    @Test void fixedServerAmountAndLookup() {
        var service = new DevTossOrderService();
        var order = service.create();
        assertThat(order.amount()).isEqualTo(10000);
        assertThat(order.currency()).isEqualTo("KRW");
        assertThat(order.status()).isEqualTo("PENDING");
        assertThat(service.find(order.orderId())).isEqualTo(order);
        assertThatThrownBy(() -> service.find("bad")).isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException)e).getStatusCode().value()).isEqualTo(400));
        assertThatThrownBy(() -> service.find(ID)).isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException)e).getStatusCode().value()).isEqualTo(404));
    }
    @Test void concurrentRequestsHaveUniqueIds() throws Exception {
        var service = new DevTossOrderService();
        try (var executor = Executors.newFixedThreadPool(12)) {
            var calls = new ArrayList<Callable<String>>();
            for (int i=0; i<300; i++) calls.add(() -> service.create().orderId());
            var ids = new HashSet<String>();
            for (var future : executor.invokeAll(calls)) assertThat(ids.add(future.get())).isTrue();
            assertThat(ids).hasSize(300);
        }
    }
    @Test void collisionRetriesDoNotOverwriteStoredOrder() {
        var count = new AtomicInteger();
        var service = new DevTossOrderService(Clock.systemUTC(),
            () -> count.incrementAndGet() <= 2 ? ID : "dev_"+"b".repeat(32), 10);
        var first = service.create();
        var second = service.create();
        assertThat(first.orderId()).isNotEqualTo(second.orderId());
        assertThat(service.find(first.orderId())).isSameAs(first);
    }
    @Test void repeatedCollisionsFailInsteadOfOverwriting() {
        var service = new DevTossOrderService(Clock.systemUTC(), () -> ID, 10);
        service.create();
        assertThatThrownBy(service::create).isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException)e).getStatusCode().value()).isEqualTo(503));
    }
    @Test void expiryAndCapacityAreBounded() {
        var instant = new java.util.concurrent.atomic.AtomicReference<>(Instant.now());
        var clock = new Clock() {
            public ZoneId getZone() {return ZoneOffset.UTC;}
            public Clock withZone(ZoneId zone) {return this;}
            public Instant instant() {return instant.get();}
        };
        var service = new DevTossOrderService(clock, () -> ID, 1);
        service.create();
        assertThatThrownBy(service::create).isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException)e).getStatusCode().value()).isEqualTo(429));
        instant.set(instant.get().plusSeconds(1800));
        assertThatThrownBy(() -> service.find(ID)).isInstanceOf(ResponseStatusException.class);
        assertThat(service.create()).isNotNull();
    }
}
