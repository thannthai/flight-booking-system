# 0007. The frontend proxy forwards the visitor address with a shared secret

**Status:** Accepted. Refines [0006](0006-redis-rate-limiting.md).

## Context
Rate limits count requests per visitor, but the backend sits behind the Next.js rewrite on Vercel and only sees the proxy. Reading `X-Forwarded-For` by hop count did not help: in testing, two clients on different networks still shared one counter, so the visitor address does not reach the backend that way.

## Decision
`proxy.ts` in the Next.js app runs on `/api/v1` requests and sets two request headers for the rewrite destination: `X-Client-IP`, taken only from headers Vercel sets itself (`x-vercel-forwarded-for`, then `x-real-ip`), and `X-Proxy-Secret`, a secret shared with the backend. Any `X-Client-IP` or `X-Proxy-Secret` the browser sent is removed first. The backend uses `X-Client-IP` only when the secret matches (compared in constant time) and the value looks like an address; otherwise it falls back to the hop-count logic and then the socket address.

## Consequences
- Each visitor gets their own counter behind Vercel, and a caller reaching the backend directly cannot pick its identity without knowing the secret.
- The secret must be set identically on Vercel and the backend. If either side is missing it, the feature quietly falls back to the shared counter rather than failing.
- Anyone who learns the secret can claim any address, so it must stay server-side and be rotated like other secrets.
- The header names Vercel sets are taken from its documentation and verified only locally; confirm on the deployed domain with the two-network check in the architecture notes.

## Evidence
[`proxy.ts`](../../frontend/proxy.ts), [`client-ip.mjs`](../../frontend/lib/client-ip.mjs) and its Node test; [`ClientIpResolver`](../../backend/src/main/java/com/thanh/backend/common/ratelimit/ClientIpResolver.java) with `ClientIpResolverTest` and `RateLimitIT` (valid secret gives separate counters, a forged address without the secret does not escape the limit).
