import { apiGet } from './client'
import type { SourceType } from './records'

export type TimelineType = 'CONDITION' | 'VISIT' | 'SYMPTOM' | 'MEDICATION' | 'LAB_RESULT'

export type TimelineEntry = {
  type: TimelineType
  id: string
  date: string | null
  time: string | null
  title: string
  detail: string | null
  sourceType: SourceType
  sourceDocumentId: string | null
  sourceDocumentDeletedAt: string | null
}

export type Timeline = { entries: TimelineEntry[]; dateUnknown: TimelineEntry[] }

export function getTimeline(
  profileId: string,
  filter: { from: string; to: string; types: TimelineType[] },
): Promise<Timeline> {
  const query = new URLSearchParams()
  if (filter.from) {
    query.set('from', filter.from)
  }
  if (filter.to) {
    query.set('to', filter.to)
  }
  if (filter.types.length > 0) {
    query.set('types', filter.types.join(','))
  }
  const suffix = query.size > 0 ? `?${query.toString()}` : ''
  return apiGet(`/profiles/${profileId}/timeline${suffix}`)
}
