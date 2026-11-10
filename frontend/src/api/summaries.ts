import { apiBlob, apiDelete, apiGet, apiPost, type Page } from './client'
import type { SourceType } from './records'

export type SummaryTopic = 'CONDITIONS' | 'ALLERGIES' | 'MEDICATIONS' | 'LAB_RESULTS' | 'VISITS' | 'SYMPTOMS'

/** A cited record, as it was when the summary was made. */
export type SummarySource = {
  ref: string
  type: string
  id: string
  date: string | null
  title: string
  detail: string | null
  sourceType: SourceType | null
}

export type HealthSummary = {
  id: string
  profileId: string
  contentType: 'AI_GENERATED'
  promptVersion: string
  model: string
  periodFrom: string | null
  periodTo: string | null
  createdAt: string
  points: { topic: SummaryTopic; text: string; sources: SummarySource[] }[]
  gaps: string[]
  conflicts: { text: string; sources: SummarySource[] }[]
  limitations: string[]
  disclaimer: string
}

export function listSummaries(profileId: string): Promise<Page<HealthSummary>> {
  return apiGet(`/profiles/${profileId}/summaries?size=20`)
}

export function generateSummary(
  profileId: string,
  period: { periodFrom: string | null; periodTo: string | null },
): Promise<HealthSummary> {
  return apiPost(`/profiles/${profileId}/summaries`, period)
}

export function deleteSummary(profileId: string, summaryId: string): Promise<void> {
  return apiDelete(`/profiles/${profileId}/summaries/${summaryId}`)
}

/** The summary as a labelled PDF (Phase 18). Every export is recorded in the audit log. */
export function summaryPdf(profileId: string, summaryId: string): Promise<Blob> {
  return apiBlob(`/profiles/${profileId}/summaries/${summaryId}/pdf`)
}
