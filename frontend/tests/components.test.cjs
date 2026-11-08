const { test, beforeEach, afterEach } = require('node:test')
const assert = require('node:assert/strict')
const { JSDOM } = require('jsdom')
const dom = new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>', {url:'https://frontend.example/'})
for(const name of ['window','document','HTMLElement','HTMLInputElement','Element','Node','MutationObserver','DocumentFragment','CustomEvent','Event','MouseEvent','KeyboardEvent','sessionStorage','localStorage'])global[name]=dom.window[name]
global.getComputedStyle=dom.window.getComputedStyle.bind(dom.window)
global.requestAnimationFrame=fn=>setTimeout(fn,0)
global.cancelAnimationFrame=clearTimeout
window.scrollTo=()=>{}
HTMLElement.prototype.scrollIntoView=()=>{}
global.IS_REACT_ACT_ENVIRONMENT=true
const React=require('react'),{act}=React,{createRoot}=require('react-dom/client')
const {AppRouterContext}=require('next/dist/shared/lib/app-router-context.shared-runtime')
const {SearchParamsContext}=require('next/dist/shared/lib/hooks-client-context.shared-runtime')
const router={
 push(url){window.history.pushState({},'',url);window.dispatchEvent(new dom.window.PopStateEvent('popstate'))},
 replace(url){window.history.replaceState({},'',url);window.dispatchEvent(new dom.window.PopStateEvent('popstate'))},
 back(){window.history.back()},forward(){window.history.forward()},refresh(){},prefetch:async()=>{},
}
const {api,invalidateAuth}=require('../lib/api.ts')
const {AuthProvider}=require('../lib/auth-context.tsx')
const {AuthModal}=require('../components/auth/AuthModal.tsx')
const {VietQRPayment}=require('../components/payment/VietQRPayment.tsx')
const {SeatSelection}=require('../components/seats/SeatSelection.tsx')
const {FlightResults}=require('../components/flights/FlightResults.tsx')
const {BoardingPassModal}=require('../components/booking/BoardingPassModal.tsx')
const {adaptFlight}=require('../lib/flight.ts')
const qr=require('qrcode')
const original={get:api.get,post:api.post,getBookingById:api.getBookingById,qr:qr.toDataURL}
const jwt=()=>`e30.${Buffer.from(JSON.stringify({exp:Date.now()/1000+900})).toString('base64url')}.test`
const authResponse=()=>({ok:true,status:200,json:async()=>({data:{accessToken:jwt(),user:{id:1,fullName:'Tester',email:'tester@example.test'}}})})
const flight={id:1,flightNo:'DEMO-VN',airline:{name:'Vietnam Airlines',code:'VN'},aircraft:{model:'A321'},origin:{id:1,iataCode:'SGN',city:'SGN',timezone:'Asia/Ho_Chi_Minh'},dest:{id:2,iataCode:'SIN',city:'SIN',timezone:'Asia/Singapore'},departureTime:'2099-11-01T09:00:00+07:00',arrivalTime:'2099-11-01T12:05:00+08:00',basePrice:4500000,source:'DEMO'}
const booking={id:7,pnr:'ABCDEF',status:'PENDING',paymentDeadline:'2099-12-01T00:00:00Z',totalAmount:1000000,passengers:[{id:1}],tickets:[],createdAt:'2026-10-29T00:00:00Z'}
const page=content=>({content,last:true,totalPages:1,totalElements:content.length,pageNumber:0,pageSize:100})
let root,container
const flush=()=>new Promise(r=>setTimeout(r,0))
async function render(node){await act(async()=>{root.render(React.createElement(AppRouterContext.Provider,{value:router},React.createElement(SearchParamsContext.Provider,{value:new URLSearchParams(window.location.search)},React.createElement(AuthProvider,null,node))));await flush()})}
const button=text=>[...container.querySelectorAll('button')].find(b=>b.textContent.includes(text))
beforeEach(()=>{window.history.replaceState({},'', '/');sessionStorage.clear();localStorage.clear();invalidateAuth(false);global.fetch=async()=>authResponse();api.get=async()=>page([]);api.post=async()=>({});api.getBookingById=async()=>booking;container=document.getElementById('root');root=createRoot(container)})
afterEach(async()=>{await act(async()=>root.unmount());Object.assign(api,{get:original.get,post:original.post,getBookingById:original.getBookingById});qr.toDataURL=original.qr})
for(const invalidCredentials of [true,false]) test(`login renders ${invalidCredentials?'credential error code':'fallback for unknown rejection'} without closing the dialog`,async()=>{
 const {useAuth}=require('../lib/auth-context.tsx')
 const {ApiError}=require('../lib/api.ts')
 const savedLogin=api.login
 function OpenAuth(){const {openAuthModal}=useAuth();return React.createElement('button',{onClick:()=>openAuthModal('login')},'Open auth')}
 api.login=async()=>{throw invalidCredentials?new ApiError('Login rejected','INVALID_CREDENTIALS',401):null}
 try{
  await render(React.createElement(React.Fragment,null,React.createElement(OpenAuth),React.createElement(AuthModal)))
  await act(async()=>button('Open auth').click())
  const setValue=(input,value)=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}))}
  await act(async()=>{setValue(container.querySelector('input[type="email"]'),'test@example.test');setValue(container.querySelector('input[type="password"]'),'TestPassword123@')})
  await act(async()=>{container.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await flush()})
  assert.match(container.textContent,invalidCredentials?/Incorrect email or password/:/Login failed\. Please check your credentials/)
  assert.ok(container.querySelector('[role="dialog"]'))
  assert.equal(container.querySelector('button[type="submit"]').disabled,false)
 }finally{api.login=savedLogin}
})
test('payment mount/reload reads completed booking, never creates booking/link or synthetic tickets',async()=>{let posts=0;api.post=async()=>posts++;api.get=async()=>({bookingStatus:'COMPLETED',paymentStatus:'SUCCESS',payment:{status:'SUCCESS',refundStatus:'NONE'}});api.getBookingById=async()=>({...booking,status:'COMPLETED'});await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>{}}));assert.match(document.body.textContent,/Payment confirmed/);assert.equal(posts,0);await act(async()=>button('View Tickets').click());assert.match(document.body.textContent,/No ticket details/);assert.doesNotMatch(document.body.textContent,/Gate 06|VN 123/)})
test('pending QR encodes exact backend payload locally; terminal update removes QR',async()=>{let state='PENDING',encoded;qr.toDataURL=async payload=>{encoded=payload;return 'data:image/png;base64,TEST'};api.get=async()=>({bookingStatus:'PENDING',paymentStatus:state,payment:{status:state,refundStatus:'NONE',qrCode:'BACKEND-EMV-PAYLOAD',paymentUrl:'https://pay.payos.vn/web/7'}});await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>{}}));assert.equal(encoded,'BACKEND-EMV-PAYLOAD');assert.ok(container.querySelector('img[alt="Payment QR supplied by payOS"]'));state='CANCELLED';await act(async()=>{assert.equal(button('Retry'),undefined);await new Promise(resolve=>setTimeout(resolve,3200))});assert.equal(container.querySelector('img[alt="Payment QR supplied by payOS"]'),null);assert.match(container.textContent,/Do not transfer money/)})
test('expired countdown never posts cancel; explicit booking confirmation does not run at mount',async()=>{let posts=0;api.post=async()=>posts++;api.get=async()=>({paymentStatus:'EXPIRED',payment:null});api.getBookingById=async()=>({...booking,paymentDeadline:'2000-01-01'});await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>{}}));assert.equal(posts,0);assert.match(container.textContent,/deadline/);await render(React.createElement(VietQRPayment,{flight:{id:1},passengerData:{paxList:[],passengerRequests:[],contactInfo:{}},holdExpiresAt:'2099-01-01',onHome:()=>{}}));assert.equal(posts,0);assert.ok(button('Confirm booking & continue to payment'))})
test('seat auto-assign excludes business and historical unavailable seats',async()=>{const seat=(id,status,cabinClass,price)=>({id,seatNo:`${id}A`,status,cabinClass,price});api.get=async path=>path.includes('/seats')?{seats:[seat(1,'AVAILABLE','BUSINESS',2500000),seat(2,'HELD','ECONOMY',1000000),seat(3,'AVAILABLE','ECONOMY',1000000)]}:page([]);let held;api.post=async(path,body)=>{held=body.seatIds;return {expiresAt:'2099-01-01'}};await render(React.createElement(SeatSelection,{flight:adaptFlight(flight),passengerData:{paxList:[{type:'Adult',title:'Passenger 1'}],passengers:[{name:'Tester'}]},cabinClass:'ECONOMY',historicallySelectedSeats:[2],onHome:()=>{},onBack:()=>{},onContinue:()=>{}}));await act(async()=>button('Auto-assign').click());await act(async()=>{button('Continue to Payment').click();await flush()});assert.deepEqual(held,[3])})
test('guest login callback continues seat hold without reopening the login modal',async()=>{global.fetch=async url=>url.endsWith('/auth/login')?authResponse():({ok:false,status:401,json:async()=>({})});const seat={id:3,seatNo:'3A',status:'AVAILABLE',cabinClass:'ECONOMY',price:1000000};api.get=async path=>path.includes('/seats')?{seats:[seat]}:page([]);let holds=0;api.post=async()=>{holds++;return {expiresAt:'2099-01-01'}};await render(React.createElement(React.Fragment,null,React.createElement(SeatSelection,{flight:adaptFlight(flight),passengerData:{paxList:[{type:'Adult',title:'Passenger 1'}],passengers:[{name:'Tester'}]},onHome:()=>{},onBack:()=>{},onContinue:()=>{}}),React.createElement(AuthModal)));await act(async()=>button('Auto-assign').click());await act(async()=>button('Continue to Payment').click());const setValue=(input,value)=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}))};await act(async()=>{setValue(container.querySelector('input[type="email"]'),'test@example.test');setValue(container.querySelector('input[type="password"]'),'Password123@')});await act(async()=>{container.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await flush()});assert.equal(holds,1);assert.equal(container.querySelector('[role="dialog"]'),null)})
test('late flight response is ignored after selecting another date',async()=>{let resolveOld;let queries=0;api.get=async path=>{if(path.startsWith('/airlines'))return page([]);queries++;if(queries===1)return new Promise(r=>resolveOld=r);return page([{...flight,id:2,flightNo:'NEW-DATE'}])};await render(React.createElement(FlightResults,{searchParams:{originCode:'SGN',destinationCode:'SIN',departureDate:'2099-11-01',returnDate:null,passengers:{adults:1,children:0,infants:0},cabinClass:'ECONOMY'},onModify:()=>{},onSelectFlight:()=>{}}));const dateButtons=[...container.querySelectorAll('button')].filter(b=>b.textContent.includes('NOV'));await act(async()=>{dateButtons[dateButtons.length-1].click();await flush()});await act(async()=>{resolveOld(page([{...flight,flightNo:'STALE-DATE'}]));await flush()});assert.match(container.textContent,/NEW-DATE/);assert.doesNotMatch(container.textContent,/STALE-DATE/);const slider=container.querySelector('input[type="range"]');assert.ok(Number(slider.max)>=4500000);assert.match(container.textContent,/Simulated schedule/);assert.match(container.textContent,/No limit/)})
test('ticket uses airport timezone and contains no fake operational boarding information',async()=>{await act(async()=>root.render(React.createElement(BoardingPassModal,{isOpen:true,onClose:()=>{},booking:{...booking,status:'COMPLETED',tickets:[{id:1,ticketNo:'REAL-123',flight,passengerName:'Tester',passengerType:'ADULT',status:'ISSUED',seat:{seatNo:'3A',cabinClass:'ECONOMY'},addOns:[]}]}})));assert.match(document.body.textContent,/12:05/);assert.match(document.body.textContent,/E-Tickets/);assert.match(document.body.textContent,/not an airline boarding pass/);assert.doesNotMatch(document.body.textContent,/Gate 06|Terminal 1|Official Boarding/);assert.match(document.body.textContent,/REAL-123/)})
test('booking draft reload restores form; account switch removes prior contact and private storage',async()=>{
 const BookingApp=require('../app/page.tsx').default
 const {getCurrentUser}=require('../lib/api.ts')
 await api.login({email:'tester@example.test',password:'test-only'})
 const form={contactInfo:{name:'PRIVATE ALICE',email:'alice@example.test',phone:'0123456789'},passengers:[{title:'Mr',name:'PRIVATE ALICE',dob:'1990-01-01',idType:'CITIZEN_ID',id:'123456789',idExpiry:'',nationality:'Vietnam'}],passengerAddOns:{}}
 const session={version:3,ownerId:1,updatedAt:Date.now(),view:'passengers',searchParams:{originCode:'SGN',destinationCode:'SIN',departureDate:'2099-11-01',returnDate:null,passengers:{adults:1,children:0,infants:0},cabinClass:'ECONOMY'},selectedFlight:adaptFlight(flight),selectedReturnFlight:null,passengerData:null,passengerDraft:form,selectedSeats:[],selectedReturnSeats:[],bookingData:null,holdExpiresAt:null}
 sessionStorage.setItem('skyway_session',JSON.stringify(session));api.getMyPassengers=async()=>[]
 await render(React.createElement(BookingApp))
 assert.ok([...container.querySelectorAll('input')].some(input=>input.value==='PRIVATE ALICE'))
 global.fetch=async()=>({ok:true,status:200,json:async()=>({data:{accessToken:jwt(),user:{id:2,fullName:'BOB',email:'bob@example.test'}}})})
 await act(async()=>{await api.login({});await flush()})
 assert.equal(getCurrentUser().id,2)
 assert.ok(![...container.querySelectorAll('input')].some(input=>input.value.includes('PRIVATE ALICE')))
 assert.ok(!sessionStorage.getItem('skyway_session')?.includes('PRIVATE ALICE'))
})
test('unknown create outcome blocks confirmation after payment component remount',async()=>{
 const props={flight:{id:1},seats:[{id:3}],holdExpiresAt:'2099-01-01',passengerData:{paxList:[{type:'Adult'}],passengerRequests:[{fullName:'Tester'}],contactInfo:{name:'Tester',email:'t@example.test',phone:'12345678'}},onHome:()=>{},onBack:()=>{}}
 let calls=0;api.post=async path=>{assert.equal(path,'/bookings');calls++;throw Error('response lost')}
 await render(React.createElement(VietQRPayment,props));await act(async()=>{button('Confirm booking & continue to payment').click();await flush()});assert.equal(calls,1);assert.match(container.textContent,/may have succeeded/)
 await render(React.createElement(VietQRPayment,{...props,key:'remount'}));assert.equal(button('Confirm booking & continue to payment'),undefined);assert.equal(calls,1)
})
test('held or booked right-hand seats cannot become selectable through historical clicks',async()=>{
 api.get=async path=>path.includes('/seats')?{seats:[{id:2,seatNo:'2D',status:'HELD',cabinClass:'ECONOMY',price:1000000},{id:3,seatNo:'3D',status:'BOOKED',cabinClass:'ECONOMY',price:1000000}]}:page([])
 await render(React.createElement(SeatSelection,{flight:adaptFlight(flight),passengerData:{paxList:[{type:'Adult',title:'Passenger 1'}],passengers:[{name:'Tester'}]},historicallySelectedSeats:[2,3],onHome:()=>{},onBack:()=>{},onContinue:()=>{}}))
 for(const number of ['2D','3D']){const seat=[...container.querySelectorAll('button')].find(b=>b.textContent===number);assert.ok(seat.disabled)}
})
test('expired hold disables explicit booking confirmation and offers return to seats',async()=>{let posts=0;api.post=async()=>posts++;await render(React.createElement(VietQRPayment,{flight:{id:1},seats:[{id:3}],holdExpiresAt:'2000-01-01',passengerData:{paxList:[{type:'Adult'}],passengerRequests:[],contactInfo:{}},onHome:()=>{},onBack:()=>{}}));assert.ok(button('Confirm booking & continue to payment').disabled);assert.ok(button('Back to Seats'));assert.equal(posts,0)})
test('ticket dialog supports Escape and restores keyboard focus',async()=>{let closed=0;const trigger=document.createElement('button');document.body.append(trigger);trigger.focus();await act(async()=>root.render(React.createElement(BoardingPassModal,{isOpen:true,onClose:()=>closed++,booking:{...booking,status:'COMPLETED'}})));assert.ok(document.querySelector('[role="dialog"]').contains(document.activeElement));await act(async()=>document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true})));assert.equal(closed,1);await act(async()=>root.render(null));assert.equal(document.activeElement,trigger);trigger.remove()})
test('explicit round-trip booking maps seated passengers and outbound extras without infant seats',async()=>{
 const requests=[{fullName:'Adult',passengerType:'ADULT'},{fullName:'Child',passengerType:'CHILD'},{fullName:'Infant',passengerType:'INFANT',guardianPassengerIndex:0}]
 let payload,posts=0,created
 api.post=async(path,body)=>{assert.equal(path,'/bookings');posts++;payload=body;return booking}
 await render(React.createElement(VietQRPayment,{flight:{id:1},returnFlight:{id:2},seats:[{id:11},{id:12}],returnSeats:[{id:21},{id:22}],holdExpiresAt:'2099-01-01',passengerData:{paxList:[{type:'Adult'},{type:'Child'},{type:'Infant'}],passengerRequests:requests,contactInfo:{name:'Adult',email:'test@example.test',phone:'12345678'},passengerAddOns:{1:{baggage:400000,meal:true,insurance:false}}},onBookingCreated:value=>created=value,onHome:()=>{}}))
 await act(async()=>{button('Confirm booking & continue to payment').click();await flush()})
 assert.equal(posts,1);assert.equal(created.id,7);assert.equal(payload.tripType,'ROUND_TRIP');assert.deepEqual(payload.flightIds,[1,2]);assert.deepEqual(payload.passengers,requests);assert.deepEqual(payload.seatSelections.map(s=>[s.flightSeatId,s.passengerIndex]),[[11,0],[12,1],[21,0],[22,1]]);assert.deepEqual(payload.seatSelections[1].addOns,[{serviceType:'BAGGAGE',quantity:2},{serviceType:'MEAL',quantity:1}]);assert.deepEqual(payload.seatSelections[3].addOns,[])
})
test('history shows both legs and one passenger for a round trip, and exposes payment resume',async()=>{
 const {MyBookingsModal}=require('../components/booking/MyBookingsModal.tsx');let resumed
 const roundtrip={...booking,tripType:'ROUND_TRIP',tickets:[{id:1,passengerId:1,flight},{id:2,passengerId:1,flight:{...flight,id:2,flightNo:'RETURN-VN',origin:flight.dest,dest:flight.origin}}]}
 api.get=async()=>page([roundtrip])
 await render(React.createElement(MyBookingsModal,{isOpen:true,onClose:()=>{},onPayBooking:b=>resumed=b}))
 assert.match(container.textContent,/DEMO-VN/);assert.match(container.textContent,/RETURN-VN/);assert.doesNotMatch(container.textContent,/2 Passengers/)
 await act(async()=>button('Continue payment').click());assert.equal(resumed.id,7)
})
for (const clear of [true,false]) test(`directory ${clear?'blocks unsupported document removal':'refetches canonical document after update'}`,async()=>{
 const {PassengerDirectoryModal}=require('../components/passenger/PassengerDirectoryModal.tsx')
 const oldGet=api.getMyPassengers,oldUpdate=api.updatePassenger
 const passenger={id:22,fullName:'Directory Tester',dateOfBirth:'1990-01-01',gender:'MALE',nationality:'Vietnam',identityType:'CITIZEN_ID',identityNumber:'OLD123'}
 let calls=0,reads=0
 api.getMyPassengers=async()=>{reads++;return [{...passenger,identityNumber:calls?'SERVER-CANONICAL':'OLD123'}]}
 api.updatePassenger=async()=>{calls++;return {...passenger,identityNumber:'PARTIAL-RESPONSE'}}
 try {
  await render(React.createElement(PassengerDirectoryModal,{isOpen:true,onClose:()=>{}}))
  await act(async()=>container.querySelector('button[title="Edit"]').click())
  const input=container.querySelector('input[placeholder="e.g. 079195001234"]')
  await act(async()=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,clear?'':'NEW123');input.dispatchEvent(new Event('input',{bubbles:true}))})
  await act(async()=>{container.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await flush()})
  if(clear){assert.equal(calls,0);assert.match(container.textContent,/Removing a document is not supported/)}
  else{assert.equal(calls,1);assert.equal(reads,2);assert.match(container.textContent,/SERVER-CANONICAL/);assert.doesNotMatch(container.textContent,/PARTIAL-RESPONSE/)}
 } finally {api.getMyPassengers=oldGet;api.updatePassenger=oldUpdate}
})
for (const guardianIndex of [0,1]) test(`passenger guardian dropdown stores numeric adult index ${guardianIndex} before validation and request mapping`,async()=>{
 const {PassengerDetails}=require('../components/passenger/PassengerDetails.tsx')
 const oldGet=api.getMyPassengers;api.getMyPassengers=async()=>[]
 const form=(name,dob,id)=>({title:'Mr',name,dob,idType:id?'CITIZEN_ID':'',id,idExpiry:'',nationality:'Vietnam',guardianIndex:null})
 let continued
 const passengerData={contactInfo:{name:'Contact',email:'test@example.test',phone:'0123456789'},passengers:[form('Adult A','1990-01-01','A'),form('Adult B','1991-01-01','B'),form('Infant','2099-01-01','')],passengerAddOns:{}}
 try {
  await render(React.createElement(PassengerDetails,{flight:adaptFlight(flight),searchParams:{passengers:{adults:2,children:0,infants:1}},passengerData,onHome:()=>{},onBack:()=>{},onContinue:data=>continued=data}))
  await act(async()=>{const select=container.querySelector('#pax_2_guardian');select.value=String(guardianIndex);select.dispatchEvent(new Event('change',{bubbles:true}))})
  await act(async()=>button('Continue to Seats').click())
  assert.ok(continued,'valid guardian selection must allow continuation')
  assert.equal(continued.passengers[2].guardianIndex,guardianIndex)
  assert.equal(continued.passengerRequests[2].guardianPassengerIndex,guardianIndex)
 } finally {api.getMyPassengers=oldGet}
})
test('passenger controller preserves quickfill, contact copy, add-ons and autosaved draft after remount',async()=>{
 const {PassengerDetails}=require('../components/passenger/PassengerDetails.tsx')
 const oldGet=api.getMyPassengers
 api.getMyPassengers=async()=>[{id:8,fullName:'SAVED ADULT',dateOfBirth:'1990-01-01',gender:'FEMALE',nationality:'Vietnam',identityType:'PASSPORT',identityNumber:'TEST-DOC'}]
 let draft,continued
 const props={flight:adaptFlight(flight),searchParams:{passengers:{adults:1,children:0,infants:0}},passengerData:{contactInfo:{name:'Contact',email:'contact@example.test',phone:'0123456789'},passengers:[{title:'Mr',name:'',dob:'',idType:'CITIZEN_ID',id:'',idExpiry:'',nationality:'Vietnam'}],passengerAddOns:{}},onHome:()=>{},onBack:()=>{},onDraftChange:value=>draft=value,onContinue:value=>continued=value}
 try {
  await render(React.createElement(PassengerDetails,props))
  await act(async()=>button('SAVED ADULT').click())
  assert.equal(container.querySelector('#pax_0_name').value,'SAVED ADULT')
  assert.equal(container.querySelector('#pax_0_id').value,'TEST-DOC')
  await act(async()=>{container.querySelector('input[type="checkbox"]').click()})
  await act(async()=>{const input=container.querySelector('#contact_name');Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,'UPDATED CONTACT');input.dispatchEvent(new Event('input',{bubbles:true}))})
  assert.equal(container.querySelector('#pax_0_name').value,'UPDATED CONTACT')
  await act(async()=>[...container.querySelectorAll('label')].find(l=>l.textContent.includes('In-Flight Hot Meal')).querySelector('input').click())
  await act(async()=>[...container.querySelectorAll('label')].find(l=>l.textContent.includes('+20kg Checked Bag')).querySelector('input').click())
  assert.deepEqual(draft.passengerAddOns[0],{baggage:200000,meal:true,insurance:false})
  const saved=structuredClone(draft)
  await render(React.createElement(PassengerDetails,{...props,key:'restored-form',passengerData:saved}))
  assert.equal(container.querySelector('#contact_name').value,'UPDATED CONTACT')
  assert.equal(container.querySelector('#pax_0_name').value,'UPDATED CONTACT')
  await act(async()=>button('Continue to Seats').click())
  assert.equal(continued.passengerRequests[0].identityNumber,'TEST-DOC')
  assert.equal(continued.passengerRequests[0].gender,'FEMALE')
  assert.equal(continued.pricing.addOnsTotal,280000)
  assert.equal(continued.pricing.totalTaxes,478000)
 } finally {api.getMyPassengers=oldGet}
})

