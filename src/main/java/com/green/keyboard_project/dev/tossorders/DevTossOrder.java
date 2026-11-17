package com.green.keyboard_project.dev.tossorders;

/** DB 엔티티와 분리된 개발 전용 불변 스냅샷. */
public record DevTossOrder(String orderId, String orderName, int amount, String currency, String status) {}
