package com.thanh.backend.flight.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;

@Slf4j
@Component
@ConditionalOnProperty(name="app.demo-flights.enabled",havingValue="true")
public class DemoFlightGenerator implements ApplicationRunner {
    private final DemoFlightConfiguration safety;
    private final DemoFlightProperties properties;
    private final DemoFlightTemplates templates;
    private final DemoFlightWriter writer;
    private final Clock clock;
    private volatile boolean ready;
    public DemoFlightGenerator(DemoFlightConfiguration safety,DemoFlightProperties properties,
            DemoFlightTemplates templates,DemoFlightWriter writer,@Qualifier("demoFlightClock") Clock clock) {
        this.safety=safety;this.properties=properties;this.templates=templates;this.writer=writer;this.clock=clock;
    }
    @Override public void run(ApplicationArguments args) {
        safety.assertSafe(); reconcile();
    }
    @EventListener(ApplicationReadyEvent.class) public void ready() { ready=true; }
    @Scheduled(fixedDelayString="${app.demo-flights.interval-ms:3600000}")
    public void scheduled() { if(ready) reconcile(); }
    public synchronized void reconcile() {
        safety.assertSafe();
        try {
            templates.validate(properties);
        } catch (IllegalArgumentException error) {
            // Invalid identities/templates disable this run; the application stays available.
            log.warn("Demo templates rejected before writes: failure={}", error.getClass().getSimpleName());
            return;
        }
        int created=0, skipped=0, failed=0;
        LocalDate today=LocalDate.now(clock.withZone(DemoFlightTemplates.ZONE));
        window: for(int day=0;day<30;day++) {
            for(var pair:templates.pairs(properties,today.plusDays(day))) {
                for(boolean returning:new boolean[]{false,true}) {
                    try { if(writer.create(pair,returning)) created++; else skipped++; }
                    catch(RuntimeException error) {
                        failed++;
                        log.warn("Demo occurrence skipped: airline={}, date={}, returning={}, failure={}",
                                pair.airline().getCode(),pair.date(),returning,error.getClass().getSimpleName());
                        if(error instanceof org.springframework.transaction.CannotCreateTransactionException
                                || error instanceof org.springframework.dao.DataAccessResourceFailureException) break window;
                    }
                }
            }
        }
        log.info("Demo reconciliation: from={}, through={}, created={}, skipped={}, failed={}",today,today.plusDays(29),created,skipped,failed);
    }
}