test('demo checkout encodes a non-payable sample and removes payment controls on success',async()=>{
 global.fetch=async()=>{const r=authResponse(),d=await r.json();d.data.user.demoAlias='A';return {...r,json:async()=>d}}
 let done=false,encoded,posts=0
 qr.toDataURL=async payload=>{encoded=payload;return 'data:image/png;base64,TEST'}
 api.get=async()=>({paymentStatus:done?'SUCCESS':'PENDING',payment:{id:9,provider:'DEMO',status:done?'SUCCESS':'PENDING',refundStatus:'NONE',qrCode:'MUST-NOT-ENCODE'}})
 api.getBookingById=async()=>({...booking,status:done?'COMPLETED':'PENDING'})
 api.post=async path=>{assert.equal(path,'/payments/9/demo-confirm');posts++;done=true;return {}}
 await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>{}}))
 assert.match(container.textContent,/no money is transferred/);assert.equal(encoded,'SKYWAY-DEMO-NOT-PAYABLE:7:9')
 await act(async()=>{button('Confirm Demo Payment').click();await flush()})
 assert.equal(posts,1);assert.match(container.textContent,/Payment confirmed/)
 assert.equal(button('Confirm Demo Payment'),undefined)
 assert.equal(button('Check / Retry'),undefined);assert.doesNotMatch(container.textContent,/Payment deadline:|Estimated time remaining:/)
 assert.equal(container.querySelector('img[alt="Demo QR — not payable"]'),null)
})

