import { apiGet, apiPost, type Page } from './client'

export type FamilyRole = 'MANAGER' | 'MEMBER'

export type FamilySummary = { id: string; name: string; role: FamilyRole }

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
