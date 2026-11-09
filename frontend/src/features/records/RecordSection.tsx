import { Pencil, Plus, Trash2 } from 'lucide-react'
import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { ApiError, errorMessage, type FieldError } from '../../api/client'
import { listAttachments, listDocuments, type Attachment, type HealthDocument } from '../../api/documents'
import {
  createRecord,
  deleteRecord,
  listRecords,
  updateRecord,
  type RecordItem,
} from '../../api/records'
import { SourceBadge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import { Dialog, useConfirm } from '../../components/Dialog'
import { EmptyState, ErrorNote, Loading, Missing } from '../../components/Feedback'
import { useToast } from '../../components/Toast'
import { inputClass } from '../../components/ui'
import { Attachments } from '../documents/Attachments'
import type { FieldConfig, ResourceConfig } from './resources'

type Props = {
  profileId: string
  canEdit: boolean
  resource: ResourceConfig
  /** Extra controls for one item, such as a medication's schedule. */
  renderItemExtras?: (item: RecordItem) => ReactNode
}

function emptyForm(resource: ResourceConfig): Record<string, string> {
  return Object.fromEntries(
    resource.fields.map((field) => [
      field.name,
      field.type === 'select' ? (field.options?.[0]?.value ?? '') : '',
    ]),
  )
}

function formFrom(resource: ResourceConfig, item: RecordItem): Record<string, string> {
  return Object.fromEntries(
    resource.fields.map((field) => [field.name, displayValue(item[field.name], field) ?? '']),
  )
}

/** A time arrives as 08:30. A missing value is null, and stays distinct from an empty string. */
export function displayValue(value: unknown, field: FieldConfig): string | null {
  if (value === null || value === undefined || value === '') {
    return null
  }
  return field.type === 'time' ? String(value).slice(0, 5) : String(value)
}

export function titleOf(resource: ResourceConfig, item: RecordItem): string {
  const title = item[resource.titleField]
  return title ? String(title) : (resource.fallbackTitle ?? resource.singular)
}

function optionLabel(field: FieldConfig, value: string): string {
  return field.options?.find((option) => option.value === value)?.label ?? value
}

const iconButton =
  'grid size-9 place-items-center rounded-lg text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-800'

export function RecordSection({ profileId, canEdit, resource, renderItemExtras }: Props) {
  const [items, setItems] = useState<RecordItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [editing, setEditing] = useState<RecordItem | 'new' | null>(null)
  const [confirm, confirmDialog] = useConfirm()
  const toast = useToast()
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const recordType = resource.recordType

  useEffect(() => {
    let ignore = false
    Promise.all([
      listRecords(profileId, resource.path),
      recordType ? listAttachments(profileId, recordType) : Promise.resolve([]),
    ]).then(
      ([page, attached]) => {
        if (!ignore) {
          setItems(page.items)
          setAttachments(attached)
        }
      },
      (failure) => {
        if (!ignore) {
          setError(errorMessage(failure))
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [profileId, resource.path, recordType, revision])

  function reload() {
    setEditing(null)
    setRevision((current) => current + 1)
  }

  async function handleDelete(item: RecordItem) {
    const confirmed = await confirm({
      title: `Delete this ${resource.singular}?`,
      message: `${titleOf(resource, item)} will be permanently deleted. This can't be undone.`,
      confirmLabel: 'Delete',
    })
    if (!confirmed) {
      return
    }
    setError(null)
    try {
      await deleteRecord(profileId, resource.path, item.id)
      toast('Deleted')
      reload()
    } catch (failure) {
      setError(errorMessage(failure))
    }
  }

  const addButton = canEdit && (
    <Button icon={Plus} onClick={() => setEditing('new')}>
      Add {resource.singular}
    </Button>
  )

  return (
    <Card title={resource.title} actions={addButton}>
      {confirmDialog}
      <ErrorNote message={error} />
      {!items && !error && <Loading />}
      {items && items.length === 0 && (
        <EmptyState title="None recorded">
          This doesn't mean there are none, only that nothing has been entered.
        </EmptyState>
      )}
      {items && items.length > 0 && (
        <ul className="divide-y divide-slate-100">
          {items.map((item) => {
            const title = titleOf(resource, item)
            return (
              <li key={item.id} className="py-4 first:pt-0 last:pb-0">
                <div className="flex items-start justify-between gap-3">
                  <div className="flex min-w-0 flex-wrap items-center gap-2">
                    <h4 className="font-semibold break-words text-slate-900">{title}</h4>
                    {resource.hasProvenance && (
                      <SourceBadge
                        sourceType={item.sourceType}
                        sourceDocumentDeletedAt={item.sourceDocumentDeletedAt}
                      />
                    )}
                  </div>
                  {canEdit && (
                    <div className="-mt-1.5 flex shrink-0 gap-1">
                      <button
                        type="button"
                        aria-label={`Edit ${title}`}
                        title="Edit"
                        onClick={() => setEditing(item)}
                        className={iconButton}
                      >
                        <Pencil aria-hidden="true" className="size-4" />
                      </button>
                      <button
                        type="button"
                        aria-label={`Delete ${title}`}
                        title="Delete"
                        onClick={() => void handleDelete(item)}
                        className={`${iconButton} hover:bg-red-50 hover:text-red-700`}
                      >
                        <Trash2 aria-hidden="true" className="size-4" />
                      </button>
                    </div>
                  )}
                </div>
                <dl className="mt-2 grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2 lg:grid-cols-3">
                  {resource.fields
                    .filter((field) => field.name !== resource.titleField)
                    .map((field) => {
                      const value = displayValue(item[field.name], field)
                      return (
                        <div
                          key={field.name}
                          className={field.type === 'textarea' ? 'sm:col-span-2 lg:col-span-3' : undefined}
                        >
                          <dt className="text-xs font-medium text-slate-500">{field.label}</dt>
                          <dd className="mt-0.5 break-words whitespace-pre-line text-slate-800">
                            {value === null ? (
                              <Missing />
                            ) : field.type === 'select' ? (
                              optionLabel(field, value)
                            ) : (
                              value
                            )}
                          </dd>
                        </div>
                      )
                    })}
                </dl>
                {recordType && (
                  <Attachments
                    profileId={profileId}
                    recordType={recordType}
                    recordId={item.id}
                    recordTitle={title}
                    attachments={attachments.filter((attachment) => attachment.recordId === item.id)}
                    canEdit={canEdit}
                    onChanged={() => setRevision((current) => current + 1)}
                  />
                )}
                {renderItemExtras?.(item)}
              </li>
            )
          })}
        </ul>
      )}
      <Dialog
        open={canEdit && editing !== null}
        title={
          editing === 'new'
            ? `Add ${resource.singular}`
            : `Edit ${editing ? titleOf(resource, editing) : resource.singular}`
        }
        onClose={() => setEditing(null)}
      >
        {editing !== null && (
          <RecordForm
            key={editing === 'new' ? 'new' : editing.id}
            profileId={profileId}
            resource={resource}
            item={editing === 'new' ? null : editing}
            onSaved={() => {
              toast('Saved')
              reload()
            }}
            onCancel={() => setEditing(null)}
          />
        )}
      </Dialog>
    </Card>
  )
}

function RecordForm({
  profileId,
  resource,
  item,
  onSaved,
  onCancel,
}: {
  profileId: string
  resource: ResourceConfig
  item: RecordItem | null
  onSaved: () => void
  onCancel: () => void
}) {
  const [values, setValues] = useState(() => (item ? formFrom(resource, item) : emptyForm(resource)))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldError[]>([])
  const [sourceDocumentId, setSourceDocumentId] = useState('')
  const [documents, setDocuments] = useState<HealthDocument[]>([])
  // Provenance is set when a record is created, and can't change afterwards.
  const offersSource = resource.hasProvenance && item === null

  useEffect(() => {
    if (!offersSource) {
      return
    }
    let ignore = false
    // Without the list, the record is simply user reported, so a failure here needs no message.
    listDocuments(profileId).then(
      (page) => {
        if (!ignore) {
          setDocuments(page.items)
        }
      },
      () => {},
    )
    return () => {
      ignore = true
    }
  }, [profileId, offersSource])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setFieldErrors([])
    // A blank optional field is unknown, so it's sent as null rather than an empty string.
    const body: Record<string, unknown> = Object.fromEntries(
      Object.entries(values).map(([name, value]) => {
        const field = resource.fields.find((candidate) => candidate.name === name)
        if (value === '') {
          return [name, null]
        }
        return [name, field?.type === 'number' ? Number(value) : value]
      }),
    )
    if (offersSource && sourceDocumentId) {
      body.sourceDocumentId = sourceDocumentId
    }
    try {
      if (item) {
        await updateRecord(profileId, resource.path, item.id, { ...body, version: item.version })
      } else {
        await createRecord(profileId, resource.path, body)
      }
      onSaved()
    } catch (failure) {
      if (failure instanceof ApiError && failure.fieldErrors.length > 0) {
        setFieldErrors(failure.fieldErrors)
      } else {
        setError(errorMessage(failure))
      }
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {resource.hint && <p className="text-sm text-slate-600">{resource.hint}</p>}
      {resource.fields.map((field) => {
        const shared = {
          className: inputClass,
          value: values[field.name],
          required: field.required,
          maxLength: field.maxLength,
          onChange: (event: { target: { value: string } }) =>
            setValues((current) => ({ ...current, [field.name]: event.target.value })),
        }
        const fieldError = fieldErrors.find((candidate) => candidate.field === field.name)
        return (
          <label key={field.name} className="block text-sm font-medium text-slate-800">
            {field.label}
            {!field.required && field.type !== 'select' && ' (optional)'}
            {field.type === 'textarea' ? (
              <textarea rows={3} {...shared} />
            ) : field.type === 'select' ? (
              <select
                className={shared.className}
                value={shared.value}
                required={shared.required}
                onChange={shared.onChange}
              >
                {field.options?.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            ) : (
              <input
                type={field.type ?? 'text'}
                step={field.type === 'number' ? 'any' : undefined}
                {...shared}
              />
            )}
            {fieldError && <span className="mt-1 block font-normal text-red-700">{fieldError.message}</span>}
          </label>
        )
      })}
      {offersSource && documents.length > 0 && (
        <label className="block text-sm font-medium text-slate-800">
          Source
          <select
            className={inputClass}
            value={sourceDocumentId}
            onChange={(event) => setSourceDocumentId(event.target.value)}
          >
            <option value="">Entered by me (user reported)</option>
            {documents.map((document) => (
              <option key={document.id} value={document.id}>
                From document: {document.description ?? document.originalFilename}
              </option>
            ))}
          </select>
        </label>
      )}
      <ErrorNote message={error} />
      <div className="flex flex-wrap justify-end gap-2 pt-2">
        <Button size="md" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" size="md" busy={busy}>
          Save
        </Button>
      </div>
    </form>
  )
}