test('single demo entry uses account A and hides after sign-in',async()=>{
 const {DemoGuide}=require('../components/DemoGuide.tsx');let logged=false
 global.fetch=async url=>{
  if(String(url).includes('/demo/login/A')) logged=true
  if(!logged)return {ok:false,status:401,json:async()=>({errorCode:'REFRESH_TOKEN_INVALID'})}
  const r=authResponse(),d=await r.json();d.data.user.demoAlias='A';return {...r,json:async()=>d}
 }
 api.get=async()=>({enabled:true,accountsEnabled:true,flights:[]})
 await render(React.createElement(DemoGuide))
 assert.ok(button('Try demo'));assert.equal(button('Try Demo B'),undefined)
 await act(async()=>{button('Try demo').click();await flush()})
 assert.equal(logged,true);assert.equal(button('Try demo'),undefined)
})
test('disabled demo does not expose a sign-in action',async()=>{
 const {DemoGuide}=require('../components/DemoGuide.tsx')
 global.fetch=async()=>({ok:false,status:401,json:async()=>({})})
 api.get=async()=>({enabled:false,accountsEnabled:false})
 await render(React.createElement(DemoGuide));assert.equal(button('Try demo'),undefined)
})

test('sample passenger fill is available only to a demo account and produces a valid draft',async()=>{
 const {PassengerDetails}=require('../components/passenger/PassengerDetails.tsx');let result
 const getPassengers=api.getMyPassengers;api.getMyPassengers=async()=>[]
 const props={flight:adaptFlight(flight),searchParams:{originCode:'SGN',destinationCode:'SIN',departureDate:'2099-11-01',returnDate:null,passengers:{adults:1,children:0,infants:0},cabinClass:'ECONOMY'},onBack:()=>{},onHome:()=>{},onContinue:value=>result=value}
 try {
  await render(React.createElement(PassengerDetails,props));assert.equal(button('Fill sample details'),undefined)
  global.fetch=async()=>{const r=authResponse(),d=await r.json();d.data.user.demoAlias='A';return {...r,json:async()=>d}}
  const {refreshAccessToken}=require('../lib/api.ts');await act(async()=>{await refreshAccessToken();await flush()})
  await act(async()=>{button('Fill sample details').click();await flush()})
  assert.equal(container.querySelector('#pax_0_name').value,'Demo Adult 1')
  const nameInput=container.querySelector('#pax_0_name'),contactInput=container.querySelector('#contact_email')
  const setValue=(input,value)=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}))}
  await act(async()=>{setValue(nameInput,'User Entered Name');setValue(contactInput,'keep@example.test')})
  await act(async()=>{button('Fill sample details').click();await flush()})
  assert.equal(nameInput.value,'USER ENTERED NAME');assert.equal(contactInput.value,'keep@example.test')

  await act(async()=>{button('Continue to Seats').click();await flush()})
  assert.ok(result.passengerRequests[0].identityNumber.startsWith('DEMO'))
 } finally {api.getMyPassengers=getPassengers}
})

