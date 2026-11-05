import { apiDelete, apiGet, apiPost, apiPut, type Page } from './client'

export type AccessLevel = 'VIEW' | 'EDIT' | 'MANAGE'

export type ProfileKind = 'SELF' | 'DEPENDENT' | 'ADULT'

export type Profile = {
  id: string
  familyId: string
  fullName: string
  dateOfBirth: string | null
  dependent: boolean
  linkedToAccount: boolean
  accessLevel: AccessLevel
  version: number
}

export type Grant = {
  accountId: string
  displayName: string
  email: string
  level: AccessLevel
  linkedAccount: boolean
}

export function listProfiles(): Promise<Page<Profile>> {
  return apiGet('/profiles?size=100')
}

export function getProfile(id: string): Promise<Profile> {
  return apiGet(`/profiles/${id}`)
}

export function createProfile(
  familyId: string,
  profile: { fullName: string; dateOfBirth: string | null; kind: ProfileKind },
): Promise<Profile> {
  return apiPost(`/families/${familyId}/profiles`, profile)
}

export function updateProfile(
  id: string,
  profile: { fullName: string; dateOfBirth: string | null; version: number },
): Promise<Profile> {
  return apiPut(`/profiles/${id}`, profile)
}

export function deleteProfile(id: string): Promise<void> {
  return apiDelete(`/profiles/${id}`)
}

export function listGrants(profileId: string): Promise<Grant[]> {
  return apiGet(`/profiles/${profileId}/grants`)
}

export function putGrant(profileId: string, accountId: string, level: AccessLevel): Promise<Grant> {
  return apiPut(`/profiles/${profileId}/grants/${accountId}`, { level })
}

export function deleteGrant(profileId: string, accountId: string): Promise<void> {
  return apiDelete(`/profiles/${profileId}/grants/${accountId}`)
}
