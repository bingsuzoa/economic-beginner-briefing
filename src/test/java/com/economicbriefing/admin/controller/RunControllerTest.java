package com.economicbriefing.admin.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.economicbriefing.admin.entity.PipelineItemEntity;
import com.economicbriefing.admin.entity.PipelineLogEntity;
import com.economicbriefing.admin.entity.PipelineRunEntity;
import com.economicbriefing.admin.repository.PipelineItemRepository;
import com.economicbriefing.admin.repository.PipelineLogRepository;
import com.economicbriefing.admin.repository.PipelineRunRepository;
import com.economicbriefing.admin.security.SecurityConfig;
import com.economicbriefing.pipeline.BriefingPipeline;
import com.economicbriefing.pipeline.PipelineLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RunController.class)
@Import({SecurityConfig.class, AdminTestConfig.class})
@ActiveProfiles("test")
class RunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PipelineRunRepository runRepo;

    @MockitoBean
    private PipelineLogRepository logRepo;

    @MockitoBean
    private PipelineItemRepository itemRepo;

    @MockitoBean
    private PipelineLock pipelineLock;

    @MockitoBean
    private BriefingPipeline pipeline;

    private static final String AUTH = "Bearer test-admin-token";

    @Test
    void shouldListRunsWithPagination() throws Exception {
        PipelineRunEntity run = createRun("run-1", "SUCCESS");
        when(runRepo.findAllByOrderByStartedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(run)));

        mockMvc.perform(get("/api/admin/runs")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value("run-1"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldFilterRunsByStatus() throws Exception {
        PipelineRunEntity run = createRun("run-2", "FAILED");
        when(runRepo.findByStatusOrderByStartedAtDesc(eq("FAILED"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(run)));

        mockMvc.perform(get("/api/admin/runs?status=FAILED")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("FAILED"));
    }

    @Test
    void shouldGetRunDetail() throws Exception {
        PipelineRunEntity run = createRun("run-3", "SUCCESS");
        when(runRepo.findById("run-3")).thenReturn(Optional.of(run));

        mockMvc.perform(get("/api/admin/runs/run-3")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("run-3"));
    }

    @Test
    void shouldReturn404ForMissingRun() throws Exception {
        when(runRepo.findById("not-exist")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/runs/not-exist")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }

    @Test
    void shouldGetRunLogs() throws Exception {
        PipelineLogEntity log = new PipelineLogEntity();
        log.setRunId("run-4");
        log.setLevel("INFO");
        log.setStep("COLLECT");
        log.setMessage("수집 시작");

        when(runRepo.findById("run-4")).thenReturn(Optional.of(createRun("run-4", "SUCCESS")));
        when(logRepo.findByRunIdOrderByCreatedAtAsc("run-4")).thenReturn(List.of(log));

        mockMvc.perform(get("/api/admin/runs/run-4/logs")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].message").value("수집 시작"));
    }

    @Test
    void shouldReturn404ForLogsOfMissingRun() throws Exception {
        when(runRepo.findById("not-exist")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/runs/not-exist/logs")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetRunItems() throws Exception {
        PipelineItemEntity item = new PipelineItemEntity();
        item.setRunId("run-5");
        item.setOriginalTitle("테스트 뉴스");

        when(runRepo.findById("run-5")).thenReturn(Optional.of(createRun("run-5", "SUCCESS")));
        when(itemRepo.findByRunIdOrderByIdAsc("run-5")).thenReturn(List.of(item));

        mockMvc.perform(get("/api/admin/runs/run-5/items")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].originalTitle").value("테스트 뉴스"));
    }

    @Test
    void shouldTriggerPipelineRun() throws Exception {
        when(pipelineLock.isLocked()).thenReturn(false);

        mockMvc.perform(post("/api/admin/runs")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldRejectWhenPipelineAlreadyRunning() throws Exception {
        when(pipelineLock.isLocked()).thenReturn(true);

        mockMvc.perform(post("/api/admin/runs")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PIPELINE_ALREADY_RUNNING"));
    }

    @Test
    void shouldRejectInvalidTargetDate() throws Exception {
        mockMvc.perform(post("/api/admin/runs")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDate\": \"invalid-date\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TARGET_DATE"));
    }

    @Test
    void shouldTriggerWithTargetDate() throws Exception {
        when(pipelineLock.isLocked()).thenReturn(false);

        mockMvc.perform(post("/api/admin/runs")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDate\": \"2025-01-15\"}"))
                .andExpect(status().isAccepted());
    }

    private PipelineRunEntity createRun(String id, String status) {
        PipelineRunEntity run = new PipelineRunEntity();
        run.setId(id);
        run.setStatus(status);
        run.setTriggerType("MANUAL");
        run.setStartedAt(OffsetDateTime.now());
        run.setCurrentStep("DONE");
        return run;
    }
}