test('manual one-way selection works without auto-assign and ignores extra restored slots',async()=>{
 let held,continued=false;const s={id:11,seatNo:'1A',status:'AVAILABLE',cabinClass:'ECONOMY',price:1000000}
 api.get=async()=>({seats:[s,{...s,id:12,seatNo:'1B'}]});api.post=async(path,body)=>{held=body.seatIds;return {expiresAt:'2099-01-01'}}
 await render(React.createElement(SeatSelection,{flight:adaptFlight(flight),passengerData:{paxList:[{type:'Adult'}],passengers:[{name:'Tester'}]},initialSeats:[null,null],onHome:()=>{},onBack:()=>{},onContinue:()=>continued=true}))
 await act(async()=>[...container.querySelectorAll('button')].find(b=>b.textContent==='1A').click())
 assert.equal(button('Continue to Payment').disabled,false)
 await act(async()=>{button('Continue to Payment').click();await flush()})
 assert.deepEqual(held,[11]);assert.equal(continued,true)
})
test('manual round-trip selection guides return seats before one atomic hold',async()=>{
 let held;api.get=async path=>{const ret=path.includes('/2/');return {seats:[{id:ret?21:11,seatNo:ret?'2A':'1A',status:'AVAILABLE',cabinClass:'ECONOMY',price:1000000},{id:ret?22:12,seatNo:ret?'2B':'1B',status:'AVAILABLE',cabinClass:'ECONOMY',price:1000000}]}}
 api.post=async(path,body)=>{held=body.seatIds;return {expiresAt:'2099-01-01'}}
 await render(React.createElement(SeatSelection,{flight:adaptFlight(flight),returnFlight:adaptFlight({...flight,id:2}),passengerData:{paxList:[{type:'Adult'}],passengers:[{name:'Tester'}]},onHome:()=>{},onBack:()=>{},onContinue:()=>{}}))
 await act(async()=>[...container.querySelectorAll('button')].find(b=>b.textContent==='1A').click())
 assert.equal(button('Choose return seats').disabled,false)
 await act(async()=>button('Choose return seats').click());assert.equal(held,undefined)
 await act(async()=>[...container.querySelectorAll('button')].find(b=>b.textContent==='2A').click())
 await act(async()=>{button('Continue to Payment').click();await flush()})
 assert.deepEqual(held,[11,21])
})

