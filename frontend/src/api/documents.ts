import { apiBlob, apiDelete, apiGet, apiPost, apiUpload, type Page } from './client'

export type DocumentCategory = 'LAB_REPORT' | 'PRESCRIPTION' | 'SCAN' | 'BILL' | 'PHOTO' | 'OTHER'

export const CATEGORY_LABELS: Record<DocumentCategory, string> = {
  PRESCRIPTION: 'Prescription',
  LAB_REPORT: 'Lab report',
  SCAN: 'Scan or X-ray',
  BILL: 'Bill or receipt',
  PHOTO: 'Photo',
  OTHER: 'Other',
}

export type HealthDocument = {
  id: string
  profileId: string
  originalFilename: string
  contentType: 'application/pdf' | 'image/jpeg' | 'image/png'
  sizeBytes: number
  category: DocumentCategory
  documentOn: string | null
  description: string | null
  extractionStatus: string
  uploadedAt: string
}

export type RecordType =
  | 'CONDITION'
  | 'ALLERGY'
  | 'DIETARY_RESTRICTION'
  | 'VISIT'
  | 'SYMPTOM'
  | 'MEDICATION'
  | 'LAB_RESULT'
  | 'FOOD_LOG'

export type Attachment = {
  id: string
  recordType: RecordType
  recordId: string
  document: HealthDocument
}

/** Matches the backend limit, so the user hears about it before a long upload. */
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024

export const ACCEPTED_FILES = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png'

export type UploadDetails = {
  category: DocumentCategory
  documentOn?: string | null
  description?: string | null
}

function documentsPath(profileId: string) {
  return `/profiles/${profileId}/documents`
}

export function listDocuments(profileId: string): Promise<Page<HealthDocument>> {
  return apiGet(`${documentsPath(profileId)}?size=100`)
}

export function uploadDocument(
  profileId: string,
  file: File,
  details: UploadDetails,
): Promise<HealthDocument> {
  const form = new FormData()
  form.append('file', file)
  form.append('category', details.category)
  if (details.documentOn) {
    form.append('documentOn', details.documentOn)
  }
  if (details.description) {
    form.append('description', details.description)
  }
  return apiUpload(documentsPath(profileId), form)
}

export function documentContent(profileId: string, documentId: string): Promise<Blob> {
  return apiBlob(`${documentsPath(profileId)}/${documentId}/content`)
}

export function deleteDocument(profileId: string, documentId: string): Promise<void> {
  return apiDelete(`${documentsPath(profileId)}/${documentId}`)
}

export function listAttachments(profileId: string, recordType: RecordType): Promise<Attachment[]> {
  return apiGet(`/profiles/${profileId}/attachments?recordType=${recordType}`)
}

export function attachDocument(
  profileId: string,
  recordType: RecordType,
  recordId: string,
  documentId: string,
): Promise<Attachment> {
  return apiPost(`/profiles/${profileId}/attachments`, { recordType, recordId, documentId })
}

export function detachDocument(profileId: string, attachmentId: string): Promise<void> {
  return apiDelete(`/profiles/${profileId}/attachments/${attachmentId}`)
}

export function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
