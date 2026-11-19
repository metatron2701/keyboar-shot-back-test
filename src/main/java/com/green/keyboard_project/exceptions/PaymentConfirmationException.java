package com.green.keyboard_project.exceptions;

public final class PaymentConfirmationException extends RuntimeException {
    private final int status;
    private final String code;
    private final String providerCode;

    // 내부 오류 코드와 사용자에게 보여도 되는 메시지만 승인 API 응답에 전달합니다.
    public PaymentConfirmationException(int status, String code, String safeMessage) {
        this(status, code, safeMessage, null);
    }
    // 외부 오류가 있을 때도 허용된 제공자 코드만 추가하며 원본 예외 본문은 저장하지 않습니다.
    public PaymentConfirmationException(int status, String code, String safeMessage, String providerCode) {
        super(safeMessage);
        this.status = status;
        this.code = code;
        this.providerCode = providerCode;
    }
    // HTTP 응답에 사용할 오류 상태를 반환합니다.
    public int status() { return status; }
    // 프론트가 오류 종류를 구분할 내부 코드를 반환합니다.
    public String code() { return code; }
    // 안전하게 선별한 외부 오류 코드만 반환합니다.
    public String providerCode() { return providerCode; }
}
