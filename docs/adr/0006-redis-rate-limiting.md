# 0006. Redis fixed-window rate limiting that fails open

**Status:** Accepted

## Context
Registration is open and login needs only an email and password, so anyone can guess passwords, create accounts in bulk, or hold seats repeatedly. Redis is already required for seat leases and refresh tokens, and the backend sits behind proxies, which hides the real client address.

## Decision
Counters live in Redis as fixed windows: the first hit on a key starts its window, `INCR` and the expiry run in one script, and a request over the limit gets `429` with `Retry-After`. Limits per endpoint come from configuration (login, demo login, registration and refresh per client address; seat holds per account). A separate counter tracks wrong passwords per (email, client address), never per email alone, so nobody can lock a victim out by failing logins on purpose; a successful login clears it. Emails are hashed in Redis keys. The client address is the socket address, or the Nth entry from the right of `X-Forwarded-For` for N configured trusted proxy hops, so entries a client forges on the left are ignored. If Redis is unavailable every request is allowed and a throttled warning is logged.

## Consequences
- Slows password guessing and bulk abuse using infrastructure that is already there, with no new dependency.
- A fixed window can let through up to twice the limit around a window boundary, which is acceptable for this purpose. It does not stop a distributed attack from many addresses.
- Failing open keeps login and booking available when Redis is down, at the cost of no protection during that time.
- `RATE_LIMIT_TRUSTED_PROXY_HOPS` must match the deployment. With the default 0 behind a proxy, all customers share one counter; the real header chain has to be checked on the deployed domain.

## Evidence
[`RateLimiter`](../../backend/src/main/java/com/thanh/backend/common/ratelimit/RateLimiter.java), [`RateLimitInterceptor`](../../backend/src/main/java/com/thanh/backend/common/ratelimit/RateLimitInterceptor.java), [`ClientIpResolver`](../../backend/src/main/java/com/thanh/backend/common/ratelimit/ClientIpResolver.java), [`LoginAttemptLimiter`](../../backend/src/main/java/com/thanh/backend/auth/service/LoginAttemptLimiter.java) and the `app.rate-limit` settings in `application.yaml`; `RateLimitIT` (real Redis and HTTP), `RateLimiterTest` (fail-open), `ClientIpResolverTest`.
