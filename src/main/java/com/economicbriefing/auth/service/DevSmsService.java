package com.economicbriefing.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "sms.provider", havingValue = "dev", matchIfMissing = true)
public class DevSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(DevSmsService.class);

    @Override
    public void sendCode(String phone, String code) {
        log.info("[DEV SMS] phone={}, code={}", phone, code);
    }
}
