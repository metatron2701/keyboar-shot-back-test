package com.green.keyboard_project.controller;

import com.green.keyboard_project.config.tosspayments.DevPaymentConfirmationConfiguration;
import com.green.keyboard_project.dto.ApiResponse;
import com.green.keyboard_project.dto.payment.*;
import com.green.keyboard_project.service.DevPaymentConfirmationService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Conditional(DevPaymentConfirmationConfiguration.Enabled.class)
public class DevPaymentConfirmationController {
    private final DevPaymentConfirmationService service;
    public DevPaymentConfirmationController(DevPaymentConfirmationService service) { this.service = service; }

    @PostMapping(value = "/api/payments/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<PaymentConfirmationResponse>> confirm(@Valid @RequestBody PaymentConfirmationRequest request) {
        PaymentConfirmationResponse result = service.confirm(request);
        var builder = ResponseEntity.status(result.pending() ? 202 : 200).cacheControl(CacheControl.noStore());
        if (result.pending()) builder.header(HttpHeaders.RETRY_AFTER, "3");
        return builder.body(ApiResponse.ok(result));
    }
}
