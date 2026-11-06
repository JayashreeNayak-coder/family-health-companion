import { apiGet, apiPost } from './client'

export type DraftItemType = 'MEDICATION' | 'LAB_RESULT' | 'CONDITION' | 'ALLERGY' | 'VISIT'

export type ExtractionJob = {
  id: string
  documentId: string
  status: 'QUEUED' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED'
  errorCode: string | null
  attempts: number
  requestedAt: string
  finishedAt: string | null
}

export type DraftItem = {
  id: string
  type: DraftItemType
  payload: Record<string, unknown>
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  confirmedRecordId: string | null
  version: number
}

export type Draft = {
  id: string
  documentId: string
  status: 'PENDING_REVIEW' | 'COMPLETED' | 'REJECTED'
  schemaVersion: string
  contentType: 'AI_GENERATED'
  disclaimer: string
  limitations: string[]
  items: DraftItem[]
}

function documentPath(profileId: string, documentId: string) {
  return `/profiles/${profileId}/documents/${documentId}`
}

export function requestExtraction(profileId: string, documentId: string): Promise<ExtractionJob> {
  return apiPost(`${documentPath(profileId, documentId)}/extraction`)
}

export function getExtractionJob(profileId: string, documentId: string): Promise<ExtractionJob> {
  return apiGet(`${documentPath(profileId, documentId)}/extraction`)
}

export function getDraft(profileId: string, documentId: string): Promise<Draft> {
  return apiGet(`${documentPath(profileId, documentId)}/draft`)
}

export function approveItem(
  profileId: string,
  documentId: string,
  item: DraftItem,
  payload: Record<string, unknown>,
): Promise<DraftItem> {
  return apiPost(`${documentPath(profileId, documentId)}/draft/items/${item.id}/approve`, {
    payload,
    version: item.version,
  })
}

export function rejectItem(profileId: string, documentId: string, item: DraftItem): Promise<DraftItem> {
  return apiPost(`${documentPath(profileId, documentId)}/draft/items/${item.id}/reject`, {
    version: item.version,
  })
}

export function rejectRemaining(profileId: string, documentId: string): Promise<Draft> {
  return apiPost(`${documentPath(profileId, documentId)}/draft/reject`)
}

/** What a failed job's code means to the user. Manual entry always remains possible. */
export function failureMessage(code: string | null): string {
  switch (code) {
    case 'UNREADABLE':
      return "The AI couldn't read this as a health document."
    case 'NOTHING_FOUND':
      return 'The AI found nothing to extract.'
    case 'RATE_LIMITED':
      return 'The AI service is busy or its free limit is used up. Try again later.'
    case 'MALFORMED_RESPONSE':
      return 'The AI answered in an unexpected form, so its answer was discarded.'
    case 'BLOCKED':
      return 'The AI service declined to read this document.'
    default:
      return 'The AI service was unavailable.'
  }
}
