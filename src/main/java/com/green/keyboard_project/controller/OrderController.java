package com.green.keyboard_project.controller;

import com.green.keyboard_project.dto.OrderCreateRequest;
import com.green.keyboard_project.dto.OrderCreateResponse;
import com.green.keyboard_project.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderCreateResponse> createOrder(
            @RequestBody OrderCreateRequest request
    ) {
        OrderCreateResponse response =
                orderService.createOrder(request);

        return ResponseEntity.ok(response);
    }
}