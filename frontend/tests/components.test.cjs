const { test, beforeEach, afterEach } = require('node:test')
const assert = require('node:assert/strict')
const { JSDOM } = require('jsdom')
const dom = new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>', {url:'https://frontend.example/'})
for(const name of ['window','document','HTMLElement','HTMLInputElement','Element','Node','MutationObserver','DocumentFragment','CustomEvent','Event','MouseEvent','KeyboardEvent','sessionStorage','localStorage'])global[name]=dom.window[name]
global.getComputedStyle=dom.window.getComputedStyle.bind(dom.window)
global.requestAnimationFrame=fn=>setTimeout(fn,0)
global.cancelAnimationFrame=clearTimeout
window.scrollTo=()=>{}
global.IS_REACT_ACT_ENVIRONMENT=true
const React=require('react'),{act}=React,{createRoot}=require('react-dom/client')
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
async function render(node){await act(async()=>{root.render(React.createElement(AuthProvider,null,node));await flush()})}
const button=text=>[...container.querySelectorAll('button')].find(b=>b.textContent.includes(text))
beforeEach(()=>{sessionStorage.clear();localStorage.clear();invalidateAuth(false);global.fetch=async()=>authResponse();api.get=async()=>page([]);api.post=async()=>({});api.getBookingById=async()=>booking;container=document.getElementById('root');root=createRoot(container)})
afterEach(async()=>{await act(async()=>root.unmount());Object.assign(api,{get:original.get,post:original.post,getBookingById:original.getBookingById});qr.toDataURL=original.qr})
test('payment mount/reload reads completed booking, never creates booking/link or synthetic tickets',async()=>{let posts=0;api.post=async()=>posts++;api.get=async()=>({bookingStatus:'COMPLETED',paymentStatus:'SUCCESS',payment:{status:'SUCCESS',refundStatus:'NONE'}});api.getBookingById=async()=>({...booking,status:'COMPLETED'});await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>{}}));assert.match(container.textContent,/Payment confirmed/);assert.equal(posts,0);await act(async()=>button('View E-Tickets').click());assert.match(container.textContent,/No ticket details/);assert.doesNotMatch(container.textContent,/Gate 06|VN 123/)})
test('pending QR encodes exact backend payload locally; terminal update removes QR',async()=>{let state='PENDING',encoded;qr.toDataURL=async payload=>{encoded=payload;return 'data:image/png;base64,TEST'};api.get=async()=>({bookingStatus:'PENDING',paymentStatus:state,payment:{status:state,refundStatus:'NONE',qrCode:'BACKEND-EMV-PAYLOAD',paymentUrl:'https://pay.payos.vn/web/7'}});await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>{}}));assert.equal(encoded,'BACKEND-EMV-PAYLOAD');assert.ok(container.querySelector('img[alt="Payment QR supplied by payOS"]'));state='CANCELLED';await act(async()=>{button('Check / Retry').click();await flush()});assert.equal(container.querySelector('img[alt="Payment QR supplied by payOS"]'),null);assert.match(container.textContent,/Do not transfer money/)})
test('expired countdown never posts cancel; explicit booking confirmation does not run at mount',async()=>{let posts=0;api.post=async()=>posts++;api.get=async()=>({paymentStatus:'EXPIRED',payment:null});api.getBookingById=async()=>({...booking,paymentDeadline:'2000-01-01'});await render(React.createElement(VietQRPayment,{bookingId:7,onHome:()=>{}}));assert.equal(posts,0);assert.match(container.textContent,/deadline/);await render(React.createElement(VietQRPayment,{flight:{id:1},passengerData:{paxList:[],passengerRequests:[],contactInfo:{}},holdExpiresAt:'2099-01-01',onHome:()=>{}}));assert.equal(posts,0);assert.ok(button('Confirm Booking'))})
test('seat auto-assign excludes business and historical unavailable seats',async()=>{const seat=(id,status,cabinClass,price)=>({id,seatNo:`${id}A`,status,cabinClass,price});api.get=async path=>path.includes('/seats')?{seats:[seat(1,'AVAILABLE','BUSINESS',2500000),seat(2,'HELD','ECONOMY',1000000),seat(3,'AVAILABLE','ECONOMY',1000000)]}:page([]);let held;api.post=async(path,body)=>{held=body.seatIds;return {expiresAt:'2099-01-01'}};await render(React.createElement(SeatSelection,{flight:adaptFlight(flight),passengerData:{paxList:[{type:'Adult',title:'Passenger 1'}],passengers:[{name:'Tester'}]},cabinClass:'ECONOMY',historicallySelectedSeats:[2],onHome:()=>{},onBack:()=>{},onContinue:()=>{}}));await act(async()=>button('Auto-assign').click());await act(async()=>{button('Continue to Payment').click();await flush()});assert.deepEqual(held,[3])})
test('guest login callback continues seat hold without reopening the login modal',async()=>{global.fetch=async url=>url.endsWith('/auth/login')?authResponse():({ok:false,status:401,json:async()=>({})});const seat={id:3,seatNo:'3A',status:'AVAILABLE',cabinClass:'ECONOMY',price:1000000};api.get=async path=>path.includes('/seats')?{seats:[seat]}:page([]);let holds=0;api.post=async()=>{holds++;return {expiresAt:'2099-01-01'}};await render(React.createElement(React.Fragment,null,React.createElement(SeatSelection,{flight:adaptFlight(flight),passengerData:{paxList:[{type:'Adult',title:'Passenger 1'}],passengers:[{name:'Tester'}]},onHome:()=>{},onBack:()=>{},onContinue:()=>{}}),React.createElement(AuthModal)));await act(async()=>button('Auto-assign').click());await act(async()=>button('Continue to Payment').click());const setValue=(input,value)=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}))};await act(async()=>{setValue(container.querySelector('input[type="email"]'),'test@example.test');setValue(container.querySelector('input[type="password"]'),'Password123@')});await act(async()=>{container.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await flush()});assert.equal(holds,1);assert.equal(container.querySelector('[role="dialog"]'),null)})
test('late flight response is ignored after selecting another date',async()=>{let resolveOld;let queries=0;api.get=async path=>{if(path.startsWith('/airlines'))return page([]);queries++;if(queries===1)return new Promise(r=>resolveOld=r);return page([{...flight,id:2,flightNo:'NEW-DATE'}])};await render(React.createElement(FlightResults,{searchParams:{originCode:'SGN',destinationCode:'SIN',departureDate:'2099-11-01',returnDate:null,passengers:{adults:1,children:0,infants:0},cabinClass:'ECONOMY'},onModify:()=>{},onSelectFlight:()=>{}}));const dateButtons=[...container.querySelectorAll('button')].filter(b=>b.textContent.includes('NOV'));await act(async()=>{dateButtons[dateButtons.length-1].click();await flush()});await act(async()=>{resolveOld(page([{...flight,flightNo:'STALE-DATE'}]));await flush()});assert.match(container.textContent,/NEW-DATE/);assert.doesNotMatch(container.textContent,/STALE-DATE/);const slider=container.querySelector('input[type="range"]');assert.ok(Number(slider.max)>=4500000);assert.match(container.textContent,/DEMO · Simulated/);assert.match(container.textContent,/No limit/)})
test('ticket uses airport timezone and contains no fake operational boarding information',async()=>{await act(async()=>root.render(React.createElement(BoardingPassModal,{isOpen:true,onClose:()=>{},booking:{...booking,status:'COMPLETED',tickets:[{id:1,ticketNo:'REAL-123',flight,passengerName:'Tester',passengerType:'ADULT',status:'ISSUED',seat:{seatNo:'3A',cabinClass:'ECONOMY'},addOns:[]}]}})));assert.match(container.textContent,/12:05/);assert.match(container.textContent,/Demo E-Tickets/);assert.match(container.textContent,/not an airline boarding pass/);assert.doesNotMatch(container.textContent,/Gate 06|Terminal 1|Official Boarding/);assert.match(container.textContent,/REAL-123/)})
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
 await render(React.createElement(VietQRPayment,props));await act(async()=>{button('Confirm Booking').click();await flush()});assert.equal(calls,1);assert.match(container.textContent,/may have succeeded/)
 await render(React.createElement(VietQRPayment,{...props,key:'remount'}));assert.equal(button('Confirm Booking'),undefined);assert.equal(calls,1)
})
test('held or booked right-hand seats cannot become selectable through historical clicks',async()=>{
 api.get=async path=>path.includes('/seats')?{seats:[{id:2,seatNo:'2D',status:'HELD',cabinClass:'ECONOMY',price:1000000},{id:3,seatNo:'3D',status:'BOOKED',cabinClass:'ECONOMY',price:1000000}]}:page([])
 await render(React.createElement(SeatSelection,{flight:adaptFlight(flight),passengerData:{paxList:[{type:'Adult',title:'Passenger 1'}],passengers:[{name:'Tester'}]},historicallySelectedSeats:[2,3],onHome:()=>{},onBack:()=>{},onContinue:()=>{}}))
 for(const number of ['2D','3D']){const seat=[...container.querySelectorAll('button')].find(b=>b.textContent===number);assert.ok(seat.disabled)}
})
test('expired hold disables explicit booking confirmation and offers return to seats',async()=>{let posts=0;api.post=async()=>posts++;await render(React.createElement(VietQRPayment,{flight:{id:1},seats:[{id:3}],holdExpiresAt:'2000-01-01',passengerData:{paxList:[{type:'Adult'}],passengerRequests:[],contactInfo:{}},onHome:()=>{},onBack:()=>{}}));assert.ok(button('Confirm Booking').disabled);assert.ok(button('Back to Seats'));assert.equal(posts,0)})
test('ticket dialog supports Escape and restores keyboard focus',async()=>{let closed=0;const trigger=document.createElement('button');document.body.append(trigger);trigger.focus();await act(async()=>root.render(React.createElement(BoardingPassModal,{isOpen:true,onClose:()=>closed++,booking:{...booking,status:'COMPLETED'}})));assert.ok(container.querySelector('[role="dialog"]').contains(document.activeElement));await act(async()=>document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true})));assert.equal(closed,1);await act(async()=>root.render(null));assert.equal(document.activeElement,trigger);trigger.remove()})
test('explicit round-trip booking maps seated passengers and outbound extras without infant seats',async()=>{
 const requests=[{fullName:'Adult',passengerType:'ADULT'},{fullName:'Child',passengerType:'CHILD'},{fullName:'Infant',passengerType:'INFANT',guardianPassengerIndex:0}]
 let payload,posts=0,created
 api.post=async(path,body)=>{assert.equal(path,'/bookings');posts++;payload=body;return booking}
 await render(React.createElement(VietQRPayment,{flight:{id:1},returnFlight:{id:2},seats:[{id:11},{id:12}],returnSeats:[{id:21},{id:22}],holdExpiresAt:'2099-01-01',passengerData:{paxList:[{type:'Adult'},{type:'Child'},{type:'Infant'}],passengerRequests:requests,contactInfo:{name:'Adult',email:'test@example.test',phone:'12345678'},passengerAddOns:{1:{baggage:400000,meal:true,insurance:false}}},onBookingCreated:value=>created=value,onHome:()=>{}}))
 await act(async()=>{button('Confirm Booking').click();await flush()})
 assert.equal(posts,1);assert.equal(created.id,7);assert.equal(payload.tripType,'ROUND_TRIP');assert.deepEqual(payload.flightIds,[1,2]);assert.deepEqual(payload.passengers,requests);assert.deepEqual(payload.seatSelections.map(s=>[s.flightSeatId,s.passengerIndex]),[[11,0],[12,1],[21,0],[22,1]]);assert.deepEqual(payload.seatSelections[1].addOns,[{serviceType:'BAGGAGE',quantity:2},{serviceType:'MEAL',quantity:1}]);assert.deepEqual(payload.seatSelections[3].addOns,[])
})
test('history shows both legs and one passenger for a round trip, and exposes payment resume',async()=>{
 const {MyBookingsModal}=require('../components/booking/MyBookingsModal.tsx');let resumed
 const roundtrip={...booking,tripType:'ROUND_TRIP',tickets:[{id:1,passengerId:1,flight},{id:2,passengerId:1,flight:{...flight,id:2,flightNo:'RETURN-VN',origin:flight.dest,dest:flight.origin}}]}
 api.get=async()=>page([roundtrip])
 await render(React.createElement(MyBookingsModal,{isOpen:true,onClose:()=>{},onPayBooking:b=>resumed=b}))
 assert.match(container.textContent,/DEMO-VN/);assert.match(container.textContent,/RETURN-VN/);assert.doesNotMatch(container.textContent,/2 Passengers/)
 await act(async()=>button('Complete Payment').click());assert.equal(resumed.id,7)
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
  await act(async()=>button('In-Flight Hot Meal').click())
  await act(async()=>button('+20kg Checked Bag').click())
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
