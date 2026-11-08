import { test, expect } from '@playwright/test'

test('two tabs rotate one shared cookie serially and both stay signed in', async ({ context }) => {
  let version = 0, rejected = 0
  const seen: string[] = []
  let release!: () => void, entered!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  const started = new Promise<void>(resolve => { entered = resolve })
  const user = { id: 1, fullName: 'Tab Tester', email: 'tabs@example.test', role: 'ROLE_USER' }
  const token = () => `e30.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 900 })).toString('base64url')}.test-only`
  await context.addCookies([{ name: 'refreshToken', value: 'v0', domain: '127.0.0.1', path: '/api/v1/auth', httpOnly: true, sameSite: 'Lax' }])
  await context.route('https://tiles.openfreemap.org/**', route => route.abort())
  await context.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/auth/refresh')) {
      const cookie = (await route.request().allHeaders()).cookie || ''
      seen.push(cookie)
      if (seen.length === 1) { entered(); await gate }
      if (!cookie.includes(`refreshToken=v${version}`)) {
        rejected++
        return route.fulfill({ status: 401, json: { success: false, errorCode: 'REFRESH_TOKEN_INVALID' } })
      }
      version++
      return route.fulfill({ json: { success: true, data: { accessToken: token(), user } },
        headers: { 'set-cookie': `refreshToken=v${version}; HttpOnly; SameSite=Lax; Path=/api/v1/auth` } })
    }
    if (path.endsWith('/airports')) return route.fulfill({ json: { success: true, data: { content: [], last: true, totalPages: 0 } } })
    if (path.endsWith('/demo/info')) return route.fulfill({ json: { success: true, data: { enabled: false, accountsEnabled: false, flights: [] } } })
    return route.fulfill({ status: 401, json: { success: false } })
  })
  const first = await context.newPage(), second = await context.newPage()
  try {
    await first.goto('/')
    await started
    await second.goto('/')
    await expect.poll(() => second.evaluate(async () => (await navigator.locks.query()).pending?.filter(lock => lock.name === 'skyway-auth-cookie').length)).toBe(1)
    expect(seen).toHaveLength(1)
    release()
    await expect(first.getByRole('button', { name: 'Tab Tester' })).toBeVisible()
    await expect(second.getByRole('button', { name: 'Tab Tester' })).toBeVisible()
    expect(rejected).toBe(0)
    expect(seen).toHaveLength(2)
    expect(seen[1]).toContain('refreshToken=v1')
    const cookie = (await context.cookies()).find(value => value.name === 'refreshToken')
    expect(cookie?.value).toBe('v2')
    for (const page of [first, second]) {
      expect(await page.evaluate(() => [localStorage.getItem('access_token'), sessionStorage.getItem('access_token')])).toEqual([null, null])
      await expect(page.getByRole('dialog', { name: 'Authentication' })).toHaveCount(0)
    }
  } finally { release() }
})
