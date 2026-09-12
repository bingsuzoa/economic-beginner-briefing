package com.economicbriefing.exchangerate;

import com.economicbriefing.admin.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExchangeRateBriefingController {
    private final ExchangeRateBriefingService service;

    public ExchangeRateBriefingController(ExchangeRateBriefingService service) { this.service = service; }

    @GetMapping("/api/exchange-rate/briefing/{currency}")
    public ResponseEntity<ApiResponse<?>> briefing(@PathVariable String currency) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(service.find(SupportedCurrency.from(currency))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_EXCHANGE_RATE_REQUEST", e.getMessage()));
        }
    }
}
