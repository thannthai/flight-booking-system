import { test, expect, type Page } from '@playwright/test'

async function airports(page: Page) {
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/airports')) return route.fulfill({ json: { success: true, data: { content: [
      { id: 1, iataCode: 'SGN', name: 'Tan Son Nhat', country: 'Vietnam', city: 'Hồ Chí Minh', timezone: 'Asia/Ho_Chi_Minh' },
      { id: 2, iataCode: 'HAN', name: 'Noi Bai', country: 'Vietnam', city: 'Hà Nội', timezone: 'Asia/Ho_Chi_Minh' },
    ], last: true, totalPages: 1 } } })
    if (path.endsWith('/demo/info')) return route.fulfill({ json: { success: true, data: { enabled: false, accountsEnabled: false, flights: [] } } })
    return route.fulfill({ status: 401, json: { success: false } })
  })
}

for (const width of [375, 768, 1280]) {
  test(`route map follows swap, preserves form and falls back at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    await airports(page)
    // Local style tests rendering without depending on a public tile server in CI.
    await page.route('https://tiles.openfreemap.org/**', route => {
      if (route.request().url().endsWith('/styles/liberty')) return route.fulfill({ json: {
        version: 8, sources: {}, layers: [{ id: 'background', type: 'background', paint: { 'background-color': '#fff' } }],
      } })
      return route.abort()
    })
    await page.goto('/')
    const map = page.getByRole('figure', { name: 'Selected flight route' })
    await expect(map).toHaveAttribute('data-map-state', 'ready')
    await expect(map.locator('figcaption')).toContainText('SGN → HAN')
    await expect(map.locator('.maplibregl-marker').filter({ hasText: 'SGN' })).toBeVisible()
    await page.getByRole('button', { name: 'Swap departure and destination' }).click()
    await expect(map.locator('figcaption')).toContainText('HAN → SGN')
    await expect(map.locator('.maplibregl-marker').filter({ hasText: 'HAN' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Search flights', exact: true })).toBeEnabled()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await expect(map.getByRole('link', { name: '© OpenStreetMap', exact: true })).toBeVisible()
    await page.unroute('https://tiles.openfreemap.org/**')
    await page.route('https://tiles.openfreemap.org/**', route => route.abort())
    await page.reload()
    await expect(map).toHaveAttribute('data-map-state', 'fallback')
    await expect(map.locator('figcaption')).toContainText('SGN → HAN')
    await expect(page.getByRole('button', { name: 'Search flights', exact: true })).toBeEnabled()
    expect(errors).toEqual([])
  })
}

const plainStyle = { version: 8, sources: {}, layers: [{ id: 'background', type: 'background', paint: { 'background-color': '#edf2f4' } }] }

test('temporary style failure recovers automatically without changing the selected route', async ({ page }) => {
  await airports(page)
  let reads = 0
  await page.route('https://tiles.openfreemap.org/**', route => {
    reads++
    return reads === 1 ? route.abort() : route.fulfill({ json: plainStyle })
  })
  await page.goto('/')
  const map = page.getByRole('figure', { name: 'Selected flight route' })
  await expect(map).toHaveAttribute('data-map-state', 'fallback')
  await page.getByRole('button', { name: 'Swap departure and destination' }).click()
  await expect(map).toHaveAttribute('data-map-state', 'ready')
  await expect(map.locator('figcaption')).toContainText('HAN → SGN')
  await expect(map.locator('.maplibregl-marker').filter({ hasText: 'HAN' })).toBeVisible()
  expect(reads).toBe(2)
})

test('persistent outage stops automatic retries and online event restores the map', async ({ page }) => {
  await airports(page)
  let available = false, reads = 0
  await page.route('https://tiles.openfreemap.org/**', route => {
    reads++
    return available ? route.fulfill({ json: plainStyle }) : route.abort()
  })
  await page.goto('/')
  const map = page.getByRole('figure', { name: 'Selected flight route' })
  await expect.poll(() => reads, { timeout: 12000 }).toBe(4)
  await expect(map).toHaveAttribute('data-map-state', 'fallback')
  await page.waitForTimeout(1500)
  expect(reads).toBe(4)
  available = true
  await page.evaluate(() => window.dispatchEvent(new Event('online')))
  await expect(map).toHaveAttribute('data-map-state', 'ready')
  expect(reads).toBe(5)
  await expect(map.getByRole('link', { name: '© OpenStreetMap', exact: true })).toBeVisible()
})

test('failed tile is retried without requiring page reload', async ({ page }) => {
  await airports(page)
  let tiles = 0, styles = 0
  await page.route('https://tiles.openfreemap.org/styles/liberty', route => {
    styles++
    return route.fulfill({ json: { ...plainStyle,
      sources: { test: { type: 'raster', tiles: ['https://tiles.example.invalid/{z}/{x}/{y}.png'], tileSize: 256, minzoom: 0, maxzoom: 0 } },
      layers: [...plainStyle.layers, { id: 'test-raster', type: 'raster', source: 'test' }],
    } })
  })
  await page.route('https://tiles.example.invalid/**', route => {
    tiles++
    return tiles === 1 ? route.abort() : route.fulfill({ contentType: 'image/png',
      body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAQAAAAEACAYAAABccqhmAAADH0lEQVR4nO3UoREAIBDAsN9/VwyHhzEQjYiv6qx9LtA0vwOAfwwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwgwAwh5fyTlPjU07bQAAAABJRU5ErkJggg==', 'base64') })
  })
  await page.goto('/')
  const map = page.getByRole('figure', { name: 'Selected flight route' })
  await expect.poll(() => styles).toBe(2)
  await expect.poll(() => tiles).toBeGreaterThanOrEqual(2)
  await expect(map).toHaveAttribute('data-map-state', 'ready')
  await expect(map.locator('.maplibregl-marker').filter({ hasText: 'SGN' })).toBeVisible()
  await page.waitForTimeout(1500)
  expect(styles).toBe(2)
})

test('missing glyphs keep the map and selected route usable', async ({ page }) => {
  await airports(page)
  let styles = 0, glyphs = 0
  await page.route('https://tiles.openfreemap.org/styles/liberty', route => {
    styles++
    return route.fulfill({ json: { ...plainStyle, glyphs: 'https://glyphs.example.invalid/{fontstack}/{range}.pbf',
      sources: { cities: { type: 'geojson', data: { type: 'Feature', properties: { class: 'city', rank: 1, name: 'City', capital: 0 }, geometry: { type: 'Point', coordinates: [106, 16] } } } },
      layers: [...plainStyle.layers, { id: 'label_city', type: 'symbol', source: 'cities', layout: { 'text-field': '{name}', 'text-font': ['Noto Sans Regular'] } }],
    } })
  })
  await page.route('https://glyphs.example.invalid/**', route => { glyphs++; return route.fulfill({ status: 503, body: 'unavailable' }) })
  await page.goto('/')
  const map = page.getByRole('figure', { name: 'Selected flight route' })
  await expect.poll(() => glyphs).toBeGreaterThan(0)
  await expect(map).toHaveAttribute('data-map-state', 'ready')
  await page.getByRole('button', { name: 'Swap departure and destination' }).click()
  await expect(map.locator('figcaption')).toContainText('HAN → SGN')
  await expect(map.locator('.maplibregl-marker').filter({ hasText: 'HAN' })).toBeVisible()
  await page.waitForTimeout(1500)
  await expect(map).toHaveAttribute('data-map-state', 'ready')
  expect(styles).toBe(1)
})
