import type { PageResponse } from '@/types/api'
export async function fetchAllPages<T>(path: string, get: <R>(path: string) => Promise<R>): Promise<T[]> {
  const items: T[] = []
  for (let page = 0; page < 100; page++) {
    const response = await get<PageResponse<T>>(`${path}${path.includes('?') ? '&' : '?'}page=${page}&size=100`)
    items.push(...response.content)
    if (response.last || page + 1 >= response.totalPages) return items
  }
  throw new Error('Too many results. Please narrow your search.')
}