test('ordinary users cannot simulate a legacy DEMO payment and are told how to recover',async()=>{
 api.get=async()=>({paymentStatus:'PENDING',payment:{id:9,provider:'DEMO',status:'PENDING',refundStatus:'NONE'}})
 await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>{}}))
 assert.equal(button('Confirm Demo Payment'),undefined)
 assert.match(container.textContent,/earlier simulated payment/)
 assert.equal(container.querySelector('img[alt="Demo QR — not payable"]'),null)
})

test('background status polling preserves gateway initialization error without spinner or provider retry',async()=>{
 let posts=0
 api.get=async()=>({paymentStatus:'PENDING',payment:{id:9,provider:'PAYOS',status:'PENDING',refundStatus:'NONE'}})
 api.post=async()=>{posts++;throw new Error('Payment gateway communication error')}
 await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>{}}))
 assert.match(container.textContent,/Payment gateway communication error/)
 await act(async()=>{await new Promise(resolve=>setTimeout(resolve,3200))})
 assert.equal(posts,1);assert.match(container.textContent,/Payment gateway communication error/)
 assert.match(container.textContent,/No QR or checkout link/)
 assert.doesNotMatch(container.textContent,/Awaiting payment confirmation/)
 assert.equal(container.querySelector('[aria-label="Checking payment"]'),null)
 await act(async()=>{button('Retry').click();await flush()});assert.equal(posts,2)
})

