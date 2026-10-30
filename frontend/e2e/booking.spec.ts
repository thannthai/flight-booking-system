import { test, expect, type Page } from '@playwright/test'
import type { CreateBookingRequest } from '../types/api'

async function mockApi(page: Page) {
  const counters = { bookings: 0, links: 0, cancellations: 0, holds: 0 }
  const state = { authenticated: false, paid: false, cancelled: false, loseCreateResponse: false, created: false, linked: false }
  const airport = (id: number, iataCode: string) => ({ id, iataCode, name: `${iataCode} Test Airport`, city: iataCode, country: 'Vietnam', timezone: 'Asia/Ho_Chi_Minh' })
  const origin = airport(1, 'SGN'), dest = airport(2, 'HAN')
  const flight = { id: 1, flightNo: 'TEST-VN01', origin, dest, airline: {id:1,code:'VN',name:'Test Airlines',freeBaggage:false,extraBaggageFee:200000}, aircraft:{id:1,model:'Test A320',totalSeats:6,businessSeats:0,economySeats:6,registrationNo:'TEST-ONLY'}, departureTime:'2099-11-01T10:00:00+07:00',arrivalTime:'2099-11-01T12:00:00+07:00',basePrice:1000000,status:'SCHEDULED',source:'DEMO' }
  const seats = ['1A','1B','1C','1D','1E','1F'].map((seatNo,i)=>({id:i+1,seatNo,cabinClass:'ECONOMY',status:'AVAILABLE',price:1000000}))
  const user = { id:1,fullName:'Browser Tester',email:'browser@example.test',phone:'0901234567',role:'ROLE_USER',createdAt:'2026-01-01T00:00:00Z' }
  const token = () => `e30.${Buffer.from(JSON.stringify({exp:Math.floor(Date.now()/1000)+900})).toString('base64url')}.test-only`
  let request: CreateBookingRequest | undefined
  const booking = () => ({ id:7,pnr:'TEST07',status:state.cancelled?'CANCELLED':state.paid?'COMPLETED':'PENDING',tripType:'ONE_WAY',contactName:request?.contactInfo.fullName || 'Browser Tester',contactEmail:'browser@example.test',contactPhone:'0901234567',createdAt:new Date().toISOString(),paymentDeadline:new Date(Date.now()+600000).toISOString(),baseAmount:1000000,taxAmount:100000,discountAmount:0,totalAmount:1100000,
    passengers:[{id:1,fullName:'Browser Tester'}],tickets:[{id:1,ticketNo:'TEST-TICKET-1',passengerId:1,passengerName:'Browser Tester',passengerType:'ADULT',status:state.paid?'ISSUED':'PENDING',price:1000000,flight,seat:seats[0],addOns:[]}] })
  const payment = () => ({id:9,bookingId:7,orderCode:777,provider:'PAYOS',currency:'VND',amount:1100000,status:state.cancelled?'CANCELLED':state.paid?'SUCCESS':'PENDING',refundStatus:'NONE',paymentUrl:'https://pay.payos.vn/web/test-only',qrCode:'TEST_ONLY_NOT_A_REAL_PAYMENT_PAYLOAD',expiredAt:booking().paymentDeadline,createdAt:new Date().toISOString()})
  const paged = (content: unknown[]) => ({content,pageNumber:0,pageSize:100,totalElements:content.length,totalPages:1,last:true})
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin !== 'http://127.0.0.1:3100') return route.abort('blockedbyclient')
    if (!url.pathname.startsWith('/api/v1')) return route.continue()
    const path = url.pathname.slice('/api/v1'.length), method = route.request().method()
    const ok = (data: unknown, headers = {}) => route.fulfill({status:200,contentType:'application/json',headers,body:JSON.stringify({success:true,data,timestamp:new Date().toISOString()})})
    if (path === '/auth/login') { state.authenticated=true; return ok({accessToken:token(),user},{'set-cookie':'refreshToken=browser-test-only; HttpOnly; SameSite=Lax; Path=/api/v1/auth'}) }
    if (path === '/auth/refresh' && state.authenticated) return ok({accessToken:token(),user})
    if (path === '/auth/logout') {state.authenticated=false;return ok(null,{'set-cookie':'refreshToken=; Max-Age=0; Path=/api/v1/auth'})}
    if (path === '/airports') return ok(paged([origin,dest]))
    if (path === '/airlines') return ok(paged([flight.airline]))
    if (path === '/flights/search') return ok(paged([flight]))
    if (path === '/flights/1/seats') return ok({flightId:1,flightNo:flight.flightNo,aircraft:flight.aircraft,seats})
    if (path === '/passengers/my') return ok([])
    if (path === '/bookings/hold-seats') { counters.holds++;return ok({heldSeats:[1],expiresAt:new Date(Date.now()+600000).toISOString(),ttlSeconds:600}) }
    if (path === '/bookings' && method === 'POST') {counters.bookings++;request=route.request().postDataJSON();state.created=true;if(state.loseCreateResponse)return route.abort('failed');return ok(booking())}
    if (path === '/bookings/my') return ok(paged(state.created?[booking()]:[]))
    if (path === '/bookings/7') return ok(booking())
    if (path === '/bookings/7/cancel') {counters.cancellations++;state.cancelled=true;return ok(null)}
    if (path === '/payments/create-link') {counters.links++;state.linked=true;return ok(payment())}
    if (path === '/payments/booking/7') return ok({bookingId:7,bookingStatus:booking().status,paymentStatus:state.linked?payment().status:'NOT_CREATED',payment:state.linked?payment():null})
    return route.fulfill({status:401,contentType:'application/json',body:JSON.stringify({success:false,message:'Sign in required',errorCode:'UNAUTHORIZED'})})
  })
  return {state,counters}
}
async function login(page: Page) {
  const dialog = page.getByRole('dialog',{name:'Authentication'})
  await dialog.getByPlaceholder('name@example.com').fill('browser@example.test')
  await dialog.getByPlaceholder('Enter your password').fill('TestPassword123@')
  await dialog.locator('button[type="submit"]').click()
  await expect(dialog).toBeHidden()
}
async function chooseSeats(page: Page) {
  await page.goto('/')
  await page.getByRole('button',{name:'One way',exact:true}).click()
  await page.getByRole('button',{name:'Search flights',exact:true}).click()
  await page.getByRole('button',{name:'Select flight',exact:true}).click()
  await page.locator('#contact_name').fill('Browser Tester')
  await page.locator('#contact_email').fill('browser@example.test')
  await page.locator('#contact_phone').fill('0901234567')
  await page.locator('#pax_0_name').fill('Browser Tester')
  await page.locator('#pax_0_dob').fill('1990-01-01')
  await page.locator('#pax_0_id').fill('TEST-ID-123')
  await page.getByRole('button',{name:'Continue to Seats'}).click()
  await page.getByRole('button',{name:'Auto-assign Seats'}).click()
  await page.getByRole('button',{name:'Continue to Payment'}).click()
  await login(page)
  await expect(page.getByRole('button',{name:'Confirm Booking',exact:true})).toBeEnabled()
}
async function history(page: Page) {
  await page.getByRole('button',{name:'Browser Tester'}).click()
  await page.getByRole('button',{name:'My Bookings & Passes'}).click()
  return page.getByRole('dialog',{name:'My Bookings',exact:true})
}
test('guest login continuation, create booking, QR, reload, status-driven success, tickets/history and logout',async({page})=>{
  const {state,counters}=await mockApi(page)
  await chooseSeats(page)
  await page.getByRole('button',{name:'Confirm Booking',exact:true}).click()
  const qr=page.getByRole('img',{name:'Payment QR supplied by payOS'})
  await expect(qr).toBeVisible();await expect(qr).toHaveAttribute('src',/^data:image\/png/)
  await page.reload();await expect(qr).toBeVisible()
  expect(counters.bookings).toBe(1);expect(counters.links).toBe(1);expect(counters.holds).toBe(1)
  state.paid=true // Mock gateway/webhook result already persisted by the backend contract, not a real webhook test.
  await page.getByRole('button',{name:'Check / Retry Payment Status'}).click()
  await expect(page.getByText('Payment confirmed.',{exact:false})).toBeVisible()
  const modal=await history(page)
  await modal.getByRole('button',{name:'View E-Tickets'}).click()
  const ticket=page.getByRole('dialog',{name:'Demo E-Tickets'})
  await expect(ticket.getByText('TEST-TICKET-1').first()).toBeVisible()
  await page.keyboard.press('Escape');await modal.getByRole('button',{name:'Close',exact:true}).click()
  await page.getByRole('button',{name:'Browser Tester'}).click();await page.getByRole('button',{name:'Sign Out'}).click()
  await expect(page.getByRole('button',{name:'Sign In',exact:true})).toBeVisible()
  expect(await page.evaluate(()=>sessionStorage.getItem('skyway_session'))).toBeNull()
})
test('explicit cancellation removes QR and persists cancelled history',async({page})=>{
  const {counters}=await mockApi(page);await chooseSeats(page)
  await page.getByRole('button',{name:'Confirm Booking',exact:true}).click()
  await expect(page.getByRole('img',{name:'Payment QR supplied by payOS'})).toBeVisible()
  page.on('dialog',dialog=>dialog.accept())
  await page.getByRole('button',{name:'Cancel Booking',exact:true}).click()
  await expect(page.getByText('Booking or payment cancelled.',{exact:false})).toBeVisible()
  await expect(page.getByRole('img',{name:'Payment QR supplied by payOS'})).toHaveCount(0)
  const modal=await history(page);await expect(modal.getByText('CANCELLED',{exact:true})).toBeVisible();expect(counters.cancellations).toBe(1)
})
test('unknown creation result survives reload, resumes history without second create',async({page})=>{
  const {state,counters}=await mockApi(page);await chooseSeats(page);state.loseCreateResponse=true
  await page.getByRole('button',{name:'Confirm Booking',exact:true}).click()
  await expect(page.getByText('A previous reservation request may have succeeded.',{exact:false})).toBeVisible()
  await page.reload();await expect(page.getByRole('button',{name:'Confirm Booking',exact:true})).toHaveCount(0)
  const modal=await history(page);await modal.getByRole('button',{name:'Complete Payment'}).click()
  await expect(page.getByRole('img',{name:'Payment QR supplied by payOS'})).toBeVisible();expect(counters.bookings).toBe(1)
})
test('mobile sign-in and flight search remain reachable',async({page})=>{
  await page.setViewportSize({width:390,height:844});await mockApi(page);await page.goto('/')
  await page.getByRole('button',{name:'Sign In',exact:true}).click();await login(page)
  await page.getByRole('button',{name:'One way',exact:true}).click();await page.getByRole('button',{name:'Search flights',exact:true}).click()
  await expect(page.getByRole('button',{name:'Select flight',exact:true})).toBeVisible()
  await expect(page.getByText('DEMO · Simulated flight')).toBeVisible()
})
