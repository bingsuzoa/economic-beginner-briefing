package com.economicbriefing.api;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.economicbriefing.admin.entity.PipelineRunEntity;
import com.economicbriefing.admin.repository.PipelineRunRepository;
import com.economicbriefing.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BriefingHealthControllerTest {

    private static AppProperties props(boolean schedulerEnabled, String cron) {
        return new AppProperties(
                false,
                new AppProperties.TimeoutProperties(Duration.ofSeconds(10), Duration.ofSeconds(60)),
                new AppProperties.RetryProperties(3, Duration.ofSeconds(1), Duration.ofSeconds(2)),
                new AppProperties.DiversityProperties(3, 3, 3, 5),
                new AppProperties.AudienceProperties("beginner", List.of("interest_rate"), List.of()),
                new AppProperties.SchedulerProperties(schedulerEnabled, cron),
                new AppProperties.TeacherProperties(true, "teacher-v1", "gpt-4o-mini", 6),
                new AppProperties.EmbeddingProperties(false, "text-embedding-3-small", 1536));
    }

    private static PipelineRunEntity runFinishedMinutesAgo(long minutes) {
        PipelineRunEntity run = new PipelineRunEntity();
        run.setId("exec-1");
        run.setStatus("SUCCESS");
        run.setStartedAt(OffsetDateTime.now().minusMinutes(minutes + 1));
        run.setFinishedAt(OffsetDateTime.now().minusMinutes(minutes));
        return run;
    }

    private static BriefingHealthController controller(PipelineRunRepository repo, AppProperties props) {
        return new BriefingHealthController(repo, props, Duration.ofHours(3));
    }

    @Test
    void shouldBeUpWhenRecentRunSucceeded() {
        PipelineRunRepository repo = mock(PipelineRunRepository.class);
        when(repo.findFirstByStatusOrderByStartedAtDesc("SUCCESS"))
                .thenReturn(Optional.of(runFinishedMinutesAgo(42)));

        var response = controller(repo, props(true, "0 0 * * * *")).briefingHealth();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", body(response).status());
        assertEquals("ENABLED", body(response).scheduler());
        assertEquals(42L, body(response).lastSuccessAgeMinutes());
        assertTrue(body(response).reasons().isEmpty());
    }

    /** The symptom that matters: the scheduler looks fine but nothing is being produced. */
    @Test
    void shouldBeDownWhenLastSuccessIsStale() {
        PipelineRunRepository repo = mock(PipelineRunRepository.class);
        when(repo.findFirstByStatusOrderByStartedAtDesc("SUCCESS"))
                .thenReturn(Optional.of(runFinishedMinutesAgo(310)));

        var response = controller(repo, props(true, "0 0 * * * *")).briefingHealth();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", body(response).status());
        assertEquals("ENABLED", body(response).scheduler(), "cron itself is fine");
        assertTrue(body(response).reasons().stream().anyMatch(r -> r.contains("no successful run in")));
    }

    @Test
    void shouldBeDownWhenCronIsInvalid() {
        PipelineRunRepository repo = mock(PipelineRunRepository.class);
        when(repo.findFirstByStatusOrderByStartedAtDesc("SUCCESS"))
                .thenReturn(Optional.of(runFinishedMinutesAgo(5)));

        var response = controller(repo, props(true, "0 0 * *")).briefingHealth();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("MISCONFIGURED", body(response).scheduler());
        assertTrue(body(response).reasons().stream().anyMatch(r -> r.contains("cron is invalid")));
    }

    /** Locally the scheduler is off, so stale data is expected and must not page anyone. */
    @Test
    void shouldStayUpWhenSchedulerIsDisabledEvenIfStale() {
        PipelineRunRepository repo = mock(PipelineRunRepository.class);
        when(repo.findFirstByStatusOrderByStartedAtDesc("SUCCESS"))
                .thenReturn(Optional.of(runFinishedMinutesAgo(9999)));

        var response = controller(repo, props(false, "0 0 * * * *")).briefingHealth();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("DISABLED", body(response).scheduler());
        assertEquals(9999L, body(response).lastSuccessAgeMinutes(), "age is still reported");
    }

    @Test
    void shouldBeDownWhenNoRunHasEverSucceeded() {
        PipelineRunRepository repo = mock(PipelineRunRepository.class);
        when(repo.findFirstByStatusOrderByStartedAtDesc("SUCCESS")).thenReturn(Optional.empty());

        var response = controller(repo, props(true, "0 0 * * * *")).briefingHealth();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNull(body(response).lastSuccessAgeMinutes());
        assertTrue(body(response).reasons().stream().anyMatch(r -> r.contains("no successful pipeline run")));
    }

    @Test
    void shouldBeDownWhenDatabaseIsUnreachable() {
        PipelineRunRepository repo = mock(PipelineRunRepository.class);
        when(repo.findFirstByStatusOrderByStartedAtDesc(anyString()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("no connection"));

        var response = controller(repo, props(true, "0 0 * * * *")).briefingHealth();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertFalse(body(response).dbConnected());
        assertTrue(body(response).reasons().stream().anyMatch(r -> r.contains("database unreachable")));
    }

    private static BriefingHealthResponse body(ResponseEntity<BriefingHealthResponse> response) {
        return response.getBody();
    }
}
