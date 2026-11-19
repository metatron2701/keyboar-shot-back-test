package com.green.keyboard_project.dev.tossorders;

/** DB 엔티티와 분리된 개발 전용 불변 스냅샷. */
// 개발 화면에 전달할 주문번호·이름·금액·통화·상태만 담습니다. 실제 주문 엔티티나 DB 저장 내역은 아닙니다.
public record DevTossOrder(String orderId, String orderName, int amount, String currency, String status) {}
