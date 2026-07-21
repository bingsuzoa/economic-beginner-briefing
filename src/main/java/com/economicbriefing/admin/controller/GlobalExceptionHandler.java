package com.economicbriefing.admin.controller;

import com.economicbriefing.admin.dto.ApiResponse;
import com.economicbriefing.exception.BriefingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BriefingException.class)
    public ResponseEntity<ApiResponse<Void>> handleBriefingException(BriefingException e) {
        log.error("Business error: code={}, stage={}", e.getErrorCode(), e.getStage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(e.getErrorCode().name(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        log.error("Admin API error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("SYSTEM_UNEXPECTED", "서버 내부 오류가 발생했습니다."));
    }
}