test('review associates seats with each leg and skips infants',async()=>{
 const {TripSummary}=require('../components/payment/TripSummary.tsx')
 await act(async()=>root.render(React.createElement(TripSummary,{flight:adaptFlight(flight),returnFlight:adaptFlight({...flight,id:2,origin:flight.dest,dest:flight.origin}),seats:[{seatNo:'7A',cabinClass:'ECONOMY'}],returnSeats:[{seatNo:'8B',cabinClass:'BUSINESS'}],passengerData:{paxList:[{type:'Adult'},{type:'Infant'}],passengerRequests:[{fullName:'Sample Adult'},{fullName:'Sample Infant'}]}})))
 const legs=container.querySelectorAll('section');assert.equal(legs.length,2)
 assert.match(legs[0].textContent,/7A · ECONOMY/);assert.doesNotMatch(legs[0].textContent,/8B/)
 assert.match(legs[1].textContent,/8B · BUSINESS/);assert.doesNotMatch(legs[1].textContent,/7A/)
 for(const leg of legs){assert.match(leg.textContent,/Sample Adult/);assert.match(leg.textContent,/Sample Infant/);assert.match(leg.textContent,/No separate seat/);assert.match(leg.textContent,/Departure/);assert.match(leg.textContent,/Arrival/)}
})
test('ready QR hides retry and displays provider metadata; old QR does not recreate payment',async()=>{
 let posts=0;api.post=async()=>{posts++;throw Error('unexpected create')}
 api.getBookingById=async()=>booking
 api.get=async()=>({paymentStatus:'PENDING',payment:{id:9,provider:'PAYOS',status:'PENDING',refundStatus:'NONE',qrCode:'TEST-QR',bankBin:'970000',accountNumber:'123456',accountName:'TEST RECIPIENT',transferDescription:'EXACT PROVIDER TEXT',transferAmount:1500000}})
 await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>{}}))
 assert.equal(posts,0);assert.equal(button('Retry'),undefined)
 assert.match(container.textContent,/TEST RECIPIENT/);assert.match(container.textContent,/EXACT PROVIDER TEXT/)
 assert.ok(container.querySelector('details summary'));assert.equal(button('Pay later'),undefined)
 api.get=async()=>({paymentStatus:'PENDING',payment:{id:9,provider:'PAYOS',status:'PENDING',refundStatus:'NONE',qrCode:'OLD-QR'}})
 await render(React.createElement(VietQRPayment,{key:'legacy',bookingId:7,onHome:()=>{}}))
 assert.equal(posts,0);assert.match(container.textContent,/Some bank details are unavailable/)
})

test('payment URL restores booking without draft and Home does not reopen it',async()=>{
 const BookingApp=require('../app/page.tsx').default
 let posts=0,reads=0
 qr.toDataURL=async()=> 'data:image/png;base64,EXISTING'
 api.post=async()=>{posts++;throw Error('must not create')}
 api.getBookingById=async id=>{reads++;assert.equal(id,7);return booking}
 api.get=async()=>({paymentStatus:'PENDING',payment:{id:9,provider:'PAYOS',status:'PENDING',refundStatus:'NONE',qrCode:'EXISTING-QR'}})
 window.history.replaceState({},'', '/?step=payment&bookingId=7')
 await render(React.createElement(BookingApp))
 assert.ok(container.querySelector('img[alt="Payment QR supplied by payOS"]'));assert.equal(posts,0);assert.equal(reads,1)
 sessionStorage.setItem(require('../lib/private-session.ts').BOOKING_ATTEMPT_KEY,JSON.stringify({ownerId:1,bookingId:7}))
 window.history.replaceState({},'', '/')
 await render(React.createElement(BookingApp,{key:'new-home'}))
 assert.ok(button('Search flights'));assert.equal(container.querySelector('img[alt="Payment QR supplied by payOS"]'),null)
 assert.equal(posts,0);assert.equal(reads,1)
})
test('pending history shows deadline and stops offering payment when it has passed',async()=>{
 const {MyBookingsModal}=require('../components/booking/MyBookingsModal.tsx')
 api.get=async()=>page([booking])
 await render(React.createElement(MyBookingsModal,{isOpen:true,onClose:()=>{},onPayBooking:()=>{}}))
 assert.match(container.textContent,/Awaiting payment/);assert.match(container.textContent,/Payment deadline:/)
 assert.ok(button('Continue payment'))
 api.get=async()=>page([{...booking,paymentDeadline:'2000-01-01T00:00:00Z'}])
 await act(async()=>{[...container.querySelectorAll('button')].find(b=>b.title==='Refresh bookings').click();await flush()})
 assert.equal(button('Continue payment'),undefined);assert.match(container.textContent,/Verifying payment/)
})

test('guest payment URL requires login without reading private booking or saving a draft',async()=>{
 const BookingApp=require('../app/page.tsx').default
 global.fetch=async()=>({ok:false,status:401,json:async()=>({})})
 let reads=0
 api.get=async()=>{reads++;throw Error('must not read private payment')}
 api.getBookingById=async()=>{reads++;throw Error('must not read private booking')}
 window.history.replaceState({},'', '/?step=payment&bookingId=7')
 await render(React.createElement(BookingApp))
 assert.match(container.textContent,/Sign in to continue payment/);assert.ok(button('Sign In'))
 assert.equal(reads,0);assert.equal(sessionStorage.getItem('skyway_session'),null)
})

