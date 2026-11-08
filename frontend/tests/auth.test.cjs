const { test, beforeEach } = require('node:test')
const assert = require('node:assert/strict')
const makeStorage = () => { const data=new Map();return {getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,String(v)),removeItem:k=>data.delete(k)} }
let client
const user = id => ({ id, fullName:`User ${id}`,email:`${id}@example.test` })
const token = () => `e30.${Buffer.from(JSON.stringify({ exp:Date.now()/1000+900 })).toString('base64url')}.test`
const response = (id=1) => ({ok:true,status:200,json:async()=>({data:{accessToken:token(),user:user(id)}})})
beforeEach(() => { global.window={dispatchEvent:()=>{}};global.CustomEvent=class{constructor(type){this.type=type}};global.sessionStorage=makeStorage();global.localStorage=makeStorage();const facade=require('../lib/api.ts');client={...facade,...facade.createApiClient()} })
test('parallel refreshes share one request and publish one authoritative snapshot',async()=>{let calls=0,release;global.fetch=()=>{calls++;return new Promise(r=>release=r)};const a=client.refreshAccessToken(),b=client.refreshAccessToken();assert.equal(a,b);release(response());await Promise.all([a,b]);assert.equal(calls,1);assert.equal(client.getAuthState().user.id,1);assert.ok(client.getAccessToken());assert.equal(sessionStorage.getItem('access_token'),null)})
test('login preserves invalid credentials error without session-expired event',async()=>{let events=0;window.dispatchEvent=()=>events++;global.fetch=async()=>({ok:false,status:401,json:async()=>({message:'Invalid email or password',errorCode:'INVALID_CREDENTIALS'})});await assert.rejects(client.api.login({}),e=>e.errorCode==='INVALID_CREDENTIALS');assert.equal(events,0)})
test('logout prevents stale refresh from resurrecting session and drains rotation',async()=>{let release,calls=[];global.fetch=async url=>{calls.push(url);if(url.endsWith('refresh'))return new Promise(r=>release=r);return response()};const refresh=client.refreshAccessToken();sessionStorage.setItem('skyway_session','private');const logout=client.api.logout();release(response());await Promise.all([refresh,logout]);assert.equal(client.getAuthState().user,null);assert.equal(sessionStorage.getItem('skyway_session'),null);assert.equal(calls.length,2);await client.refreshAccessToken();assert.equal(calls.length,2)})
test('account switch clears private drafts and unknown booking marker',async()=>{global.fetch=async()=>response(1);await client.api.login({});sessionStorage.setItem('skyway_session','A');sessionStorage.setItem('skyway_booking_attempt','A');global.fetch=async()=>response(2);await client.api.login({});assert.equal(client.getCurrentUser().id,2);assert.equal(sessionStorage.getItem('skyway_session'),null);assert.equal(sessionStorage.getItem('skyway_booking_attempt'),null)})
test('late data response from prior account is rejected before returning private data',async()=>{global.fetch=async()=>response(1);await client.api.login({});let release;global.fetch=()=>new Promise(r=>release=r);const old=client.api.getBookingById(1);client.invalidateAuth();release({ok:true,status:200,json:async()=>({data:{contactName:'private A'}})});await assert.rejects(old,e=>e.errorCode==='SESSION_CHANGED')})
test('failed logout blocks silent reauthentication from leftover cookie',async()=>{global.fetch=async()=>response(1);await client.api.login({});global.fetch=async()=>{throw Error('offline')};await assert.rejects(client.api.logout());global.fetch=async()=>{throw Error('must not refresh')};assert.equal(await client.refreshAccessToken(),null);assert.equal(client.getCurrentUser(),null)})
test('silent refresh updates subscribers without losing same-user checkout draft',async()=>{global.fetch=async()=>response(1);await client.api.login({});sessionStorage.setItem('skyway_session','same-user');let updates=0;const unsubscribe=client.subscribeAuth(()=>updates++);await client.refreshAccessToken();unsubscribe();assert.equal(updates,1);assert.equal(sessionStorage.getItem('skyway_session'),'same-user')})
test('5xx mentioning JWT never retries a mutating booking request',async()=>{global.fetch=async()=>response(1);await client.api.login({});let calls=0;global.fetch=async()=>{calls++;return {ok:false,status:500,json:async()=>({message:'JWT downstream timeout',errorCode:'INTERNAL_ERROR'})}};await assert.rejects(client.api.post('/bookings',{}));assert.equal(calls,1)})
test('bounded HTTP wait aborts a stalled request',async()=>{global.fetch=(_input,init)=>new Promise((resolve,reject)=>init.signal.addEventListener('abort',()=>reject(new DOMException('Timed out','AbortError'))));await assert.rejects(client.fetchWithTimeout('/slow',{},5),e=>e.name==='AbortError')})
test('unavailable browser storage never prevents clearing in-memory auth on logout',async()=>{global.fetch=async()=>response(1);await client.api.login({});global.sessionStorage={getItem:()=>{throw Error('blocked')},setItem:()=>{throw Error('blocked')},removeItem:()=>{throw Error('blocked')}};global.localStorage={setItem:()=>{throw Error('blocked')},removeItem:()=>{throw Error('blocked')}};await client.api.logout();assert.equal(client.getCurrentUser(),null);assert.equal(await client.refreshAccessToken(),null)})
test('separate API client factories do not share auth snapshots or listeners',async()=>{
 const {createApiClient}=require('../lib/api.ts')
 const other=createApiClient();let changes=0
 other.subscribeAuth(()=>changes++)
 global.fetch=async()=>response(1);await client.api.login({})
 assert.equal(client.getCurrentUser().id,1)
 assert.equal(other.getCurrentUser(),null)
 assert.equal(changes,0)
 global.fetch=async()=>response(2);await other.api.login({})
 assert.equal(other.getCurrentUser().id,2)
 assert.equal(client.getCurrentUser().id,1)
})
test('concurrent protected 401 responses share refresh and retry each request once',{timeout:3000},async()=>{
 global.fetch=async()=>response(1);await client.api.login({})
 const attempts=new Map();let refreshes=0,release,started
 const refreshing=new Promise(resolve=>started=resolve)
 global.fetch=async url=>{
  if(url.endsWith('/auth/refresh')){refreshes++;started();return new Promise(resolve=>release=resolve)}
  const count=(attempts.get(url)||0)+1;attempts.set(url,count)
  return count===1?{ok:false,status:401,json:async()=>({message:'Expired',errorCode:'UNAUTHORIZED'})}:{ok:true,status:200,json:async()=>({data:{id:url.endsWith('/1')?1:2}})}
 }
 const a=client.api.getBookingById(1),b=client.api.getBookingById(2)
 await refreshing
 // Flush the second request's 401 handler while refresh is held.
 await new Promise(resolve=>setImmediate(resolve))
 assert.equal(refreshes,1)
 release(response(1))
 assert.deepEqual((await Promise.all([a,b])).map(item=>item.id),[1,2])
 assert.deepEqual([...attempts.values()],[2,2])
})
test('logout during a protected request refresh fences mutation instead of replaying it',{timeout:3000},async()=>{
 global.fetch=async()=>response(1);await client.api.login({})
 let release,started,creates=0
 const refreshing=new Promise(resolve=>started=resolve)
 global.fetch=async url=>{
  if(url.endsWith('/auth/refresh')){started();return new Promise(resolve=>release=resolve)}
  if(url.endsWith('/auth/logout'))return response()
  creates++;return {ok:false,status:401,json:async()=>({errorCode:'UNAUTHORIZED'})}
 }
 const pending=client.api.post('/bookings',{})
 const rejected=assert.rejects(pending,error=>error.errorCode==='SESSION_CHANGED')
 await refreshing
 const logout=client.api.logout();release(response(1))
 await Promise.all([rejected,logout])
 assert.equal(creates,1)
 assert.equal(client.getCurrentUser(),null)
 assert.equal(await client.refreshAccessToken(),null)
})
test('account switch while decoding a response rejects prior data and clears both checkout stores',{timeout:3000},async()=>{
 global.fetch=async()=>response(1);await client.api.login({})
 sessionStorage.setItem('skyway_session','private-A');sessionStorage.setItem('skyway_booking_attempt','unknown-A')
 let release,started
 const decoding=new Promise(resolve=>started=resolve)
 global.fetch=async()=>({ok:true,status:200,json:()=>{started();return new Promise(resolve=>release=resolve)}})
 const pending=client.api.getBookingById(1)
 const rejected=assert.rejects(pending,error=>error.errorCode==='SESSION_CHANGED')
 await decoding
 global.fetch=async()=>response(2);await client.api.login({})
 release({data:{contactName:'PRIVATE A'}});await rejected
 assert.equal(client.getCurrentUser().id,2)
 assert.equal(sessionStorage.getItem('skyway_session'),null)
 assert.equal(sessionStorage.getItem('skyway_booking_attempt'),null)
})
test('demo login preserves credential failures without refresh or global sign-in modal',async()=>{
 let calls=0,events=0;window.dispatchEvent=()=>events++
 global.fetch=async()=>{calls++;return {ok:false,status:401,json:async()=>({message:'Invalid credentials',errorCode:'INVALID_CREDENTIALS'})}}
 await assert.rejects(client.api.loginDemo('A'),e=>e.errorCode==='INVALID_CREDENTIALS')
 assert.equal(calls,1);assert.equal(events,0)
})

