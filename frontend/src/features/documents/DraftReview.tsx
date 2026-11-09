import { Check, CircleCheck, CircleX, X } from 'lucide-react'
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
import { AiBadge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { useConfirm } from '../../components/Dialog'
import { ErrorNote, Loading } from '../../components/Feedback'
import { inputClass } from '../../components/formStyles'
import {
  ALLERGIES,
  CONDITIONS,
  LAB_RESULTS,
  MEDICATIONS,
  VISITS,
  type ResourceConfig,
} from '../records/resources'
import { DocumentView } from './DocumentView'

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
  const total = draft?.items.length ?? 0

  return (
    <section
      aria-label="AI draft review"
      className="mt-4 space-y-4 rounded-2xl border border-violet-200 bg-violet-50/50 p-4 sm:p-5"
    >
      {confirmDialog}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <AiBadge>AI generated · needs review</AiBadge>
          <h4 className="font-semibold text-slate-900">Review the extracted details</h4>
        </div>
        {draft && total > 0 && (
          <p className="text-sm text-slate-600">
            {total - pending} of {total} reviewed
          </p>
        )}
      </div>
      <ErrorNote message={error} />
      {!draft && !error && <Loading />}
      {draft && (
        <>
          <div className="rounded-xl border border-violet-100 bg-white px-4 py-3 text-sm text-slate-700">
            <p className="font-medium text-slate-900">{draft.disclaimer}</p>
            {draft.limitations.length > 0 && (
              <ul className="mt-2 list-disc space-y-1 pl-5">
                {draft.limitations.map((limitation) => (
                  <li key={limitation}>{limitation}</li>
                ))}
              </ul>
            )}
          </div>
          <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
            <div className="self-start rounded-xl border border-slate-200 bg-white p-4 lg:sticky lg:top-4">
              <p className="mb-2 text-sm font-semibold text-slate-800">Original document</p>
              <p className="mb-3 text-xs break-all text-slate-500">{document.originalFilename}</p>
              <div className="flex flex-wrap gap-2">
                <DocumentView profileId={profileId} document={document} />
              </div>
            </div>
            <div className="space-y-3">
              {draft.items.length === 0 && (
                <p className="rounded-xl bg-white p-4 text-sm text-slate-600">
                  The AI found nothing to draft from this document.
                </p>
              )}
              {draft.items.map((item) => (
                <DraftItemCard
                  key={item.id}
                  profileId={profileId}
                  documentId={document.id}
                  item={item}
                  onChanged={replaceItem}
                />
              ))}
            </div>
          </div>
          <div className="flex flex-wrap justify-end gap-2 border-t border-violet-100 pt-4">
            {pending > 0 && (
              <Button onClick={() => void discardRest()}>
                Discard the {pending} remaining {pending === 1 ? 'item' : 'items'}
              </Button>
            )}
            <Button variant={pending > 0 ? 'secondary' : 'primary'} onClick={onClose}>
              {pending > 0 ? 'Finish later' : 'Done'}
            </Button>
          </div>
        </>
      )}
    </section>
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
    const approved = item.status === 'APPROVED'
    const Icon = approved ? CircleCheck : CircleX
    return (
      <div className="flex items-start gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm">
        <Icon aria-hidden="true" className={`mt-0.5 size-5 shrink-0 ${approved ? 'text-emerald-600' : 'text-slate-400'}`} />
        <div className="min-w-0">
          <p className="font-medium break-words text-slate-900">
            {title}: {String(item.payload[resource.titleField] ?? '')}
          </p>
          <p className="text-slate-600">
            {approved ? 'Approved and saved as a record (from document).' : 'This item was discarded. Nothing from it was saved.'}
          </p>
        </div>
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
      className="rounded-xl border border-violet-200 bg-white p-4 shadow-xs"
    >
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <p className="font-semibold text-slate-900">{title}</p>
        <AiBadge>AI draft</AiBadge>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        {fields.map((field) => {
          const fieldError = fieldErrors.find((candidate) => candidate.field === field.name)
          const missing = values[field.name] === ''
          const shared = {
            className: `${inputClass} ${missing ? 'border-dashed' : ''}`,
            value: values[field.name],
            maxLength: field.maxLength,
            onChange: (event: { target: { value: string } }) =>
              setValues((current) => ({ ...current, [field.name]: event.target.value })),
          }
          return (
            <label
              key={field.name}
              className={`block text-sm font-medium text-slate-800 ${field.type === 'textarea' ? 'sm:col-span-2' : ''}`}
            >
              {field.label}
              {missing && (
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
              {fieldError && <span className="mt-1 block font-normal text-red-700">{fieldError.message}</span>}
            </label>
          )
        })}
      </div>
      <ErrorNote message={error} />
      <div className="mt-4 flex flex-wrap justify-end gap-2">
        <Button icon={X} disabled={busy} onClick={() => void reject()}>
          Discard
        </Button>
        <Button type="submit" variant="primary" icon={Check} busy={busy}>
          Approve and save
        </Button>
      </div>
    </form>
  )
}