test('cancelled demo uses a result view with exit actions and no payment instructions',async()=>{
 let cancelled=false,home=0
 const originalCancel=api.cancelBooking,originalConfirm=window.confirm
 api.cancelBooking=async()=>{cancelled=true};window.confirm=()=>true
 api.get=async()=>({paymentStatus:cancelled?'CANCELLED':'PENDING',payment:{id:9,provider:'DEMO',status:cancelled?'CANCELLED':'PENDING',refundStatus:'NONE'}})
 api.getBookingById=async()=>({...booking,status:cancelled?'CANCELLED':'PENDING'})
 try{
  await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>home++}))
  await act(async()=>{button('Cancel Booking').click();await flush()})
  assert.ok(container.querySelector('[aria-label="Payment result"]'))
  assert.match(container.textContent,/Your booking has been cancelled/)
  assert.doesNotMatch(container.textContent,/Confirm Demo Payment|Confirm to simulate|Time remaining|Total amount|Complete payment/)
  assert.equal(container.querySelector('img'),null);assert.ok(button('My Bookings'))
  await act(async()=>button('Back to Home').click());assert.equal(home,1)
 } finally {api.cancelBooking=originalCancel;window.confirm=originalConfirm}
})
test('failed cancellation keeps the user on payment and does not claim cancellation',async()=>{
 const originalCancel=api.cancelBooking,originalConfirm=window.confirm;let home=0
 api.cancelBooking=async()=>{throw Error('Cancellation could not be confirmed')};window.confirm=()=>true
 api.get=async()=>({paymentStatus:'PENDING',payment:{id:9,provider:'PAYOS',status:'PENDING',refundStatus:'NONE',qrCode:'EXISTING'}})
 try{
  await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>home++}))
  await act(async()=>{button('Cancel Booking').click();await flush()})
  assert.equal(home,0);assert.equal(container.querySelector('[aria-label="Payment result"]'),null)
  assert.match(container.textContent,/Cancellation could not be confirmed/)
  assert.equal(button('Back to Home'),undefined)
 }finally{api.cancelBooking=originalCancel;window.confirm=originalConfirm}
})

test('transfer panel copies exact provider values and reports clipboard failure',async()=>{
 const {TransferDetails}=require('../components/payment/TransferDetails.tsx')
 const saved=Object.getOwnPropertyDescriptor(navigator,'clipboard');const copied=[]
 Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async value=>copied.push(value)}})
 try{
  await render(React.createElement(TransferDetails,{payment:{id:9,provider:'PAYOS',amount:2000000,transferAmount:1234567,bankBin:'000000',accountName:'TEST ONLY',accountNumber:'00001234',transferDescription:'EXACT-TEST + 123'}}))
  for(const label of ['account number','transfer description','transfer amount'])await act(async()=>{container.querySelector(`[aria-label="Copy ${label}"]`).click();await flush()})
  assert.deepEqual(copied,['00001234','EXACT-TEST + 123','1234567'])
  navigator.clipboard.writeText=async()=>{throw Error('denied')}
  await act(async()=>{container.querySelector('[aria-label="Copy account number"]').click();await flush()})
  assert.match(container.textContent,/Could not copy/)
 }finally{if(saved)Object.defineProperty(navigator,'clipboard',saved);else delete navigator.clipboard}
})
test('demo transfer panel shows amount without empty bank rows, copy controls or bank instructions',async()=>{
 const {TransferDetails}=require('../components/payment/TransferDetails.tsx')
 await render(React.createElement(TransferDetails,{payment:{id:9,provider:'DEMO',amount:1650000,bankBin:'MUST-NOT-SHOW',accountName:'MUST-NOT-SHOW',accountNumber:'MUST-NOT-SHOW',transferDescription:'MUST-NOT-SHOW'}}))
 assert.match(container.textContent,/1\.650\.000/)
 assert.doesNotMatch(container.textContent,/Account holder|Account number|Transfer description|Not applicable/)
 assert.equal(container.querySelectorAll('button').length,0)
 assert.doesNotMatch(container.textContent,/MUST-NOT-SHOW|Unavailable/)
 assert.match(container.textContent,/no money is transferred/)
})

test('transfer bank name uses the published BIN directory and preserves unknown BINs',async()=>{
 const {TransferDetails}=require('../components/payment/TransferDetails.tsx')
 const {bankName}=require('../lib/banks.ts')
 assert.equal(bankName('970425'),'ABBANK')
 assert.equal(bankName('000000'),'BIN 000000')
 assert.equal(bankName('constructor'),'BIN constructor')
 assert.equal(bankName(null),null)
 await render(React.createElement(TransferDetails,{payment:{id:9,provider:'PAYOS',amount:1234567,bankBin:'970425',accountName:'TEST ONLY',accountNumber:'00001234',transferDescription:'EXACT-TEST',transferAmount:1234567}}))
 assert.match(container.textContent,/ABBANK/)
 assert.doesNotMatch(container.textContent,/Bank BIN/)
 assert.deepEqual([...container.querySelectorAll('dt')].map(el=>el.textContent),['Bank','Account holder','Account number','Total amount','Transfer description'])
})

test('deadline view verifies payment before cleanup and stays expired after scheduler closure',async()=>{
 let finalized=false
 api.get=async()=>({paymentStatus:finalized?'EXPIRED':'PENDING',payment:{id:9,provider:'DEMO',status:finalized?'EXPIRED':'PENDING',refundStatus:'NONE'}})
 api.getBookingById=async()=>({...booking,status:finalized?'CANCELLED':'PENDING',cancellationReason:finalized?'PAYMENT_TIMEOUT':null,paymentDeadline:'2000-01-01'})
 await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>{}}))
 assert.match(container.textContent,/checking the final payment result/);assert.match(container.textContent,/Verifying payment/)
 assert.doesNotMatch(container.textContent,/PENDING|Confirm Demo Payment/)
 finalized=true
 await act(async()=>{await new Promise(resolve=>setTimeout(resolve,3200))})
 assert.match(container.textContent,/Your booking has expired/);assert.doesNotMatch(container.textContent,/Booking cancelled|CANCELLED|PENDING/)
})
test('history labels system expiry and user cancellation separately after deadline',async()=>{
 const {MyBookingsModal}=require('../components/booking/MyBookingsModal.tsx')
 api.get=async()=>page([{...booking,id:7,status:'CANCELLED',cancellationReason:'PAYMENT_TIMEOUT'},{...booking,id:8,status:'CANCELLED',cancellationReason:'USER_CANCELLED',paymentDeadline:'2000-01-01'}])
 await render(React.createElement(MyBookingsModal,{isOpen:true,onClose:()=>{},onPayBooking:()=>{}}))
 assert.match(container.textContent,/Booking expired · Seats released/);assert.match(container.textContent,/Payment was not completed in time/)
 assert.match(container.textContent,/Cancelled at your request/);assert.equal(button('Continue payment'),undefined)
})

