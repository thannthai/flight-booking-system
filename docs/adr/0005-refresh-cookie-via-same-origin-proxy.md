# 0005. Refresh cookie through a same-origin proxy with single-use rotation

**Status:** Accepted

## Context
The frontend and backend are deployed separately. A refresh token that JavaScript can read is exposed to script injection, and a cross-site cookie needs permissive cookie and CORS settings. Refresh tokens should also be usable once, even when two requests present the same token at the same moment.

## Decision
The access token lives only in browser memory and is sent as a Bearer header. The refresh token is an `HttpOnly` cookie scoped to `/api/v1/auth`, `SameSite=Lax`, and `Secure` in production. The browser always calls same-origin `/api/v1`; a Next.js rewrite forwards it to the backend, so the cookie is first-party. Refresh consumes the token's Redis record with a single checked `DELETE`, and only the request that wins the delete receives new tokens. In the browser, a Web Lock serializes refresh, login and logout across tabs, and responses from an obsolete session are discarded.

## Consequences
- Script cannot read the refresh token, and cross-site requests do not carry it.
- A replayed refresh token is rejected, but reuse of an old token does not revoke the whole token family.
- Without Web Locks support, tabs may race; the server still lets only one refresh succeed.
- Cookie forwarding and the `Secure` flag depend on hosting configuration, which the repository tests do not prove; the deployed domain needs a manual check.

## Evidence
[`JwtService`](../../backend/src/main/java/com/thanh/backend/auth/service/JwtService.java) (`consumeRefreshToken`), [`CookieUtils`](../../backend/src/main/java/com/thanh/backend/common/utils/CookieUtils.java), [`next.config.mjs`](../../frontend/next.config.mjs), [`auth-session.ts`](../../frontend/lib/auth-session.ts); `RefreshRotationIT` (concurrent refresh with one token), `ProductionEnvironmentTest` (Secure cookie) and the frontend auth tests, including the two-tab Web Lock test.
