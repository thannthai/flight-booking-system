// Next/Webpack emits the v6 worker as a versioned, same-origin asset.
export const workerUrl = new URL('maplibre-gl/dist/maplibre-gl-worker.mjs', import.meta.url).toString()
