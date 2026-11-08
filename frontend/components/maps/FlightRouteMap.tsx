'use client'

import { useEffect, useRef, useState } from 'react'
import type { Map as LibreMap, Marker, StyleSpecification } from 'maplibre-gl'
import type { AirportResponse } from '@/types/api'
import { airportCoordinates } from '@/lib/airport-coordinates'

type Coordinate = [number, number]
const STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty'

function coordinates(airport: AirportResponse | null): Coordinate | undefined {
  if (!airport) return undefined
  const { longitude, latitude } = airport
  if (typeof longitude === 'number' && typeof latitude === 'number' &&
      Number.isFinite(longitude) && Number.isFinite(latitude) && Math.abs(longitude) <= 180 && Math.abs(latitude) <= 90) {
    return [longitude, latitude]
  }
  return airportCoordinates[airport.iataCode]
}

// Unwrap longitude before drawing: routes across the date line take the short way.
function curve(start: Coordinate, end: Coordinate): Coordinate[] {
  const dx = ((end[0] - start[0] + 540) % 360) - 180
  const dy = end[1] - start[1]
  const control: Coordinate = [start[0] + dx / 2 - dy * .18, start[1] + dy / 2 + dx * .18]
  return Array.from({ length: 101 }, (_, index) => {
    const t = index / 100, u = 1 - t
    return [u * u * start[0] + 2 * u * t * control[0] + t * t * (start[0] + dx),
      u * u * start[1] + 2 * u * t * control[1] + t * t * end[1]]
  })
}

function simplify(map: LibreMap) {
  for (const layer of map.getStyle().layers) {
    const country = layer.id.startsWith('label_country_')
    const city = layer.id === 'label_city' || layer.id === 'label_city_capital'
    const boundary = layer.id === 'boundary_2' || layer.id === 'boundary_disputed'
    const keep = layer.type === 'background' || layer.id === 'water' || boundary || country || city
    if (!keep) map.setLayoutProperty(layer.id, 'visibility', 'none')
    if (layer.type === 'background') map.setPaintProperty(layer.id, 'background-color', '#edf2f4')
    if (layer.id === 'water') map.setPaintProperty(layer.id, 'fill-color', '#cedfe8')
    if (boundary) {
      map.setPaintProperty(layer.id, 'line-color', '#a2b6c2')
      map.setPaintProperty(layer.id, 'line-width', .8)
    }
    if (country || city) {
      map.setPaintProperty(layer.id, 'text-color', '#768b99')
      map.setPaintProperty(layer.id, 'text-halo-color', '#edf2f4')
      map.setLayoutProperty(layer.id, 'text-size', ['interpolate', ['linear'], ['zoom'], 2, country ? 10 : 9, 7, country ? 12 : 11])
      map.setLayoutProperty(layer.id, 'text-padding', country ? 8 : 5)
      map.setLayoutProperty(layer.id, 'text-field', ['coalesce', ['get', 'name:en'], ['get', 'name']])
      if (city && layer.type === 'symbol') map.setFilter(layer.id, ['all', ['==', ['get', 'class'], 'city'], ['<=', ['to-number', ['get', 'rank'], 99], 3], [layer.id === 'label_city_capital' ? '==' : '!=', ['get', 'capital'], 2]])
    }
  }
}

