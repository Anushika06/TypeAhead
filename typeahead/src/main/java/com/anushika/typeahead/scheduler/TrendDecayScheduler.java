package com.anushika.typeahead.scheduler;

import com.anushika.typeahead.service.TrendDecayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trigger for the trend-score decay job.
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
