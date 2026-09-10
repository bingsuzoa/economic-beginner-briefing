package com.economicbriefing.api;

import com.economicbriefing.briefing.DailyBriefingEntity;
import com.economicbriefing.briefing.DailyBriefingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/briefings")
public class DailyBriefingController {
    private final DailyBriefingRepository repository;
    private final ObjectMapper json;

    public DailyBriefingController(DailyBriefingRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    @GetMapping("/latest")
    public ResponseEntity<JsonNode> latest() {
        return response(repository.findFirstByStatusOrderByFinishedAtDesc("SUCCESS").orElse(null));
    }

    @GetMapping("/{date}")
    public ResponseEntity<JsonNode> date(@PathVariable LocalDate date) {
        return response(repository.findFirstByTargetDateAndStatusOrderByRevisionDesc(date, "SUCCESS").orElse(null));
    }

    private ResponseEntity<JsonNode> response(DailyBriefingEntity briefing) {
        if (briefing == null || briefing.getResultJson() == null) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok(json.readTree(briefing.getResultJson()));
        } catch (Exception e) {
            throw new IllegalStateException("stored daily briefing is invalid JSON", e);
        }
    }
}
