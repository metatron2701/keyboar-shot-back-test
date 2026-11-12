package com.green.keyboard_project.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderCreateResponse {

    private String orderId;
    private String orderName;
    private int amount;
}