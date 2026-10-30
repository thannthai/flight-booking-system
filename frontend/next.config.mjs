import { apiProxyDestination } from './lib/deployment.mjs'

/** @type {import('next').NextConfig} */
const nextConfig = {
  images: { unoptimized: true },
  async rewrites() {
    // Keep refresh cookies first-party. Browser requests never need cross-site credentials/CORS.
    const base = apiProxyDestination(process.env.BACKEND_API_URL, process.env.NODE_ENV === 'production')
    return [{ source: '/api/v1/:path*', destination: `${base}/:path*` }]
  },
}
export default nextConfig