test('late payment read from a previous booking cannot replace the current booking',async()=>{
 let releaseOld
 const encoded=[];qr.toDataURL=async payload=>{encoded.push(payload);return 'data:image/png;base64,NEW'}
 api.get=async path=>path.endsWith('/7')?new Promise(resolve=>{releaseOld=resolve}):{paymentStatus:'SUCCESS',payment:{id:12,provider:'PAYOS',status:'SUCCESS',refundStatus:'NONE'}}
 api.getBookingById=async id=>({...booking,id,pnr:id===7?'OLDPNR':'NEWPNR',status:id===7?'PENDING':'COMPLETED'})
 await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>{}}))
 assert.equal(typeof releaseOld,'function')
 await render(React.createElement(VietQRPayment,{bookingId:8,onHome:()=>{}}))
 assert.match(container.textContent,/NEWPNR/)
 await act(async()=>{releaseOld({paymentStatus:'PENDING',payment:{id:9,provider:'PAYOS',status:'PENDING',refundStatus:'NONE',qrCode:'STALE-QR'}});await flush()})
 assert.match(container.textContent,/NEWPNR/);assert.doesNotMatch(container.textContent,/OLDPNR/)
 assert.deepEqual(encoded,[])
})

test('late QR encoding cannot display a QR from another booking',async()=>{
 let releaseOldQr
 api.get=async path=>({paymentStatus:'PENDING',payment:{id:9,provider:'PAYOS',status:'PENDING',refundStatus:'NONE',qrCode:path.endsWith('/7')?'OLD-QR':'NEW-QR'}})
 api.getBookingById=async id=>({...booking,id,pnr:id===7?'OLDPNR':'NEWPNR'})
 qr.toDataURL=async payload=>payload==='OLD-QR'?new Promise(resolve=>{releaseOldQr=resolve}):'data:image/png;base64,NEW'
 await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>{}}))
 assert.equal(typeof releaseOldQr,'function')
 await render(React.createElement(VietQRPayment,{bookingId:8,onHome:()=>{}}))
 assert.equal(container.querySelector('img[alt="Payment QR supplied by payOS"]').getAttribute('src'),'data:image/png;base64,NEW')
 await act(async()=>{releaseOldQr('data:image/png;base64,OLD');await flush()})
 assert.equal(container.querySelector('img[alt="Payment QR supplied by payOS"]').getAttribute('src'),'data:image/png;base64,NEW')
})

test('late background payment read cannot reopen a booking after a newer cancellation read',async()=>{
 const previousCancel=api.cancelBooking,previousConfirm=window.confirm
 let cancelled=false,bookingReads=0,releaseOld
 api.get=async()=>({paymentStatus:cancelled?'CANCELLED':'PENDING',payment:{id:9,provider:'PAYOS',status:cancelled?'CANCELLED':'PENDING',refundStatus:'NONE',qrCode:'EXISTING-QR'}})
 api.getBookingById=async()=>++bookingReads===2?new Promise(resolve=>{releaseOld=resolve}):({...booking,status:cancelled?'CANCELLED':'PENDING'})
 api.cancelBooking=async()=>{cancelled=true};window.confirm=()=>true
 qr.toDataURL=async()=> 'data:image/png;base64,EXISTING'
 try{
  await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>{}}))
  await act(async()=>{await new Promise(resolve=>setTimeout(resolve,3200))})
  assert.equal(typeof releaseOld,'function')
  await act(async()=>{button('Cancel Booking').click();await flush()})
  assert.match(container.textContent,/Your booking has been cancelled/)
  await act(async()=>{releaseOld({...booking,status:'PENDING'});await flush()})
  assert.match(container.textContent,/Your booking has been cancelled/)
  assert.equal(container.querySelector('img[alt="Payment QR supplied by payOS"]'),null)
  assert.equal(bookingReads,3)
 }finally{api.cancelBooking=previousCancel;window.confirm=previousConfirm}
})

test('directory reopened during a slow request ignores the closed dialog response',async()=>{
 const {PassengerDirectoryModal}=require('../components/passenger/PassengerDirectoryModal.tsx')
 const previous=api.getMyPassengers;let reads=0,releaseOld
 api.getMyPassengers=async()=>++reads===1?new Promise(resolve=>{releaseOld=resolve}):[{id:2,fullName:'CURRENT TRAVELER',dateOfBirth:'1990-01-01',gender:'MALE'}]
 try{
  const props={onClose:()=>{}}
  await render(React.createElement(PassengerDirectoryModal,{...props,isOpen:true}))
  await render(React.createElement(PassengerDirectoryModal,{...props,isOpen:false}))
  await render(React.createElement(PassengerDirectoryModal,{...props,isOpen:true}))
  assert.match(container.textContent,/CURRENT TRAVELER/)
  await act(async()=>{releaseOld([{id:1,fullName:'STALE TRAVELER',dateOfBirth:'1990-01-01',gender:'MALE'}]);await flush()})
  assert.match(container.textContent,/CURRENT TRAVELER/);assert.doesNotMatch(container.textContent,/STALE TRAVELER/)
  assert.equal(reads,2)
 }finally{api.getMyPassengers=previous}
})

test('authentication dialog clears credentials when closed or switched to registration',async()=>{
 const {useAuth}=require('../lib/auth-context.tsx')
 function OpenAuth(){const {openAuthModal}=useAuth();return React.createElement(React.Fragment,null,React.createElement('button',{onClick:()=>openAuthModal('login')},'Open login'),React.createElement('button',{onClick:()=>openAuthModal('register')},'Open register'))}
 await render(React.createElement(React.Fragment,null,React.createElement(OpenAuth),React.createElement(AuthModal)))
 await act(async()=>button('Open login').click())
 const password=container.querySelector('input[placeholder="Enter your password"]')
 await act(async()=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(password,'PRIVATE-PASSWORD');password.dispatchEvent(new Event('input',{bubbles:true}))})
 await act(async()=>container.querySelector('button[aria-label="Close"]').click())
 await act(async()=>button('Open login').click())
 assert.equal(container.querySelector('input[placeholder="Enter your password"]').value,'')
 await act(async()=>button('Open register').click())
 assert.match(container.textContent,/Create an account/)
 assert.equal(container.querySelector('input[placeholder="Min. 8 chars with uppercase, digit & symbol"]').value,'')
})
