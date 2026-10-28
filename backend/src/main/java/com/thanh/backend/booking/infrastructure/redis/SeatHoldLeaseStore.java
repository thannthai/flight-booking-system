package com.thanh.backend.booking.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SeatHoldLeaseStore {
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> DEADLINE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('PEXPIRETIME', KEYS[1])
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local acquired
            if ARGV[3] == '' then
                acquired = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2])
            else
                acquired = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PXAT', ARGV[3])
            end
            if acquired then
                return redis.call('PEXPIRETIME', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public record Lease(long seatId, long userId, UUID token) {
        public Lease {
            if (seatId <= 0 || userId <= 0 || token == null)
                throw new IllegalArgumentException("Invalid lease identity");
        }

        private String value() {
            return userId + ":" + token;
        }

        @Override
        public String toString() {
            return "Lease[seatId=" + seatId + ", userId=" + userId + ", token=redacted]";
        }
    }

    public record Hold(Lease lease, OffsetDateTime expiresAt, boolean acquired) { }

    // Actor IDs come from authenticated backend callers; lease tokens are never
    // accepted from API requests.
    public Optional<Hold> acquireOrReuse(long seatId, long actorId, Duration ttl, OffsetDateTime liveDbDeadline) {
        return acquireOrReuse(new Lease(seatId, actorId, UUID.randomUUID()), ttl, liveDbDeadline);
    }

    // The caller can register this identity for rollback cleanup before any Redis write.
    public Optional<Hold> acquireOrReuse(Lease candidate, Duration ttl, OffsetDateTime liveDbDeadline) {
        requirePositiveTtl(ttl);
        long seatId = candidate.seatId();
        long actorId = candidate.userId();
        String current = redis.opsForValue().get(key(seatId));
        if (current != null) {
            // Never extend the existing deadline or adopt a newer generation after this snapshot.
            return decodeOwned(seatId, actorId, current).flatMap(lease ->
                    readDeadline(lease, actorId).map(deadline -> new Hold(lease, deadline, false)));
        }
        // A missing Redis key cannot restart a still-live DB hold for the same user.
        // SET NX and its expiry are atomic; Redis supplies the exact millisecond deadline.
        Long deadline = redis.execute(ACQUIRE, List.of(key(seatId)), candidate.value(), Long.toString(ttl.toMillis()),
                liveDbDeadline == null ? "" : Long.toString(liveDbDeadline.toInstant().toEpochMilli()));
        return asDeadline(deadline).map(expiresAt -> new Hold(candidate, expiresAt, true));
    }

    public Optional<Lease> readOwned(long seatId, long actorId) {
        return decodeOwned(seatId, actorId, redis.opsForValue().get(key(seatId)));
    }

    public Optional<OffsetDateTime> readDeadline(Lease expected, long actorId) {
        if (expected == null || expected.userId() != actorId)
            return Optional.empty();
        return asDeadline(redis.execute(DEADLINE, List.of(key(expected.seatId())), expected.value()));
    }

    public boolean isCurrent(Hold expected, long actorId) {
        return readDeadline(expected.lease(), actorId).filter(expected.expiresAt()::equals).isPresent();
    }

    public boolean release(Lease expected, long actorId) {
        if (expected == null || expected.userId() != actorId)
            return false;
        return Long.valueOf(1).equals(redis.execute(RELEASE, List.of(key(expected.seatId())), expected.value()));
    }

    private Optional<Lease> decodeOwned(long seatId, long actorId, String value) {
        if (value == null)
            return Optional.empty();
        String[] parts = value.split(":", -1);
        // Legacy userId-only values fail closed and expire normally; never
        // upgrade/delete an unidentified lease.
        if (parts.length != 2 || !parts[0].equals(Long.toString(actorId)))
            return Optional.empty();
        try {
            UUID token = UUID.fromString(parts[1]);
            return token.toString().equals(parts[1])
                    ? Optional.of(new Lease(seatId, actorId, token))
                    : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String key(long seatId) {
        return "hold:seat:" + seatId;
    }

    private static Optional<OffsetDateTime> asDeadline(Long epochMillis) {
        return epochMillis != null && epochMillis > 0
                ? Optional.of(Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC))
                : Optional.empty();
    }

    private static void requirePositiveTtl(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.toMillis() < 1) {
            throw new IllegalArgumentException("Lease TTL must be positive");
        }
    }
}
