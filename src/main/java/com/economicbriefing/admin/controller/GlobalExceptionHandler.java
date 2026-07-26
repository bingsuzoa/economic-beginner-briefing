package com.economicbriefing.admin.controller;

import com.economicbriefing.admin.dto.ApiResponse;
import com.economicbriefing.exception.BriefingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BriefingException.class)
    public ResponseEntity<ApiResponse<Void>> handleBriefingException(BriefingException e) {
        log.error("Business error: code={}, stage={}", e.getErrorCode(), e.getStage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(e.getErrorCode().name(), e.getMessage()));
    }

    /**
     * 없는 정적 리소스 요청은 404이지 서버 장애가 아닙니다. 아래 catch-all에 걸리면 500 +
     * 스택트레이스로 기록되는데, 이 서버는 도메인으로 공개되어 있어 크리덴셜 스캐닝 봇이
     * key.json 류를 계속 찔러 봅니다. 그대로 두면 진짜 오류가 봇 로그에 파묻힙니다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("404 static resource: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        log.error("Admin API error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("SYSTEM_UNEXPECTED", "서버 내부 오류가 발생했습니다."));
    }
}
