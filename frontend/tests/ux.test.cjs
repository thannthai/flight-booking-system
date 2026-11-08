const { test } = require('node:test')
const assert = require('node:assert/strict')
const { fetchAllPages } = require('../lib/search.ts')
const { assignSeats } = require('../lib/seats.ts')
const { bookingStep, readBookingSession, reachableStep } = require('../lib/booking-session.ts')
test('client filters receive all backend pages, with the query preserved',async()=>{const calls=[];const items=await fetchAllPages('/flights/search?origin=HAN',async path=>{calls.push(path);return {content:[calls.length],last:calls.length===2,totalPages:2}});assert.deepEqual(items,[1,2]);assert.ok(calls[1].includes('origin=HAN&page=1&size=100'))})
test('auto assign respects requested cabin and never treats HELD/BOOKED as owned',()=>{const s=(id,cabinClass,status,price)=>({id,seatNo:`${id}A`,cabinClass,status,price});const chosen=assignSeats([s(1,'BUSINESS','AVAILABLE',250),s(2,'ECONOMY','HELD',50),s(3,'ECONOMY','BOOKED',40),s(4,'ECONOMY','AVAILABLE',100),s(5,'ECONOMY','AVAILABLE',90)],[null,null],'ECONOMY');assert.deepEqual(chosen.map(s=>s.id),[5,4])})
const blank={selectedFlight:null,selectedReturnFlight:null,passengerData:null,selectedSeats:[],selectedReturnSeats:[],bookingData:null,holdExpiresAt:null}
test('arbitrary URL steps and missing prerequisites fail to safe views',()=>{assert.equal(bookingStep('javascript:alert(1)'),'home');assert.equal(reachableStep('seats',blank),'results');assert.equal(reachableStep('payment',blank,42),'payment');assert.equal(reachableStep('payment',{...blank,selectedFlight:{id:1}}),'passengers')})
test('expired hold cannot restore directly into create booking',()=>{const s={...blank,selectedFlight:{id:1},passengerData:{paxList:[{type:'Adult'}]},selectedSeats:[{id:1}],holdExpiresAt:'2000-01-01'};assert.equal(reachableStep('payment',s),'seats');assert.equal(reachableStep('payment',{...s,holdExpiresAt:'2099-01-01'}),'payment')})
test('legacy, malformed, foreign and expired drafts are not restored',()=>{for(const raw of ['{','{}',JSON.stringify({version:3,ownerId:2,updatedAt:Date.now()}),JSON.stringify({version:3,ownerId:1,updatedAt:1})])assert.equal(readBookingSession(raw,1),null)})
test('draft reader rejects arrays and primitive values at persisted object boundaries',()=>{
 const session={version:3,ownerId:1,updatedAt:Date.now(),view:'results',searchParams:{originCode:'SGN',destinationCode:'HAN',departureDate:'2099-11-01',returnDate:null,passengers:{adults:1,children:0,infants:0},cabinClass:'ECONOMY'},selectedFlight:null,selectedReturnFlight:null,selectedSeats:[],selectedReturnSeats:[],passengerDraft:null,passengerData:null,bookingData:null,holdExpiresAt:null}
 assert.equal(readBookingSession(JSON.stringify(session),1).view,'results')
 const corruptions=[{updatedAt:'today'},{searchParams:[]},{searchParams:{...session.searchParams,departureDate:['2099-11-01']}},{searchParams:{...session.searchParams,passengers:{adults:'1',children:0,infants:0}}},{selectedFlight:[]},{selectedSeats:[{id:'1',price:1000}]},{passengerDraft:{contactInfo:[],passengers:[],passengerAddOns:{}}},{bookingData:{id:'7'}}]
 for(const change of corruptions)assert.equal(readBookingSession(JSON.stringify({...session,...change}),1),null)
})
