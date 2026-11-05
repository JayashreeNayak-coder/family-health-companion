import { apiDelete, apiGet, apiPost, apiPut, type Page } from './client'

export type SourceType = 'USER_REPORTED' | 'DOCUMENTED'

/** A profile-owned record. Its fields depend on the resource. */
export type RecordItem = {
  id: string
  version: number
  sourceType?: SourceType
  sourceDocumentId?: string | null
  sourceDocumentDeletedAt?: string | null
  [field: string]: unknown
}

function collection(profileId: string, resource: string) {
  return `/profiles/${profileId}/${resource}`
}

export function listRecords(profileId: string, resource: string): Promise<Page<RecordItem>> {
  return apiGet(`${collection(profileId, resource)}?size=100`)
}

export function createRecord(
  profileId: string,
  resource: string,
  body: Record<string, unknown>,
): Promise<RecordItem> {
  return apiPost(collection(profileId, resource), body)
}

export function updateRecord(
  profileId: string,
  resource: string,
  id: string,
  body: Record<string, unknown>,
): Promise<RecordItem> {
  return apiPut(`${collection(profileId, resource)}/${id}`, body)
}

export function deleteRecord(profileId: string, resource: string, id: string): Promise<void> {
  return apiDelete(`${collection(profileId, resource)}/${id}`)
}
