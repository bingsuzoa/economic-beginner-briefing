package com.economicbriefing.admin.controller;

import com.economicbriefing.admin.dto.ApiResponse;
import com.economicbriefing.admin.dto.PageResponse;
import com.economicbriefing.briefing.DailyBriefingEntity;
import com.economicbriefing.briefing.DailyBriefingRepository;
import com.economicbriefing.briefing.DailyBriefingService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/briefings")
public class DailyBriefingAdminController {
    private final DailyBriefingRepository repository;
    private final DailyBriefingService service;

    public DailyBriefingAdminController(DailyBriefingRepository repository, DailyBriefingService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping("/runs")
    public ApiResponse<PageResponse<DailyBriefingEntity>> runs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        int safeSize = Math.max(1, Math.min(size, 100));
        var result = status == null || status.isBlank()
                ? repository.findAllByOrderByStartedAtDesc(PageRequest.of(Math.max(0, page), safeSize))
                : repository.findByStatusOrderByStartedAtDesc(status.toUpperCase(), PageRequest.of(Math.max(0, page), safeSize));
        return ApiResponse.ok(new PageResponse<>(result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<ApiResponse<DailyBriefingEntity>> run(@PathVariable String id) {
        return repository.findById(id).map(value -> ResponseEntity.ok(ApiResponse.ok(value)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("NOT_FOUND", "브리핑 실행 기록을 찾을 수 없습니다.")));
    }

    @PostMapping("/{date}/run")
    public ResponseEntity<ApiResponse<Map<String, Object>>> start(@PathVariable LocalDate date) {
        boolean started = service.startAsync(date);
        return ResponseEntity.status(started ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT)
                .body(started ? ApiResponse.ok(Map.of("accepted", true, "targetDate", date))
                        : ApiResponse.error("ALREADY_RUNNING", "이미 경제흐름 분석이 실행 중입니다."));
    }
}
