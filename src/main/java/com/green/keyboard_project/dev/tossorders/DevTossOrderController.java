package com.green.keyboard_project.dev.tossorders;

import com.green.keyboard_project.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.context.annotation.Profile("local & !prod & !production")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "TOSS_PAYMENTS_DEV_ORDERS_ENABLED", havingValue = "true")
@RestController
@RequestMapping("/api/dev/toss-orders")
public class DevTossOrderController {
    private final DevTossOrderService orders;
    public DevTossOrderController(DevTossOrderService orders) { this.orders = orders; }

    @PostMapping
    public ResponseEntity<ApiResponse<DevTossOrder>> create(
            @RequestBody(required = false) String body,
            @RequestParam java.util.Map<String, String> parameters) {
        if ((body != null && !body.isBlank()) || !parameters.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "테스트 주문 생성은 입력값을 받지 않습니다.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(orders.create()));
    }
    @GetMapping("/{orderId}")
    public ApiResponse<DevTossOrder> find(@PathVariable String orderId) {
        return ApiResponse.ok(orders.find(orderId));
    }
}
