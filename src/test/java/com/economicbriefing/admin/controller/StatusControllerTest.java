package com.economicbriefing.admin.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.economicbriefing.admin.entity.PipelineRunEntity;
import com.economicbriefing.admin.repository.PipelineRunRepository;
import com.economicbriefing.admin.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatusController.class)
@Import({SecurityConfig.class, AdminTestConfig.class})
@ActiveProfiles("test")
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PipelineRunRepository runRepo;

    @Test
    void shouldReturnStatusWithNoPipelineRunning() throws Exception {
        when(runRepo.findRunning()).thenReturn(Optional.empty());
        when(runRepo.findAllByOrderByStartedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/status")
                        .header("Authorization", "Bearer test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.service").value("running"))
                .andExpect(jsonPath("$.data.pipelineRunning").value(false))
                .andExpect(jsonPath("$.data.dbConnected").value(true));
    }

    @Test
    void shouldReturnStatusWithPipelineRunning() throws Exception {
        PipelineRunEntity running = new PipelineRunEntity();
        running.setId("run-123");
        running.setStatus("RUNNING");
        running.setCurrentStep("COLLECT");
        running.setTriggerType("SCHEDULER");
        running.setStartedAt(OffsetDateTime.now());

        when(runRepo.findRunning()).thenReturn(Optional.of(running));
        when(runRepo.findAllByOrderByStartedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(running)));

        mockMvc.perform(get("/api/admin/status")
                        .header("Authorization", "Bearer test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pipelineRunning").value(true))
                .andExpect(jsonPath("$.data.currentRunId").value("run-123"))
                .andExpect(jsonPath("$.data.currentStep").value("COLLECT"));
    }

    @Test
    void shouldReturnLastRunInfo() throws Exception {
        PipelineRunEntity lastRun = new PipelineRunEntity();
        lastRun.setId("run-456");
        lastRun.setStatus("SUCCESS");
        lastRun.setTriggerType("MANUAL");
        lastRun.setStartedAt(OffsetDateTime.now().minusHours(1));
        lastRun.setFinishedAt(OffsetDateTime.now());
        lastRun.setCurrentStep("DONE");

        when(runRepo.findRunning()).thenReturn(Optional.empty());
        when(runRepo.findAllByOrderByStartedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lastRun)));

        mockMvc.perform(get("/api/admin/status")
                        .header("Authorization", "Bearer test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastRun.id").value("run-456"))
                .andExpect(jsonPath("$.data.lastRun.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.lastRun.triggerType").value("MANUAL"));
    }

    @Test
    void shouldRejectWithoutAuthToken() throws Exception {
        mockMvc.perform(get("/api/admin/status"))
                .andExpect(status().isUnauthorized());
    }
}
