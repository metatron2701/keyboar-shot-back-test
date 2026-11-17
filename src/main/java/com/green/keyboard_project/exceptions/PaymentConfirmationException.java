package com.green.keyboard_project.exceptions;

public final class PaymentConfirmationException extends RuntimeException {
    private final int status;
    private final String code;
    private final String providerCode;

    public PaymentConfirmationException(int status, String code, String safeMessage) {
        this(status, code, safeMessage, null);
    }
    public PaymentConfirmationException(int status, String code, String safeMessage, String providerCode) {
        super(safeMessage);
        this.status = status;
        this.code = code;
        this.providerCode = providerCode;
    }
    public int status() { return status; }
    public String code() { return code; }
    public String providerCode() { return providerCode; }
}
