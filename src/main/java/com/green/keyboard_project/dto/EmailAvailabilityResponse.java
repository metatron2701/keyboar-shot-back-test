package com.green.keyboard_project.dto;

/** 이메일의 사용 가능 여부를 중복 확인 응답으로 전달합니다. */
public record EmailAvailabilityResponse(boolean available) {
}
