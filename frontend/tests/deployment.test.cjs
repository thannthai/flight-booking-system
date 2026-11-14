const { test } = require('node:test')
const assert = require('node:assert/strict')
test('same-origin proxy uses configured upstream and validates production transport',async()=>{const {apiProxyDestination}=await import('../lib/deployment.mjs');assert.equal(apiProxyDestination(),'http://localhost:8080/api/v1');assert.equal(apiProxyDestination('https://backend.example/api/v1/',true),'https://backend.example/api/v1');for(const value of [undefined,'http://backend.example/api/v1','https://user:pass@backend.example/api/v1','https://backend.example/','https://backend.example/api/v1?token=private'])assert.throws(()=>apiProxyDestination(value,true))})
test('return/cancel booking references are validated without trusting provider status',async()=>{const {paymentReturnBookingId}=await import('../lib/deployment.mjs');for(const value of [null,'','-1','1e3','0','9007199254740992','2&status=PAID'])assert.equal(paymentReturnBookingId(value),null);assert.equal(paymentReturnBookingId('42'),42)})
test('Next rewrite preserves /api/v1 endpoint path',async()=>{const config=(await import('../next.config.mjs')).default;const original=process.env.BACKEND_API_URL;process.env.BACKEND_API_URL='https://backend.example/api/v1';try {assert.deepEqual(await config.rewrites(),[{source:'/api/v1/:path*',destination:'https://backend.example/api/v1/:path*'}])}finally{if(original===undefined)delete process.env.BACKEND_API_URL;else process.env.BACKEND_API_URL=original}})
test('backend headers carry the visitor address only with the shared secret and never trust browser-supplied ones', async () => {
  const { clientAddress, backendHeaders } = await import('../lib/client-ip.mjs')
  const secret = '0123456789abcdef0123456789abcdef'
  const incoming = (init) => new Headers(init)
  assert.equal(clientAddress(incoming({ 'x-vercel-forwarded-for': '203.0.113.7, 10.0.0.1' })), '203.0.113.7')
  assert.equal(clientAddress(incoming({ 'x-real-ip': '2001:db8::1' })), '2001:db8::1')
  assert.equal(clientAddress(incoming({ 'x-forwarded-for': '203.0.113.7' })), null)
  assert.equal(clientAddress(incoming({ 'x-vercel-forwarded-for': '<script>' })), null)
  let forwarded = backendHeaders(incoming({ 'x-vercel-forwarded-for': '203.0.113.7' }), secret)
  assert.equal(forwarded.get('x-client-ip'), '203.0.113.7')
  assert.equal(forwarded.get('x-proxy-secret'), secret)
  // A browser cannot pick its own identity or smuggle the secret header.
  forwarded = backendHeaders(incoming({ 'x-client-ip': '9.9.9.9', 'x-proxy-secret': 'guess', 'x-vercel-forwarded-for': '203.0.113.7' }), secret)
  assert.equal(forwarded.get('x-client-ip'), '203.0.113.7')
  forwarded = backendHeaders(incoming({ 'x-client-ip': '9.9.9.9', 'x-proxy-secret': 'guess' }), secret)
  assert.equal(forwarded.get('x-client-ip'), null)
  assert.equal(forwarded.get('x-proxy-secret'), null)
  for (const unusable of [undefined, '', 'too-short']) {
    const without = backendHeaders(incoming({ 'x-vercel-forwarded-for': '203.0.113.7' }), unusable)
    assert.equal(without.get('x-client-ip'), null)
    assert.equal(without.get('x-proxy-secret'), null)
  }
})
