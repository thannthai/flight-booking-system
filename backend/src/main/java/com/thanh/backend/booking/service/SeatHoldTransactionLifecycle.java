package com.thanh.backend.booking.service;

import com.thanh.backend.booking.infrastructure.redis.SeatHoldLeaseStore;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import com.thanh.backend.flight.repository.FlightSeatRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Component
public class SeatHoldTransactionLifecycle {
    private final SeatHoldLeaseStore leases;
    private final FlightSeatRepository seats;
    private final PlatformTransactionManager transactionManager;
    private final ThreadPoolTaskExecutor cleanupExecutor;

    public SeatHoldTransactionLifecycle(SeatHoldLeaseStore leases, FlightSeatRepository seats,
                                        PlatformTransactionManager transactionManager,
                                        @Qualifier("seatHoldCleanupExecutor") ThreadPoolTaskExecutor cleanupExecutor) {
        this.leases = leases;
        this.seats = seats;
        this.transactionManager = transactionManager;
        this.cleanupExecutor = cleanupExecutor;
    }

    // Register before SET NX: a Redis write can succeed even when its reply is lost.
    public void cleanupAcquisitionOnRollback(SeatHoldLeaseStore.Lease candidate) {
        requireTransaction();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) return;
                if (status != STATUS_ROLLED_BACK) {
                    log.warn("Seat hold transaction outcome unknown; retaining lease until expiry, seatId={}", candidate.seatId());
                    return;
                }
                try {
                    // Return the completed transaction's connection before attempting
                    // another one. Never block the caller on a cleanup connection.
                    cleanupExecutor.execute(() -> cleanupRolledBackCandidate(candidate));
                } catch (RuntimeException error) {
                    logCleanupFailure(candidate, error);
                }
            }
        });
    }

    private void cleanupRolledBackCandidate(SeatHoldLeaseStore.Lease candidate) {
        try {
            // Avoid DB work for candidates that never won SET NX or already expired.
            if (leases.readOwned(candidate.seatId(), candidate.userId()).filter(candidate::equals).isEmpty()) return;
            TransactionTemplate cleanup = new TransactionTemplate(transactionManager);
            cleanup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            cleanup.setTimeout(3);
            cleanup.executeWithoutResult(ignored -> {
                if (!Boolean.TRUE.equals(seats.hasLiveHoldForLeaseCleanup(candidate.seatId()))) {
                    leases.release(candidate, candidate.userId());
                }
            });
        } catch (RuntimeException error) {
            logCleanupFailure(candidate, error);
        }
    }

    public void consumeAfterCommit(List<SeatHoldLeaseStore.Hold> validatedHolds, long actorId) {
        requireTransaction();
        List<SeatHoldLeaseStore.Hold> snapshots = List.copyOf(validatedHolds);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                for (SeatHoldLeaseStore.Hold hold : snapshots) {
                    if (!leases.isCurrent(hold, actorId)) {
                        throw new AppException(ErrorCode.SEAT_NOT_AVAILABLE);
                    }
                }
            }

            @Override
            public void afterCommit() {
                // Commit has succeeded. Cleanup errors must never report booking failure.
                // Compare-delete also fences cancellation/reacquisition before this callback.
                for (SeatHoldLeaseStore.Hold hold : snapshots) {
                    try {
                        leases.release(hold.lease(), actorId);
                    } catch (RuntimeException error) {
                        logCleanupFailure(hold.lease(), error);
                    }
                }
            }
        });
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Seat hold lifecycle requires a database transaction");
        }
    }

    private static void logCleanupFailure(SeatHoldLeaseStore.Lease lease, RuntimeException error) {
        // No raw key values, tokens or exception payloads (which may include Redis arguments).
        log.warn("Seat hold cleanup deferred to original expiry, seatId={}, failure={}",
                lease.seatId(), error.getClass().getSimpleName());
    }
}
