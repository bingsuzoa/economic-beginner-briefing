package com.economicbriefing.pipeline;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.economicbriefing.domain.execution.ExecutionLog;
import com.economicbriefing.domain.article.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The single entry point for running the pipeline. The scheduler and the admin API both
 * go through here so concurrency control, exception containment and operational logging
 * exist in exactly one place.
 *
 * <p>Two different guards are in play, and they answer different questions:
 * <ul>
 *   <li>{@link PipelineLock} — "is a run in flight right now?" (this class)</li>
 *   <li>{@link ExecutionTracker#checkDuplicate} — "was this dedupe key already published?"
 *       (inside {@link BriefingPipeline})</li>
 * </ul>
 */
@Service
public class PipelineExecutionService {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutionService.class);

    private final BriefingPipeline pipeline;
    private final PipelineLock pipelineLock;

    public PipelineExecutionService(BriefingPipeline pipeline, PipelineLock pipelineLock) {
        this.pipeline = pipeline;
        this.pipelineLock = pipelineLock;
    }

    /**
     * Runs the pipeline on the calling thread. Used by the scheduler, which wants the tick
     * to stay busy for the duration so overruns are visible.
     *
     * @return empty when another run holds the lock, or when the run blew up
     */
    public Optional<ExecutionLog> tryRun(PipelineOptions options) {
        String token = newToken();
        String source = sourceOf(options);

        if (!pipelineLock.acquire(token, options.triggerType())) {
            log.warn("[{}] Previous execution is still running. Skip.", source);
            return Optional.empty();
        }

        return Optional.ofNullable(runHoldingLock(token, options, source));
    }

    /**
     * Acquires the lock, then runs on a virtual thread. The lock is taken before dispatch so
     * the caller can answer "already running" without a race.
     *
     * @return false when another run holds the lock
     */
    public boolean tryRunAsync(PipelineOptions options) {
        return tryRunAsync(options, null);
    }

    /** Starts the same pipeline for an operator-supplied article without touching RSS collection. */
    public boolean tryRunAsync(PipelineOptions options, List<Article> suppliedArticles) {
        String token = newToken();
        String source = sourceOf(options);

        if (!pipelineLock.acquire(token, options.triggerType())) {
            log.warn("[{}] Previous execution is still running. Skip.", source);
            return false;
        }

        Thread.startVirtualThread(() -> runHoldingLock(token, options, source, suppliedArticles));
        return true;
    }

    public boolean isRunning() {
        return pipelineLock.isLocked();
    }

    /**
     * Caller already owns the lock. Nothing may escape this method: a scheduled invocation
     * that throws would kill nothing by itself, but an uncaught error here would leave the
     * lock held and block every later run.
     */
    private ExecutionLog runHoldingLock(String token, PipelineOptions options, String source) {
        return runHoldingLock(token, options, source, null);
    }

    private ExecutionLog runHoldingLock(String token, PipelineOptions options, String source,
                                        List<Article> suppliedArticles) {
        long startedAt = System.nanoTime();
        log.info("[{}] Pipeline started", source);

        try {
            ExecutionLog result = suppliedArticles == null ? pipeline.run(options)
                    : pipeline.run(options, suppliedArticles);
            log.info("[{}] Pipeline finished ({}s) status={}",
                    source, elapsedSeconds(startedAt), result.getStatus());
            return result;
        } catch (Exception e) {
            log.error("[{}] Pipeline aborted after {}s with an unexpected error",
                    source, elapsedSeconds(startedAt), e);
            return null;
        } catch (Throwable t) {
            // OOM/StackOverflow still must not strand the lock
            log.error("[{}] Pipeline aborted after {}s with a fatal error",
                    source, elapsedSeconds(startedAt), t);
            throw t;
        } finally {
            pipelineLock.release(token);
        }
    }

    private static long elapsedSeconds(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000_000L;
    }

    /** Log prefix, so operators can tell a scheduled run from a manual one at a glance. */
    static String sourceOf(PipelineOptions options) {
        return "SCHEDULER".equals(options.triggerType()) ? "Scheduler" : "Admin";
    }

    private static String newToken() {
        return UUID.randomUUID().toString();
    }
}
