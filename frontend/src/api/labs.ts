import { apiGet } from './client'
import type { SourceType } from './records'

export type TrendSeries = { testKey: string; testName: string; unit: string | null; count: number }

export type TrendPoint = { id: string; resultOn: string; value: number; sourceType: SourceType }

export type Trend = {
  testKey: string
  unit: string | null
  points: TrendPoint[]
  excludedCount: number
}

export function listTrendSeries(profileId: string): Promise<TrendSeries[]> {
  return apiGet(`/profiles/${profileId}/lab-results/trend-series`)
}

export function getTrend(profileId: string, testKey: string, unit: string | null): Promise<Trend> {
  const query = new URLSearchParams({ testKey })
  if (unit !== null) {
    query.set('unit', unit)
  }
  return apiGet(`/profiles/${profileId}/lab-results/trends?${query.toString()}`)
}
