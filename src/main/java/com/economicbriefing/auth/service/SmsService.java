package com.economicbriefing.auth.service;

public interface SmsService {
    void sendCode(String phone, String code);

    /** Thrown when the SMS provider fails to send. Message is safe to show to users. */
    class SmsSendFailedException extends RuntimeException {
        public SmsSendFailedException() { super("인증번호 발송에 실패했습니다. 잠시 후 다시 시도해주세요."); }
    }
}
