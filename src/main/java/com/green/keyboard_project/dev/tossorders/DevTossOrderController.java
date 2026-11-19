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
    // 개발 주문 전용 서비스를 연결하며 실제 주문 저장소는 사용하지 않습니다.
    public DevTossOrderController(DevTossOrderService orders) { this.orders = orders; }

    // 입력 본문·쿼리 없이 테스트 주문을 생성하고 공통 응답으로 반환합니다. 브라우저가 금액을 지정하지 못하게 합니다.
    @PostMapping
    public ResponseEntity<ApiResponse<DevTossOrder>> createDevTestOrder(
            @RequestBody(required = false) String body,
            @RequestParam java.util.Map<String, String> parameters) {
        // 브라우저 입력으로 주문이나 금액을 바꾸지 못하도록 본문과 쿼리 입력을 모두 거부합니다.
        if ((body != null && !body.isBlank()) || !parameters.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "테스트 주문 생성은 입력값을 받지 않습니다.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(orders.createDevTestOrder()));
    }
    // 프론트가 결제 전 주문번호와 금액을 다시 확인할 수 있도록 개발 주문을 조회합니다.
    @GetMapping("/{orderId}")
    public ApiResponse<DevTossOrder> getDevTestOrder(@PathVariable String orderId) {
        return ApiResponse.ok(orders.getDevTestOrder(orderId));
    }
}
