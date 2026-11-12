package com.green.keyboard_project.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class OrderCreateRequest {

    private List<OrderItemRequest> items;

    private String recipientName;
    private String recipientPhone;
    private String address;
    private String addressDetail;

    @Getter
    @NoArgsConstructor
    public static class OrderItemRequest {
        private Long productId;
        private Long optionId;
        private Long customBuildId;
        private boolean includeAssembly;
        private int quantity;
    }
}