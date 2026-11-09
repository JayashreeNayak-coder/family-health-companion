import { useEffect, useState, type FormEvent } from 'react'
import { ApiError, errorMessage, type FieldError } from '../../api/client'
import type { HealthDocument } from '../../api/documents'
import {
  approveItem,
  getDraft,
  rejectItem,
  rejectRemaining,
  type Draft,
  type DraftItem,
  type DraftItemType,
} from '../../api/extraction'
import {
  ErrorNote,
  inputClass,
  primaryButtonClass,
  secondaryButtonClass,
} from '../../components/ui'
import {
  ALLERGIES,
  CONDITIONS,
  LAB_RESULTS,
  MEDICATIONS,
  VISITS,
  type ResourceConfig,
} from '../records/resources'
import { DocumentView } from './DocumentView'
import { useConfirm } from '../../components/Dialog'
import { AiBadge } from '../../components/Badge'

const RESOURCES: Record<DraftItemType, ResourceConfig> = {
  MEDICATION: MEDICATIONS,
  LAB_RESULT: LAB_RESULTS,
  CONDITION: CONDITIONS,
  ALLERGY: ALLERGIES,
  VISIT: VISITS,
}

type Props = {
  profileId: string
  document: HealthDocument
  onClose: () => void
}

/**
 * The AI's draft, shown beside the original. Every value can be corrected, and each item becomes
 * a record only when approved. Nothing here is saved by the AI itself.
 */
export function DraftReview({ profileId, document, onClose }: Props) {
  const [draft, setDraft] = useState<Draft | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [confirm, confirmDialog] = useConfirm()

  useEffect(() => {
    let ignore = false
    getDraft(profileId, document.id).then(
      (found) => {
        if (!ignore) {
          setDraft(found)
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
  }, [profileId, document.id])

  function replaceItem(updated: DraftItem) {
    setDraft((current) =>
      current
        ? { ...current, items: current.items.map((item) => (item.id === updated.id ? updated : item)) }
        : current,
    )
  }

  async function discardRest() {
    const confirmed = await confirm({
      title: 'Discard the remaining items?',
      message: 'Every item not yet approved will be discarded, and nothing from them will be saved. Approved items stay saved.',
      confirmLabel: 'Discard items',
    })
    if (!confirmed) {
      return
    }
    setError(null)
    try {
      setDraft(await rejectRemaining(profileId, document.id))
    } catch (failure) {
      setError(errorMessage(failure))
    }
  }

  const pending = draft?.items.filter((item) => item.status === 'PENDING').length ?? 0

  return (
    <div className="mt-3 space-y-3 rounded-xl border border-violet-200 bg-violet-50/60 p-4">
      {confirmDialog}
      <div className="flex flex-wrap items-center gap-2">
        <AiBadge>AI generated · needs review</AiBadge>
        <h4 className="font-semibold">Review the extracted details</h4>
      </div>
      <ErrorNote message={error} />
      {!draft && !error && <p role="status">Loading…</p>}
      {draft && (
        <>
          <p className="text-sm">{draft.disclaimer}</p>
          <ul className="list-disc pl-5 text-sm text-slate-700">
            {draft.limitations.map((limitation) => (
              <li key={limitation}>{limitation}</li>
            ))}
          </ul>
          <div className="rounded bg-white p-2">
            <p className="text-sm font-medium">Original document</p>
            <DocumentView profileId={profileId} document={document} />
          </div>
          {draft.items.map((item) => (
            <DraftItemCard
              key={item.id}
              profileId={profileId}
              documentId={document.id}
              item={item}
              onChanged={replaceItem}
            />
          ))}
          <div className="flex flex-wrap gap-2">
            {pending > 0 && (
              <button type="button" className={secondaryButtonClass} onClick={() => void discardRest()}>
                Discard the {pending} remaining {pending === 1 ? 'item' : 'items'}
              </button>
            )}
            <button type="button" className={secondaryButtonClass} onClick={onClose}>
              {pending > 0 ? 'Finish later' : 'Done'}
            </button>
          </div>
        </>
      )}
    </div>
  )
}

function DraftItemCard({
  profileId,
  documentId,
  item,
  onChanged,
}: {
  profileId: string
  documentId: string
  item: DraftItem
  onChanged: (item: DraftItem) => void
}) {
  const resource = RESOURCES[item.type]
  const fields = resource.fields.filter((field) => field.name in item.payload)
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(
      fields.map((field) => {
        const value = item.payload[field.name]
        return [field.name, value === null || value === undefined ? '' : String(value)]
      }),
    ),
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldError[]>([])

  const title = `${resource.singular[0].toUpperCase()}${resource.singular.slice(1)}`

  if (item.status !== 'PENDING') {
    return (
      <div className="rounded bg-white p-3 text-sm">
        <strong>{title}:</strong> {String(item.payload[resource.titleField] ?? '')} —{' '}
        {item.status === 'APPROVED' ? 'saved as a record (from document)' : 'discarded'}
      </div>
    )
  }

  async function approve(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setFieldErrors([])
    const payload = Object.fromEntries(
      fields.map((field) => {
        const value = values[field.name]
        if (value === '') {
          return [field.name, null]
        }
        return [field.name, field.type === 'number' ? Number(value) : value]
      }),
    )
    try {
      onChanged(await approveItem(profileId, documentId, item, payload))
    } catch (failure) {
      if (failure instanceof ApiError && failure.fieldErrors.length > 0) {
        setFieldErrors(failure.fieldErrors)
      } else {
        setError(errorMessage(failure))
      }
      setBusy(false)
    }
  }

  async function reject() {
    setBusy(true)
    setError(null)
    try {
      onChanged(await rejectItem(profileId, documentId, item))
    } catch (failure) {
      setError(errorMessage(failure))
      setBusy(false)
    }
  }

  return (
    <form
      onSubmit={approve}
      aria-label={`Draft ${resource.singular}`}
      className="space-y-2 rounded bg-white p-3"
    >
      <p className="font-medium">{title}</p>
      {fields.map((field) => {
        const fieldError = fieldErrors.find((candidate) => candidate.field === field.name)
        const shared = {
          className: inputClass,
          value: values[field.name],
          maxLength: field.maxLength,
          onChange: (event: { target: { value: string } }) =>
            setValues((current) => ({ ...current, [field.name]: event.target.value })),
        }
        return (
          <label key={field.name} className="block text-sm font-medium">
            {field.label}
            {values[field.name] === '' && (
              <span className="ml-1 font-normal text-slate-500 italic">(not found in the document)</span>
            )}
            {field.type === 'textarea' ? (
              <textarea rows={2} {...shared} />
            ) : (
              <input
                type={field.type === 'select' ? 'text' : (field.type ?? 'text')}
                step={field.type === 'number' ? 'any' : undefined}
                {...shared}
              />
            )}
            {fieldError && <span className="block text-red-800">{fieldError.message}</span>}
          </label>
        )
      })}
      <ErrorNote message={error} />
      <div className="flex flex-wrap gap-2">
        <button type="submit" disabled={busy} className={primaryButtonClass}>
          Approve and save
        </button>
        <button type="button" disabled={busy} className={secondaryButtonClass} onClick={() => void reject()}>
          Discard
        </button>
      </div>
    </form>
  )
}
