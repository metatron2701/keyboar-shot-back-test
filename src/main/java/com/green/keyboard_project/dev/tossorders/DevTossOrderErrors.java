package com.green.keyboard_project.dev.tossorders;

import com.green.keyboard_project.dto.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.context.annotation.Profile("local & !prod & !production")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "TOSS_PAYMENTS_DEV_ORDERS_ENABLED", havingValue = "true")
@RestControllerAdvice(assignableTypes = DevTossOrderController.class)
@Order(0)
public class DevTossOrderErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handle(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(ApiResponse.fail(error.getReason()));
    }
}
