const { test } = require('node:test')
const assert = require('node:assert/strict')
const { guardianIndexInput } = require('../lib/checkout-model.ts')
const { buildPassengerRequests, buildBookingRequest } = require('../lib/checkout-requests.ts')
const slots = ['Adult', 'Child', 'Infant'].map(type => ({ type, title: type }))
const form = (title, name, dob, id = '') => ({ title, name, dob, idType: id ? 'PASSPORT' : '', id, idExpiry: '', nationality: 'Vietnam', guardianIndex: null })
const forms = [form('Ms', ' Adult ', '1990-01-01', 'A123'), form('Mstr', 'Child', '2020-01-01'), { ...form('Miss', 'Infant', '2026-01-01'), guardianIndex: 0 }]
const data = () => ({ paxList: slots, passengerRequests: buildPassengerRequests(slots, forms), contactInfo: { name: 'Contact', email: 'test@example.test', phone: '12345678' }, passengerAddOns: { 1: { baggage: 400000, meal: true, insurance: false } } })

test('guardian input accepts zero and second adult index, rejects blank/fractional/negative/unsafe values', () => {
  assert.equal(guardianIndexInput('0'), 0)
  assert.equal(guardianIndexInput('1'), 1)
  assert.equal(guardianIndexInput(1), 1)
  for (const input of ['', ' ', '-1', '1.5', 'text', null, undefined, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1]) assert.equal(guardianIndexInput(input), null)
})
test('passenger builder maps enums, gender, optional documents and guardian zero without mutating forms', () => {
  const before = JSON.stringify(forms)
  const requests = buildPassengerRequests(slots, forms)
  assert.equal(requests[0].fullName, 'Adult')
  assert.equal(requests[0].gender, 'FEMALE')
  assert.deepEqual(requests.map(p => p.passengerType), ['ADULT', 'CHILD', 'INFANT'])
  assert.equal(requests[1].identityType, undefined)
  assert.equal(requests[1].guardianPassengerIndex, undefined)
  assert.equal(requests[2].guardianPassengerIndex, 0)
  assert.equal(JSON.stringify(forms), before)
  for (const guardianIndex of [null, '0', 1, -1]) assert.throws(() => buildPassengerRequests(slots, [...forms.slice(0, 2), { ...forms[2], guardianIndex }]), /guardian/)
})
for (const roundTrip of [false, true]) test(`booking builder maps ${roundTrip ? 'round trip' : 'one way'} seats by original passenger index and extras only outbound`, () => {
  // Infant first ensures seated array indices cannot be confused with passenger indices.
  const passengerData = data()
  passengerData.paxList = [slots[2], slots[0], slots[1]]
  const [adult, child, infant] = passengerData.passengerRequests
  passengerData.passengerRequests = [{ ...infant, guardianPassengerIndex: 1 }, adult, child]
  passengerData.passengerAddOns = { 2: { baggage: 400000, meal: true, insurance: false } }
  const result = buildBookingRequest({ flight: { id: 10 }, returnFlight: roundTrip ? { id: 20 } : null, seats: [{ id: 11 }, { id: 12 }], returnSeats: [{ id: 21 }, { id: 22 }], passengerData })
  assert.equal(result.tripType, roundTrip ? 'ROUND_TRIP' : 'ONE_WAY')
  assert.deepEqual(result.flightIds, roundTrip ? [10, 20] : [10])
  assert.deepEqual(result.contactInfo, { fullName: 'Contact', email: 'test@example.test', phone: '12345678' })
  assert.deepEqual(result.seatSelections.map(s => [s.flightId, s.flightSeatId, s.passengerIndex]), roundTrip ? [[10, 11, 1], [10, 12, 2], [20, 21, 1], [20, 22, 2]] : [[10, 11, 1], [10, 12, 2]])
  assert.deepEqual(result.seatSelections[1].addOns, [{ serviceType: 'BAGGAGE', quantity: 2 }, { serviceType: 'MEAL', quantity: 1 }])
  if (roundTrip) assert.deepEqual(result.seatSelections[3].addOns, [])
})
test('incomplete seat selection is rejected before a booking request can be sent', () => {
  assert.throws(() => buildBookingRequest({ flight: { id: 1 }, seats: [{ id: 11 }, null], returnSeats: [], passengerData: data() }), /Seat selection is incomplete/)
})
test('draft validation reports contact and passenger errors independently of React',()=>{
 const {validatePassengerDraft}=require('../lib/passenger-validation.ts')
 const errors=validatePassengerDraft({contactInfo:{name:'',email:'invalid',phone:'123'},passengers:forms,passengerAddOns:{}},slots,'2026-11-01')
 assert.deepEqual(Object.keys(errors),['contact_name','contact_email','contact_phone'])
 assert.deepEqual(validatePassengerDraft({contactInfo:{name:'Contact',email:'test@example.test',phone:'0123456789'},passengers:forms,passengerAddOns:{}},slots,'2026-11-01'),{})
})
