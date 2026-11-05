export type FieldConfig = {
  name: string
  label: string
  type?: 'text' | 'textarea' | 'date' | 'time' | 'tel' | 'select'
  required?: boolean
  maxLength?: number
  options?: { value: string; label: string }[]
}

export type ResourceConfig = {
  /** The API path under the profile. */
  path: string
  title: string
  singular: string
  /** The field shown as the item's heading. */
  titleField: string
  fields: FieldConfig[]
  /** Health records show where they came from. Emergency contacts aren't health records. */
  hasProvenance: boolean
}

export const CONDITIONS: ResourceConfig = {
  path: 'conditions',
  title: 'Medical conditions',
  singular: 'condition',
  titleField: 'name',
  hasProvenance: true,
  fields: [
    { name: 'name', label: 'Condition', required: true, maxLength: 200 },
    {
      name: 'status',
      label: 'Status',
      type: 'select',
      options: [
        { value: 'UNKNOWN', label: 'Unknown' },
        { value: 'ACTIVE', label: 'Active' },
        { value: 'RESOLVED', label: 'Resolved' },
      ],
    },
    { name: 'onsetOn', label: 'Started on', type: 'date' },
    { name: 'notes', label: 'Notes', type: 'textarea', maxLength: 4000 },
  ],
}

export const ALLERGIES: ResourceConfig = {
  path: 'allergies',
  title: 'Allergies',
  singular: 'allergy',
  titleField: 'allergen',
  hasProvenance: true,
  fields: [
    { name: 'allergen', label: 'Allergen', required: true, maxLength: 200 },
    { name: 'reactionAsRecorded', label: 'Reaction, as recorded', maxLength: 500 },
    { name: 'severityAsRecorded', label: 'Severity, as recorded', maxLength: 100 },
    { name: 'notes', label: 'Notes', type: 'textarea', maxLength: 4000 },
  ],
}

export const DIETARY: ResourceConfig = {
  path: 'dietary-restrictions',
  title: 'Dietary restrictions and preferences',
  singular: 'dietary item',
  titleField: 'description',
  hasProvenance: true,
  fields: [
    { name: 'description', label: 'Description', required: true, maxLength: 500 },
    {
      name: 'kind',
      label: 'Type',
      type: 'select',
      required: true,
      options: [
        { value: 'RESTRICTION', label: 'Restriction' },
        { value: 'PREFERENCE', label: 'Preference' },
      ],
    },
  ],
}

export const CONTACTS: ResourceConfig = {
  path: 'emergency-contacts',
  title: 'Emergency contacts',
  singular: 'contact',
  titleField: 'name',
  hasProvenance: false,
  fields: [
    { name: 'name', label: 'Name', required: true, maxLength: 200 },
    { name: 'relationship', label: 'Relationship', maxLength: 100 },
    { name: 'phone', label: 'Phone', type: 'tel', required: true, maxLength: 30 },
  ],
}
