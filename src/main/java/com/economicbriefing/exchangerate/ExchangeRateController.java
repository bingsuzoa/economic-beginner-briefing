package com.economicbriefing.exchangerate;

import java.util.Map;

import com.economicbriefing.admin.dto.ApiResponse;
import com.economicbriefing.exchangerate.ExchangeRateService.ExchangeRateNotReadyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExchangeRateController {

    private final ExchangeRateService service;

    public ExchangeRateController(ExchangeRateService service) {
        this.service = service;
    }

    @GetMapping("/api/exchange-rate/history/{currency}")
    public ResponseEntity<ApiResponse<?>> getHistory(
            @PathVariable String currency,
            @RequestParam(defaultValue = "1M") String period) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(service.getHistory(
                    SupportedCurrency.from(currency), ExchangeRatePeriod.from(period))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_EXCHANGE_RATE_REQUEST", e.getMessage()));
        }
    }

    @PostMapping("/api/admin/exchange-rates/backfill")
    public ResponseEntity<ApiResponse<?>> backfill() {
        if (!service.startOneYearBackfill()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("BACKFILL_ALREADY_RUNNING", "환율 초기 적재가 이미 실행 중입니다."));
        }
        return ResponseEntity.accepted().body(ApiResponse.ok(Map.of("message", "최근 1년 환율 적재를 시작했습니다.")));
    }

    @ExceptionHandler(ExchangeRateNotReadyException.class)
    public ResponseEntity<ApiResponse<?>> notReady() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("EXCHANGE_RATE_NOT_READY", "아직 저장된 환율 데이터가 없습니다."));
    }
}
