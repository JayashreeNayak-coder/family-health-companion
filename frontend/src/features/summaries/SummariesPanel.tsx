import { Download, FileText, ScrollText, Sparkles, Trash2 } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client'
import {
  deleteSummary,
  summaryPdf,
  generateSummary,
  listSummaries,
  type HealthSummary,
  type SummarySource,
  type SummaryTopic,
} from '../../api/summaries'
import { AiBadge, SourceBadge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import { useConfirm } from '../../components/Dialog'
import { EmptyState, ErrorNote, Loading } from '../../components/Feedback'
import { inputClass } from '../../components/formStyles'
import { useToast } from '../../components/Toast'
import { shiftDate } from '../medication/MedicationsPanel'

const TOPICS: Record<SummaryTopic, string> = {
  CONDITIONS: 'Conditions',
  ALLERGIES: 'Allergies',
  MEDICATIONS: 'Medications',
  LAB_RESULTS: 'Lab results',
  VISITS: 'Visits',
  SYMPTOMS: 'Symptoms',
}

const RECORD_TYPES: Record<string, string> = {
  CONDITION: 'Condition',
  ALLERGY: 'Allergy',
  MEDICATION: 'Medication',
  LAB_RESULT: 'Lab result',
  VISIT: 'Visit',
  SYMPTOM: 'Symptom',
}

const labelClass = 'block text-sm font-medium text-slate-800'

function localToday() {
  return new Date().toLocaleDateString('en-CA')
}

export function periodText(summary: Pick<HealthSummary, 'periodFrom' | 'periodTo'>): string {
  if (summary.periodFrom && summary.periodTo) {
    return `${summary.periodFrom} to ${summary.periodTo}`
  }
  if (summary.periodFrom) {
    return `From ${summary.periodFrom}`
  }
  if (summary.periodTo) {
    return `Up to ${summary.periodTo}`
  }
  return 'All records'
}

/** The record a statement restates, so it can be checked against the record itself. */
function SourceChip({ source }: { source: SummarySource }) {
  return (
    <li className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-slate-600">
      <span className="font-medium text-slate-700">{RECORD_TYPES[source.type] ?? 'Record'}</span>
      <span>{source.date ?? 'date unknown'}</span>
      <span className="min-w-0 break-words">{source.title}</span>
      {source.sourceType && <SourceBadge sourceType={source.sourceType} />}
    </li>
  )
}

/** Fetches the PDF through the API (with the session and access check) and saves it. */
function DownloadPdf({ profileId, summary }: { profileId: string; summary: HealthSummary }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function download() {
    setBusy(true)
    setError(null)
    try {
      const url = URL.createObjectURL(await summaryPdf(profileId, summary.id))
      const link = document.createElement('a')
      link.href = url
      link.download = `health-record-summary-${summary.createdAt.slice(0, 10)}.pdf`
      link.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Button icon={Download} busy={busy} onClick={() => void download()} aria-label={`Download PDF: ${periodText(summary)}`}>
        Download PDF
      </Button>
      {error && <ErrorNote message={error} />}
    </>
  )
}

function SummaryView({
  profileId,
  summary,
  canEdit,
  onDelete,
}: {
  profileId: string
  summary: HealthSummary
  canEdit: boolean
  onDelete: () => void
}) {
  const topics = (Object.keys(TOPICS) as SummaryTopic[]).filter((topic) =>
    summary.points.some((point) => point.topic === topic),
  )
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-slate-600">
          Made {new Date(summary.createdAt).toLocaleString()} from the records at that time.
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <DownloadPdf profileId={profileId} summary={summary} />
          {canEdit && (
            <Button icon={Trash2} variant="ghost" onClick={onDelete} aria-label={`Delete summary: ${periodText(summary)}`}>
              Delete
            </Button>
          )}
        </div>
      </div>
      <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
        <p className="font-medium">{summary.disclaimer}</p>
        <ul className="mt-1 list-disc space-y-0.5 pl-5">
          {summary.limitations.map((limitation) => (
            <li key={limitation}>{limitation}</li>
          ))}
        </ul>
      </div>
      {topics.length === 0 && <p className="text-sm text-slate-600">The summary has no statements.</p>}
      {topics.map((topic) => (
        <section key={topic}>
          <h4 className="text-sm font-semibold text-slate-900">{TOPICS[topic]}</h4>
          <ul className="mt-2 space-y-3">
            {summary.points
              .filter((point) => point.topic === topic)
              .map((point, index) => (
                <li key={index} className="rounded-lg border border-slate-200 px-3 py-2">
                  <p className="text-sm text-slate-800">{point.text}</p>
                  <p className="mt-2 text-xs font-medium text-slate-500">Based on</p>
                  <ul className="mt-1 space-y-1">
                    {point.sources.map((source) => (
                      <SourceChip key={source.ref} source={source} />
                    ))}
                  </ul>
                </li>
              ))}
          </ul>
        </section>
      ))}
      {summary.conflicts.length > 0 && (
        <section>
          <h4 className="text-sm font-semibold text-slate-900">Records that disagree</h4>
          <ul className="mt-2 space-y-3">
            {summary.conflicts.map((conflict, index) => (
              <li key={index} className="rounded-lg border border-red-200 bg-red-50 px-3 py-2">
                <p className="text-sm text-red-900">{conflict.text}</p>
                <ul className="mt-2 space-y-1">
                  {conflict.sources.map((source) => (
                    <SourceChip key={source.ref} source={source} />
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        </section>
      )}
      {summary.gaps.length > 0 && (
        <section>
          <h4 className="text-sm font-semibold text-slate-900">Missing or unclear</h4>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-700">
            {summary.gaps.map((gap, index) => (
              <li key={index}>{gap}</li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

/**
 * Record-based summaries. Every statement shows the records it restates, so the user can check
 * it. Making one sends this person's records for the period to an outside AI service.
 */
export function SummariesPanel({ profileId, name, canEdit }: { profileId: string; name: string; canEdit: boolean }) {
  const toast = useToast()
  const [confirm, confirmDialog] = useConfirm()
  const [summaries, setSummaries] = useState<HealthSummary[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [from, setFrom] = useState(() => shiftDate(localToday(), -365))
  const [to, setTo] = useState(localToday)
  const [confirmed, setConfirmed] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false
    listSummaries(profileId).then(
      (page) => {
        if (!ignore) {
          setSummaries(page.items)
        }
      },
      (failure) => {
        if (!ignore) {
          setLoadError(errorMessage(failure))
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [profileId])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (from && to && from > to) {
      setError('The start date must not be after the end date.')
      return
    }
    if (!confirmed) {
      setError(`Confirm that ${name}'s records may be sent.`)
      return
    }
    setBusy(true)
    setError(null)
    try {
      const summary = await generateSummary(profileId, { periodFrom: from || null, periodTo: to || null })
      setSummaries((current) => [summary, ...(current ?? [])])
      setConfirmed(false)
      toast('Summary ready')
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  async function handleDelete(summary: HealthSummary) {
    const ok = await confirm({
      title: 'Delete this summary?',
      message: `The summary for ${periodText(summary)} will be permanently deleted. The records stay.`,
      confirmLabel: 'Delete',
      tone: 'danger',
    })
    if (!ok) {
      return
    }
    try {
      await deleteSummary(profileId, summary.id)
      setSummaries((current) => (current ?? []).filter((item) => item.id !== summary.id))
      toast('Summary deleted')
    } catch (failure) {
      setError(errorMessage(failure))
    }
  }

  return (
    <div className="space-y-6">
      <Card
        title="Summarise records"
        description="An AI-written overview that only restates what is recorded, with each point linked to its records."
      >
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className={labelClass}>
              From
              <input className={inputClass} type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
            </label>
            <label className={labelClass}>
              To
              <input className={inputClass} type="date" value={to} onChange={(event) => setTo(event.target.value)} />
            </label>
          </div>
          <p className="text-xs text-slate-500">
            Allergies and records with no date are always included. Leave both dates empty to cover everything.
          </p>
          <label className="flex items-start gap-2 rounded-lg bg-violet-50 px-3 py-2 text-sm text-violet-900">
            <input
              type="checkbox"
              className="mt-0.5 size-4 shrink-0 accent-brand-600"
              checked={confirmed}
              onChange={(event) => setConfirmed(event.target.checked)}
            />
            <span>
              I understand that {name}&rsquo;s conditions, allergies, medications, lab results, visits, and symptoms
              for this period are sent to an outside AI service. Use synthetic records only.
            </span>
          </label>
          <ErrorNote message={error} />
          <Button type="submit" variant="primary" size="md" icon={Sparkles} busy={busy}>
            {busy ? 'Summarising…' : 'Summarise records'}
          </Button>
        </form>
      </Card>

      <section aria-labelledby="summaries-heading" className="space-y-4">
        <h3 id="summaries-heading" className="text-lg font-semibold text-slate-900">
          Summaries
        </h3>
        <ErrorNote message={loadError} />
        {!summaries && !loadError && <Loading />}
        {summaries?.length === 0 && (
          <EmptyState icon={ScrollText} title="No summaries yet">
            A summary lists what is recorded for a period, with the records behind each point.
          </EmptyState>
        )}
        {summaries?.map((summary, index) => (
          <details
            key={summary.id}
            open={index === 0}
            className="group rounded-xl border border-slate-200 bg-white p-5 sm:p-6"
          >
            <summary className="flex cursor-pointer flex-wrap items-center gap-3 rounded-lg font-semibold text-slate-900">
              <FileText aria-hidden="true" className="size-4.5 text-slate-500" />
              <span>{periodText(summary)}</span>
              <AiBadge />
            </summary>
            <div className="mt-4">
              <SummaryView profileId={profileId} summary={summary} canEdit={canEdit} onDelete={() => handleDelete(summary)} />
            </div>
          </details>
        ))}
      </section>
      {confirmDialog}
    </div>
  )
}
