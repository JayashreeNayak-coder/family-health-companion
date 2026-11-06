import type { RecordType } from '../../api/documents'

export type FieldConfig = {
  name: string
  label: string
  type?: 'text' | 'textarea' | 'date' | 'time' | 'tel' | 'select' | 'number'
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
  /** The heading when the title field is optional and empty. */
  fallbackTitle?: string
  fields: FieldConfig[]
  /** Health records show where they came from. Emergency contacts aren't health records. */
  hasProvenance: boolean
  /** Set when documents can be attached to these records as evidence. */
  recordType?: RecordType
  /** Optional fields that are blank in the form, but where at least one must be filled. */
  hint?: string
}

export const CONDITIONS: ResourceConfig = {
  path: 'conditions',
  recordType: 'CONDITION',
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
  recordType: 'ALLERGY',
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
  recordType: 'DIETARY_RESTRICTION',
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

export const VISITS: ResourceConfig = {
  path: 'visits',
  recordType: 'VISIT',
  title: 'Doctor visits',
  singular: 'visit',
  titleField: 'reason',
  fallbackTitle: 'Doctor visit',
  hasProvenance: true,
  fields: [
    { name: 'reason', label: 'Reason', maxLength: 500 },
    { name: 'occurredOn', label: 'Date', type: 'date' },
    { name: 'occurredTime', label: 'Time', type: 'time' },
    { name: 'doctorName', label: 'Doctor', maxLength: 200 },
    { name: 'facility', label: 'Facility', maxLength: 200 },
    { name: 'notes', label: 'Notes', type: 'textarea', maxLength: 4000 },
  ],
}

export const SYMPTOMS: ResourceConfig = {
  path: 'symptoms',
  recordType: 'SYMPTOM',
  title: 'Symptoms',
  singular: 'symptom',
  titleField: 'description',
  hasProvenance: true,
  fields: [
    { name: 'description', label: 'Symptom', required: true, maxLength: 500 },
    { name: 'severityAsReported', label: 'Severity, as reported', maxLength: 100 },
    { name: 'occurredOn', label: 'Date', type: 'date' },
    { name: 'occurredTime', label: 'Time', type: 'time' },
    { name: 'notes', label: 'Notes', type: 'textarea', maxLength: 4000 },
  ],
}

export const MEDICATIONS: ResourceConfig = {
  path: 'medications',
  recordType: 'MEDICATION',
  title: 'Medications',
  singular: 'medication',
  titleField: 'name',
  hasProvenance: true,
  fields: [
    { name: 'name', label: 'Medication', required: true, maxLength: 200 },
    { name: 'dosageAsRecorded', label: 'Dosage, as recorded', maxLength: 200 },
    {
      name: 'instructionsAsRecorded',
      label: 'Instructions, as recorded',
      type: 'textarea',
      maxLength: 1000,
    },
    { name: 'prescribedBy', label: 'Prescribed by', maxLength: 200 },
    { name: 'startOn', label: 'Start date', type: 'date' },
    { name: 'endOn', label: 'End date', type: 'date' },
    {
      name: 'status',
      label: 'Status',
      type: 'select',
      options: [
        { value: 'ACTIVE', label: 'Active' },
        { value: 'STOPPED', label: 'Stopped' },
        { value: 'UNKNOWN', label: 'Unknown' },
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

export const LAB_RESULTS: ResourceConfig = {
  path: 'lab-results',
  title: 'Lab results',
  singular: 'lab result',
  titleField: 'testName',
  hasProvenance: true,
  recordType: 'LAB_RESULT',
  hint: 'Enter the value exactly as printed. A missing unit or date stays missing, and is never guessed.',
  fields: [
    { name: 'testName', label: 'Test', required: true, maxLength: 200 },
    { name: 'valueNumeric', label: 'Value (number)', type: 'number' },
    { name: 'valueText', label: 'Value (text, such as "Negative")', maxLength: 200 },
    { name: 'unit', label: 'Unit', maxLength: 50 },
    { name: 'resultOn', label: 'Result date', type: 'date' },
    { name: 'referenceRangeAsPrinted', label: 'Reference range, as printed', maxLength: 200 },
  ],
}

export const FOOD_LOG: ResourceConfig = {
  path: 'food-log',
  title: 'Food log',
  singular: 'food entry',
  titleField: 'description',
  hasProvenance: false,
  recordType: 'FOOD_LOG',
  fields: [
    { name: 'description', label: 'What was eaten', type: 'textarea', required: true, maxLength: 1000 },
    {
      name: 'mealType',
      label: 'Meal',
      type: 'select',
      options: [
        { value: '', label: 'Not specified' },
        { value: 'BREAKFAST', label: 'Breakfast' },
        { value: 'LUNCH', label: 'Lunch' },
        { value: 'DINNER', label: 'Dinner' },
        { value: 'SNACK', label: 'Snack' },
        { value: 'OTHER', label: 'Other' },
      ],
    },
    { name: 'occurredOn', label: 'Date', type: 'date' },
    { name: 'occurredTime', label: 'Time', type: 'time' },
  ],
}
