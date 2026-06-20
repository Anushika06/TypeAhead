package com.anushika.typeahead.scheduler;

import com.anushika.typeahead.service.TrendDecayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trigger for the trend-score decay job.
 *
 * <h2>Separation of concerns</h2>
 * This class owns <em>when</em> to run the decay.
 * {@link TrendDecayService} owns <em>how</em> to run it (SQL + transaction).
 * Keeping them separate makes the service independently testable without a
 * scheduler context.
 *
 * <h2>Cron schedule</h2>
 * Configured via {@code scheduler.decay.cron} in {@code application.yaml}:
 * <ul>
 *   <li><b>Development</b> — {@code "0 * * * * *"} (every 1 minute)</li>
 *   <li><b>Production</b>  — {@code "0 0 2 * * *"} (nightly at 02:00)</li>
 * </ul>
 *
 * Spring's cron format uses 6 fields: {@code second minute hour day month weekday}.
 *
 * <h2>Double-fire protection</h2>
 * The SQL {@code WHERE last_decay_at < NOW() - INTERVAL '20 hours'} predicate
 * in {@link TrendDecayService} guarantees each row is decayed at most once per
 * 20-hour window, regardless of how often the scheduler fires.
 */
@Component
public class TrendDecayScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrendDecayScheduler.class);

    private final TrendDecayService trendDecayService;

    public TrendDecayScheduler(TrendDecayService trendDecayService) {
        this.trendDecayService = trendDecayService;
    }

    /**
     * Invokes the decay pass on the configured cron schedule.
     *
     * <p>Any exception thrown by the service is caught and logged — the
     * scheduler thread must never die from a transient DB failure.
     */
    @Scheduled(cron = "${scheduler.decay.cron}")
    public void runDecay() {
        log.info("TREND DECAY STARTED");
        try {
            TrendDecayService.DecayStats stats = trendDecayService.applyDecay();

            log.info("TREND DECAY COMPLETED  rowsUpdated={}  durationMs={}",
                    stats.rowsUpdated(), stats.durationMs());

        } catch (Exception ex) {
            log.error("TREND DECAY FAILED: {}", ex.getMessage(), ex);
        }
    }
}
