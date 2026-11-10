import { apiGet, apiPost, apiPut } from './client'

export type CardItemType = 'PERSON' | 'ALLERGY' | 'CONDITION' | 'MEDICATION' | 'EMERGENCY_CONTACT' | 'NOTE'

/** NONE: typed by hand. CHANGED or DELETED: the record it was copied from changed after approval. */
export type SourceStatus = 'NONE' | 'CURRENT' | 'CHANGED' | 'DELETED'

export type CardItemInput = { type: CardItemType; text: string; sourceRecordId: string | null }

export type CardItem = CardItemInput & { sourceStatus: SourceStatus }

export type EmergencyCard = {
  enabled: boolean
  linkCreatedAt: string | null
  items: CardItem[]
  /** Null until the card is first saved. */
  version: number | null
}

/** Returned only when a link is made. The link is never shown again. */
export type CardLink = { link: string; card: EmergencyCard }

export type PublicCard = { items: { type: CardItemType; text: string }[] }

export const MAX_ITEMS = 30
export const MAX_TEXT = 300

function path(profileId: string) {
  return `/profiles/${profileId}/emergency-card`
}

export function getEmergencyCard(profileId: string): Promise<EmergencyCard> {
  return apiGet(path(profileId))
}

export function saveEmergencyCard(
  profileId: string,
  version: number | null,
  items: CardItemInput[],
): Promise<EmergencyCard> {
  return apiPut(path(profileId), { version, items })
}

export function enableEmergencyCard(profileId: string): Promise<CardLink> {
  return apiPost(`${path(profileId)}/enable`)
}

export function rotateEmergencyCardLink(profileId: string): Promise<CardLink> {
  return apiPost(`${path(profileId)}/rotate`)
}

export function disableEmergencyCard(profileId: string): Promise<EmergencyCard> {
  return apiPost(`${path(profileId)}/disable`)
}

export function readPublicEmergencyCard(token: string): Promise<PublicCard> {
  return apiGet(`/public/emergency-cards/${encodeURIComponent(token)}`)
}

/** Headings in the order a card shows its lines, on both the owner's and the public page. */
export const CARD_GROUPS: { type: CardItemType; label: string }[] = [
  { type: 'PERSON', label: 'Name' },
  { type: 'ALLERGY', label: 'Allergies' },
  { type: 'CONDITION', label: 'Conditions' },
  { type: 'MEDICATION', label: 'Medications' },
  { type: 'EMERGENCY_CONTACT', label: 'Emergency contacts' },
  { type: 'NOTE', label: 'Other information' },
]