export function FlightRouteMap({ from, to }: { from: AirportResponse | null; to: AirportResponse | null }) {
  const container = useRef<HTMLDivElement>(null)
  const mapRef = useRef<LibreMap | null>(null)
  const [ready, setReady] = useState(false)
  const [failed, setFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const retries = useRef(0)
  const recoveryNeeded = useRef(false)
  const origin = coordinates(from), destination = coordinates(to)
  const originLng = origin?.[0], originLat = origin?.[1], destLng = destination?.[0], destLat = destination?.[1]

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    let map: LibreMap | undefined
    let observer: ResizeObserver | undefined
    let retryTimer: number | undefined
    let sourceError = false
    const retry = (delay: number) => {
      if (!active || retryTimer !== undefined || retries.current >= 3) return
      retries.current++
      retryTimer = window.setTimeout(() => {
        if (!active) return
        setReady(false)
        setFailed(false)
        setAttempt(value => value + 1)
      }, delay)
    }
    const unavailable = () => {
      if (!active) return
      recoveryNeeded.current = true
      setFailed(true)
      retry(1000 * 2 ** retries.current)
    }
    const online = () => {
      if (!recoveryNeeded.current) return
      retries.current = 0
      window.clearTimeout(retryTimer)
      retryTimer = undefined
      retry(0)
    }
    window.addEventListener('online', online)
    const timeout = window.setTimeout(unavailable, 15000)
    async function initialize() {
      try {
        const [libre, { workerUrl }, response] = await Promise.all([
          import('maplibre-gl'), import('@/lib/map-worker'), fetch(STYLE_URL, { signal: controller.signal }),
        ])
        if (!response.ok) throw new Error('Map style unavailable')
        const style: StyleSpecification = await response.json()
        if (!active || !container.current) return
        libre.setWorkerUrl(workerUrl)
        map = new libre.Map({ container: container.current, style, center: [106, 16], zoom: 3,
          interactive: false, attributionControl: false, renderWorldCopies: false })
        mapRef.current = map
        // Attribution stays outside the WebGL canvas, including during failure.
        map.on('error', event => {
          if (!active) return
          // Tile/TileJSON errors are recoverable. Glyph/sprite warnings must not
          // hide an otherwise usable map. A stalled initial load has its timeout.
          if (/glyph|sprite|\/fonts\//i.test(event.error.message)) return
          if ('sourceId' in event && event.sourceId) {
            sourceError = true
            recoveryNeeded.current = true
            retry(1000 * 2 ** retries.current)
          }
        })
        map.on('load', () => {
          if (!active || !map) return
          simplify(map)
          setReady(true)
          setFailed(false)
          if (!sourceError) recoveryNeeded.current = false
          window.clearTimeout(timeout)
        })
        observer = new ResizeObserver(() => map?.resize())
        observer.observe(container.current)
      } catch {
        unavailable()
      }
    }
    void initialize()
    return () => {
      active = false
      controller.abort()
      window.removeEventListener('online', online)
      window.clearTimeout(retryTimer)
      window.clearTimeout(timeout)
      observer?.disconnect()
      mapRef.current = null
      map?.remove()
    }
  }, [attempt])

  useEffect(() => {
    const map = mapRef.current
    if (!map || !ready || failed || originLng === undefined || originLat === undefined || destLng === undefined || destLat === undefined) return
    let active = true, frame = 0
    const markers: Marker[] = []
    let observer: ResizeObserver | undefined
    const points = curve([originLng, originLat], [destLng, destLat])
    const fit = () => {
      if (!active || mapRef.current !== map) return
      const lngs = points.map(p => p[0]), lats = points.map(p => p[1])
      map.fitBounds([[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]],
        { padding: { top: 32, bottom: 35, left: 48, right: 48 }, maxZoom: 7, duration: 0 })
    }
    const draw = async () => {
      const { Marker } = await import('maplibre-gl')
      if (!active) return
      map.addSource('flight-route', { type: 'geojson', data: { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: points } } })
      map.addLayer({ id: 'flight-route', type: 'line', source: 'flight-route', layout: { 'line-cap': 'round' }, paint: { 'line-color': '#102a56', 'line-width': 2.5 } })
      for (const [point, code] of [[points[0], from?.iataCode], [points[100], to?.iataCode]] as const) {
        const element = document.createElement('div')
        element.className = 'relative'
        const dot = document.createElement('span')
        dot.className = 'block size-3 rounded-full border-2 border-white bg-[#102a56] shadow-sm'
        const label = document.createElement('span')
        label.className = 'absolute left-4 top-1/2 -translate-y-1/2 rounded bg-white/90 px-1 text-[11px] font-semibold text-[#102a56]'
        label.textContent = code || ''
        element.append(dot, label)
        markers.push(new Marker({ element }).setLngLat(point).addTo(map))
      }
      const element = document.createElement('div')
      element.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24" fill="#f59e0b" stroke="#fff" stroke-width="1" aria-hidden="true"><path d="M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.1-1.1.5l-.3.5c-.2.5-.1 1 .3 1.3L9 12l-2 3H4l-1 1 3 2 2 3 1-1v-3l3-2 3.5 5.3c.3.4.8.5 1.3.3l.5-.2c.4-.3.6-.7.5-1.2z"/></svg>'
      element.setAttribute('aria-hidden', 'true')
      const plane = new Marker({ element, rotationAlignment: 'map' }).setLngLat(points[0]).addTo(map)
      markers.push(plane)
      fit()
      observer = new ResizeObserver(fit)
      if (container.current) observer.observe(container.current)
      const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
      const start = performance.now()
      const animate = (now: number) => {
        if (!active) return
        const progress = reduced ? 1 : Math.max(0, Math.min((now - start) / 2200, 1))
        const position = progress * 100, index = Math.min(Math.floor(position), 99), fraction = position - index
        const a = points[index], b = points[index + 1]
        const p: Coordinate = [a[0] + (b[0] - a[0]) * fraction, a[1] + (b[1] - a[1]) * fraction]
        const pa = map.project(a), pb = map.project(b)
        plane.setLngLat(p).setRotation(Math.atan2(pb.x - pa.x, -(pb.y - pa.y)) * 180 / Math.PI - 45)
        if (progress < 1) frame = requestAnimationFrame(animate)
        else plane.remove()
      }
      frame = requestAnimationFrame(animate)
    }
    void draw().catch(() => { if (active) setFailed(true) })
    return () => {
      active = false
      cancelAnimationFrame(frame)
      observer?.disconnect()
      markers.forEach(marker => marker.remove())
      if (mapRef.current === map) {
        if (map.getLayer('flight-route')) map.removeLayer('flight-route')
        if (map.getSource('flight-route')) map.removeSource('flight-route')
      }
    }
  }, [ready, failed, originLng, originLat, destLng, destLat, from?.iataCode, to?.iataCode])

  const unavailable = failed || (!!from && !origin) || (!!to && !destination)
  return (
    <figure className="relative flex h-[300px] min-w-0 flex-col overflow-hidden rounded-[24px] bg-[#edf2f4] shadow-[0_8px_24px_rgb(16,42,86,0.08)] lg:h-[340px]" aria-label="Selected flight route" data-map-state={unavailable ? 'fallback' : ready ? 'ready' : 'loading'}>
      <div className="relative min-h-0 flex-1">
        <div ref={container} style={{ position: 'absolute', inset: 0 }} className={`${unavailable ? 'invisible' : ''}`} aria-hidden="true" />
        {(!ready || unavailable) && <div className="absolute inset-0 flex items-center justify-center px-6 text-center text-sm text-slate-500" role="status">
          {unavailable ? 'Map unavailable · Your selected route is shown below' : 'Loading route map…'}
        </div>}
      </div>
      <figcaption className="relative h-[72px] shrink-0 border-t border-slate-200/70 bg-white/95 px-5 pb-6 pt-2">
        <p className="text-sm font-semibold text-[#102a56]">{from && to ? `${from.iataCode} → ${to.iataCode}` : 'Choose your airports'}</p>
        <p className="truncate text-xs text-slate-500">{from && to ? `${from.city} → ${to.city}` : 'Your route will appear here'}</p>
      </figcaption>
      <div className="absolute inset-x-0 bottom-1 bg-white/95 px-5 py-1 text-right text-[9px] leading-3 text-slate-500 [&_a]:underline">
        <a href="https://openfreemap.org/" target="_blank" rel="noreferrer">OpenFreeMap</a> · <a href="https://openmaptiles.org/" target="_blank" rel="noreferrer">© OpenMapTiles</a> · <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">© OpenStreetMap</a>
      </div>
    </figure>
  )
}
