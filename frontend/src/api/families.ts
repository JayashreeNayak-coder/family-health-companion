import { apiDelete, apiGet, apiPost, apiPut, apiUpload, type Page } from './client'

export type FamilyRole = 'MANAGER' | 'MEMBER'

export type FamilySummary = {
  id: string
  name: string
  role: FamilyRole
  /** Null without a photo. Changes whenever the photo does. */
  photoUpdatedAt: string | null
  version: number
}

export type FamilyMember = {
  accountId: string
  displayName: string
  email: string
  role: FamilyRole
}

export type Family = FamilySummary & { members: FamilyMember[] }

export type InvitationCreated = { id: string; email: string; expiresAt: string; token: string }

export type InvitationPreview = {
  familyName: string
  role: FamilyRole
  profileName: string | null
}

export type KeepAccess = 'NONE' | 'VIEW' | 'EDIT'

export function listFamilies(): Promise<Page<FamilySummary>> {
  return apiGet('/families?size=100')
}

export function getFamily(id: string): Promise<Family> {
  return apiGet(`/families/${id}`)
}

export function createFamily(name: string): Promise<FamilySummary> {
  return apiPost('/families', { name })
}

/** Managers only. Names are unique among the families each member belongs to. */
export function renameFamily(id: string, name: string, version: number): Promise<FamilySummary> {
  return apiPut(`/families/${id}`, { name, version })
}

/** Managers only, and only when no other account is in the family. Deletes every profile in it. */
export function deleteFamily(id: string): Promise<void> {
  return apiDelete(`/families/${id}`)
}

export function setFamilyPhoto(id: string, file: File): Promise<FamilySummary> {
  const form = new FormData()
  form.append('file', file)
  return apiUpload(`/families/${id}/photo`, form, 'PUT')
}

export function removeFamilyPhoto(id: string): Promise<FamilySummary> {
  return apiDelete<FamilySummary>(`/families/${id}/photo`)
}

/** The photo's address, changing with the photo so the browser never shows an old one. */
export function familyPhotoUrl(family: Pick<FamilySummary, 'id' | 'photoUpdatedAt'>): string | null {
  return family.photoUpdatedAt
    ? `/api/v1/families/${family.id}/photo?v=${encodeURIComponent(family.photoUpdatedAt)}`
    : null
}

export function createInvitation(
  familyId: string,
  invitation: { email: string; role: FamilyRole; profileId?: string },
): Promise<InvitationCreated> {
  return apiPost(`/families/${familyId}/invitations`, invitation)
}

export function previewInvitation(token: string): Promise<InvitationPreview> {
  return apiGet(`/families/invitations/${encodeURIComponent(token)}`)
}

export function acceptInvitation(token: string, keepAccess?: KeepAccess): Promise<FamilySummary> {
  return apiPost(`/families/invitations/${encodeURIComponent(token)}/accept`, { keepAccess })
}
