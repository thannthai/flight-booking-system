const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
const { paymentView, preparePayment, checkoutUrl, createBookingOnce, hasUnresolvedBooking, storedBookingId } = require('../lib/payment.ts')
const { ApiError } = require('../lib/api.ts')
const booking = { id: 4, status: 'PENDING', paymentDeadline: '2099-01-01T00:00:00Z' }
const snapshot = (paymentStatus, status = 'PENDING', refundStatus = 'NONE') => ({ booking: { ...booking, status }, status: { paymentStatus, payment: paymentStatus === 'NOT_CREATED' ? null : { status: paymentStatus, refundStatus, paymentUrl: 'https://pay.payos.vn/test' } } })
beforeEach(() => { const data = new Map(); global.sessionStorage = { getItem: k => data.get(k) ?? null, setItem: (k,v) => data.set(k,v), removeItem: k => data.delete(k) } })
for (const [state, bookingStatus, expected] of [['NOT_CREATED','PENDING','ready'],['PENDING','PENDING','pending'],['SUCCESS','COMPLETED','success'],['CANCELLED','CANCELLED','cancelled'],['EXPIRED','PENDING','expired'],['PAID_AFTER_TERMINAL','CANCELLED','refund']]) {
 test(`server state ${state} maps to ${expected}`, () => assert.equal(paymentView(snapshot(state, bookingStatus)), expected))
}
test('refund marker wins over success; deadline does not mutate server', () => { assert.equal(paymentView(snapshot('SUCCESS','COMPLETED','REFUNDED')), 'refund'); const s = snapshot('PENDING'); assert.equal(paymentView(s, Date.parse('2100-01-01')), 'expired'); assert.equal(s.booking.status, 'PENDING') })
test('reload completed booking never creates a payment link', async () => { let posts=0; const s=snapshot('SUCCESS','COMPLETED'); const got=await preparePayment(4,{get:async()=>s.status,getBookingById:async()=>s.booking,post:async()=>posts++}); assert.equal(paymentView(got),'success'); assert.equal(posts,0) })
test('existing pending payment reused; no repeated create-link', async () => { let posts=0; const s=snapshot('PENDING'); await preparePayment(5,{get:async()=>s.status,getBookingById:async()=>s.booking,post:async()=>posts++}); assert.equal(posts,0) })
test('concurrent prepare joins one creation, re-reads authoritative result', async () => { let posts=0; let created=false; const client={get:async()=>snapshot(created?'PENDING':'NOT_CREATED').status,getBookingById:async()=>booking,post:async()=>{posts++;created=true}}; const [a,b]=await Promise.all([preparePayment(6,client),preparePayment(6,client)]); assert.equal(posts,1); assert.equal(a,b); assert.equal(paymentView(a),'pending') })
test('failed initialization can be explicitly retried after reading state', async () => { let posts=0; const client={get:async()=>snapshot('NOT_CREATED').status,getBookingById:async()=>booking,post:async()=>{posts++; if(posts===1) throw new Error('network'); return {}}}; await assert.rejects(preparePayment(7,client)); await preparePayment(7,client); assert.equal(posts,2) })
test('unsafe or untrusted checkout URLs are rejected', () => { for(const url of ['javascript:alert(1)','http://pay.payos.vn/x','https://payos.vn.evil.test','https://user@pay.payos.vn/x','//evil.test']) assert.equal(checkoutUrl(url),null); assert.ok(checkoutUrl('https://pay.payos.vn/web/abc')) })
test('unknown booking result survives remount/reload marker and cannot be retried', async () => { let calls=0; const client={post:async()=>{calls++;throw new Error('response lost')}}; await assert.rejects(createBookingOnce({},client)); assert.equal(hasUnresolvedBooking(),true); await assert.rejects(createBookingOnce({},client),/Check My Bookings/); assert.equal(calls,1) })
test('definitive validation rejection allows an explicit retry', async () => { await assert.rejects(createBookingOnce({}, {post:async()=>{throw new ApiError('seat unavailable','SEAT_NOT_AVAILABLE',400)}})); assert.equal(hasUnresolvedBooking(),false); const result=await createBookingOnce({}, {post:async()=>booking}); assert.equal(result.id,4); assert.equal(storedBookingId(),4) })
test('pending in-flight booking blocks a second submission', async () => { let release; const pending=createBookingOnce({}, {post:()=>new Promise(r=>release=r)}); await assert.rejects(createBookingOnce({}, {post:async()=>{throw Error('must not send')}})); release(booking); await pending })

test('demo pending payment never creates a payOS link on reload', async () => { const s=snapshot('PENDING'); s.status.payment.provider='DEMO'; delete s.status.payment.paymentUrl; let posts=0; await preparePayment(44,{get:async()=>s.status,getBookingById:async()=>s.booking,post:async()=>posts++}); assert.equal(posts,0) })

test('persisted PAYOS pending intent without QR or valid checkout is not ready for payment',()=>{
 const s=snapshot('PENDING');s.status.payment.provider='PAYOS';delete s.status.payment.paymentUrl
 assert.equal(paymentView(s),'initializing');s.status.payment.paymentUrl='https://evil.example/x';assert.equal(paymentView(s),'initializing')
 s.status.payment.paymentUrl='https://pay.payos.vn/web/test';assert.equal(paymentView(s),'pending')
})

test('timeout cancellation remains expired, while manual and unknown cancellations stay cancelled',()=>{
 const timeout=snapshot('EXPIRED','CANCELLED');timeout.booking.cancellationReason='PAYMENT_TIMEOUT'
 assert.equal(paymentView(timeout),'expired')
 for(const reason of ['USER_CANCELLED','OTHER',undefined]){
  const manual=snapshot('CANCELLED','CANCELLED');manual.booking.cancellationReason=reason;manual.booking.paymentDeadline='2000-01-01'
  assert.equal(paymentView(manual),'cancelled')
 }
 timeout.status.payment.refundStatus='REFUND_REQUIRED';assert.equal(paymentView(timeout),'refund')
 const success=snapshot('SUCCESS','COMPLETED');success.booking.paymentDeadline='2000-01-01';assert.equal(paymentView(success),'success')
})