function installLocks(t) {
 const descriptor=Object.getOwnPropertyDescriptor(globalThis,'navigator')
 let tail=Promise.resolve()
 const locks={request:(_name,operation)=>{const result=tail.then(operation);tail=result.catch(()=>{});return result}}
 Object.defineProperty(globalThis,'navigator',{value:{locks},configurable:true})
 t.after(()=>{if(descriptor)Object.defineProperty(globalThis,'navigator',descriptor);else delete globalThis.navigator})
 return locks
}

test('independent tabs serialize rotations and send the newly installed cookie',async t=>{
 installLocks(t)
 const {createAuthSession}=require('../lib/auth-session.ts')
 const a=createAuthSession(()=>{}),b=createAuthSession(()=>{})
 let cookie=0,active=0,maxActive=0,calls=0
 global.fetch=async()=>{
  const sent=cookie;active++;maxActive=Math.max(maxActive,active);calls++
  await new Promise(resolve=>setImmediate(resolve))
  assert.equal(sent,cookie,'a stale cookie must never race a rotation')
  cookie++;active--;return response()
 }
 const results=await Promise.all([a.refreshAccessToken(),b.refreshAccessToken()])
 assert.ok(results.every(Boolean));assert.equal(calls,2);assert.equal(maxActive,1)
 assert.equal(a.getCurrentUser().id,1);assert.equal(b.getCurrentUser().id,1)
 assert.equal(localStorage.getItem('access_token'),null)
})

