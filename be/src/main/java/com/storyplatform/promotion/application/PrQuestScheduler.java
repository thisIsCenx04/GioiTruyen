package com.storyplatform.promotion.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the deadlines that make PR quests work.
 *
 * <p>Every rule the marketplace rests on is a rule about time: a claim nobody
 * submits gives its slot back, and a submission nobody reviews gets paid
 * anyway. Neither happens on a request - the owner who is stalling is
 * precisely the person not sending requests - so something has to run on its
 * own. Without this class the deadlines are text on a screen.
 */
@Component
public class PrQuestScheduler {

    private static final Logger log = LoggerFactory.getLogger(PrQuestScheduler.class);

    private final PrQuestService service;

    public PrQuestScheduler(PrQuestService service) {
        this.service = service;
    }

    /**
     * Fifteen minutes is fine: the shortest deadline in the system is a day, so
     * nothing here is urgent, and a sweep this size costs three indexed queries.
     *
     * <p>A failure is logged rather than thrown. The next run picks up whatever
     * this one missed, and letting the exception escape would only stop the
     * scheduler from trying again.
     */
    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 60 * 1000)
    public void sweep() {
        try {
            int handled = service.runDueWork();
            if (handled > 0) {
                log.info("PR quest sweep handled {} overdue item(s)", handled);
            }
        } catch (RuntimeException failure) {
            log.error("PR quest sweep failed; retrying on the next run", failure);
        }
    }
}