test('logout fences a refresh waiting for another tab cookie lock',async t=>{
 const locks=installLocks(t)
 let release,entered
 const started=new Promise(resolve=>entered=resolve)
 const blocker=locks.request('skyway-auth-cookie',()=>{entered();return new Promise(resolve=>release=resolve)})
 await started
 const {createAuthSession}=require('../lib/auth-session.ts')
 const session=createAuthSession(()=>{})
 const calls=[];global.fetch=async url=>{calls.push(url);return response()}
 const refresh=session.refreshAccessToken(),logout=session.logout()
 release();await blocker;await Promise.all([refresh,logout])
 assert.deepEqual(calls,['/api/v1/auth/logout']);assert.equal(session.getCurrentUser(),null)
})

test('sign-in waits for another tab rotation before replacing its cookie',async t=>{
 installLocks(t)
 const {createAuthSession}=require('../lib/auth-session.ts')
 const a=createAuthSession(()=>{}),b=createAuthSession(()=>{})
 let release,entered;const started=new Promise(resolve=>entered=resolve),calls=[]
 global.fetch=async url=>{calls.push(url);if(url.endsWith('/refresh')){entered();return new Promise(resolve=>release=resolve)}return response(2)}
 const refresh=a.refreshAccessToken();await started
 const login=b.login({email:'2@example.test',password:'test'})
 await new Promise(resolve=>setImmediate(resolve));assert.equal(calls.length,1)
 release(response());await Promise.all([refresh,login])
 assert.equal(b.getCurrentUser().id,2);assert.equal(calls.length,2)
})
