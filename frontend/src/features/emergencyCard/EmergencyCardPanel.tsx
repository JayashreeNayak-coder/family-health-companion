import { Copy, ExternalLink, Plus, RefreshCw, ShieldAlert, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useId, useMemo, useState, type ReactNode } from 'react'
import { errorMessage } from '../../api/client'
import {
  CARD_GROUPS,
  MAX_ITEMS,
  MAX_TEXT,
  disableEmergencyCard,
  enableEmergencyCard,
  getEmergencyCard,
  rotateEmergencyCardLink,
  saveEmergencyCard,
  type CardItemInput,
  type EmergencyCard,
  type SourceStatus,
} from '../../api/emergencyCard'
import { listRecords, type RecordItem } from '../../api/records'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import { useConfirm } from '../../components/Dialog'
import { ErrorNote, Loading } from '../../components/Feedback'
import { inputClass } from '../../components/formStyles'
import { useToast } from '../../components/Toast'
import { QrCode } from './QrCode'

type SourcedType = 'ALLERGY' | 'CONDITION' | 'MEDICATION' | 'EMERGENCY_CONTACT'

const SOURCES: { type: SourcedType; resource: string; label: string; empty: string }[] = [
  { type: 'ALLERGY', resource: 'allergies', label: 'Allergies', empty: 'No allergies recorded.' },
  { type: 'CONDITION', resource: 'conditions', label: 'Conditions', empty: 'No conditions recorded.' },
  { type: 'MEDICATION', resource: 'medications', label: 'Medications', empty: 'No medications recorded.' },
  {
    type: 'EMERGENCY_CONTACT',
    resource: 'emergency-contacts',
    label: 'Emergency contacts',
    empty: 'No emergency contacts recorded.',
  },
]

type Line = CardItemInput & { key: string; sourceStatus: SourceStatus }

let nextKey = 0
function key() {
  nextKey += 1
  return `line-${nextKey}`
}

function text(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

/** A suggested line for a record. The user can edit it before saving; only saved text is shown. */
export function suggestedText(type: SourcedType, record: RecordItem): string {
  let line: string
  switch (type) {
    case 'ALLERGY':
      line = [text(record.allergen), text(record.reactionAsRecorded)].filter(Boolean).join(': ')
      break
    case 'CONDITION':
      line = text(record.name)
      break
    case 'MEDICATION':
      line = [text(record.name), text(record.dosageAsRecorded)].filter(Boolean).join(', ')
      break
    case 'EMERGENCY_CONTACT': {
      const relationship = text(record.relationship)
      line = [text(record.name) + (relationship ? ` (${relationship})` : ''), text(record.phone)]
        .filter(Boolean)
        .join(': ')
      break
    }
  }
  return line.slice(0, MAX_TEXT)
}

function toLines(card: EmergencyCard): Line[] {
  return card.items.map((item) => ({ ...item, key: key() }))
}

/** Saved order: grouped as the card shows them, keeping the order within each group. */
function ordered(lines: Line[]): CardItemInput[] {
  return CARD_GROUPS.flatMap((group) =>
    lines
      .filter((line) => line.type === group.type)
      .map(({ type, text: lineText, sourceRecordId }) => ({ type, text: lineText.trim(), sourceRecordId })),
  )
}

function sameItems(a: CardItemInput[], b: CardItemInput[]) {
  return JSON.stringify(a) === JSON.stringify(b)
}

/**
 * The owner's emergency card (Phase 17, docs/06 section 6). Managers only. Each line is text the
 * owner approves; lines copied from a record are flagged when that record later changes.
 */
export function EmergencyCardPanel({ profileId }: { profileId: string }) {
  const [card, setCard] = useState<EmergencyCard | null>(null)
  const [lines, setLines] = useState<Line[]>([])
  const [records, setRecords] = useState<Record<SourcedType, RecordItem[]> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [link, setLink] = useState<string | null>(null)
  const [confirm, confirmDialog] = useConfirm()
  const toast = useToast()

  const load = useCallback(() => {
    return Promise.all([
      getEmergencyCard(profileId),
      Promise.all(SOURCES.map((source) => listRecords(profileId, source.resource))),
    ]).then(
      ([loaded, lists]) => {
        setCard(loaded)
        setLines(toLines(loaded))
        setRecords(
          Object.fromEntries(SOURCES.map((source, index) => [source.type, lists[index].items])) as Record<
            SourcedType,
            RecordItem[]
          >,
        )
      },
      (failure) => setError(errorMessage(failure)),
    )
  }, [profileId])

  useEffect(() => {
    void load()
  }, [load])

  const draft = useMemo(() => ordered(lines), [lines])
  const dirty = card !== null && !sameItems(draft, ordered(toLines(card)))
  const invalid = draft.some((item) => !item.text) || draft.length > MAX_ITEMS

  if (!card || !records) {
    return error ? <ErrorNote message={error} /> : <Loading lines={4} />
  }

  function update(lineKey: string, value: string) {
    setLines((current) => current.map((line) => (line.key === lineKey ? { ...line, text: value } : line)))
  }

  function remove(lineKey: string) {
    setLines((current) => current.filter((line) => line.key !== lineKey))
  }

  function toggleRecord(type: SourcedType, record: RecordItem) {
    setLines((current) =>
      current.some((line) => line.sourceRecordId === record.id)
        ? current.filter((line) => line.sourceRecordId !== record.id)
        : [
            ...current,
            { key: key(), type, text: suggestedText(type, record), sourceRecordId: record.id, sourceStatus: 'CURRENT' },
          ],
    )
  }

  function setName(value: string) {
    setLines((current) => {
      const others = current.filter((line) => line.type !== 'PERSON')
      return value
        ? [{ key: 'person', type: 'PERSON', text: value, sourceRecordId: null, sourceStatus: 'NONE' }, ...others]
        : others
    })
  }

  async function run<T>(action: string, work: () => Promise<T>): Promise<T | undefined> {
    setBusy(action)
    setError(null)
    try {
      return await work()
    } catch (failure) {
      setError(errorMessage(failure))
      return undefined
    } finally {
      setBusy(null)
    }
  }

  async function save() {
    const saved = await run('save', () => saveEmergencyCard(profileId, card!.version, draft))
    if (saved) {
      setCard(saved)
      setLines(toLines(saved))
      toast('Emergency card saved')
    }
  }

  async function turnOn() {
    const ok = await confirm({
      title: 'Turn on the emergency card?',
      message: (
        <>
          Anyone with the link or QR code will be able to see the {card!.items.length} saved{' '}
          {card!.items.length === 1 ? 'line' : 'lines'} on this card, without signing in. They can't see anything
          else in this profile. You can turn the card off or replace the link at any time.
        </>
      ),
      confirmLabel: 'Turn on',
      tone: 'primary',
    })
    if (!ok) {
      return
    }
    const made = await run('enable', () => enableEmergencyCard(profileId))
    if (made) {
      setCard(made.card)
      setLink(made.link)
    }
  }

  async function newLink() {
    const ok = await confirm({
      title: 'Make a new link?',
      message: 'The current link and QR code stop working straight away. Anyone who has them will need the new one.',
      confirmLabel: 'Make a new link',
      tone: 'danger',
    })
    if (!ok) {
      return
    }
    const made = await run('rotate', () => rotateEmergencyCardLink(profileId))
    if (made) {
      setCard(made.card)
      setLink(made.link)
    }
  }

  async function turnOff() {
    const ok = await confirm({
      title: 'Turn off the emergency card?',
      message: 'The link and QR code stop working straight away. Turning the card on again makes a new link.',
      confirmLabel: 'Turn off',
      tone: 'danger',
    })
    if (!ok) {
      return
    }
    const off = await run('disable', () => disableEmergencyCard(profileId))
    if (off) {
      setCard(off)
      setLink(null)
      toast('Emergency card turned off')
    }
  }

  const name = lines.find((line) => line.type === 'PERSON')?.text ?? ''
  const notes = lines.filter((line) => line.type === 'NOTE')
  const savedCount = card.items.length

  return (
    <div className="space-y-6">
      {confirmDialog}

      <Card
        title="Emergency card"
        description="A short card for someone helping in an emergency, such as a first aider. It shows only the lines you choose below."
        actions={card.enabled ? <Badge tone="success">On</Badge> : <Badge>Off</Badge>}
      >
        {card.enabled ? (
          <p className="text-sm text-slate-700">
            Anyone with the link or QR code can see the saved lines, without signing in.
            {card.linkCreatedAt && <> The current link was made on {new Date(card.linkCreatedAt).toLocaleDateString()}.</>}
          </p>
        ) : (
          <p className="text-sm text-slate-700">The card is off. Nobody can see it.</p>
        )}

        {link && <LinkBox link={link} />}

        <div className="mt-4 flex flex-wrap gap-2">
          {card.enabled ? (
            <>
              <Button icon={RefreshCw} busy={busy === 'rotate'} onClick={() => void newLink()}>
                Make a new link
              </Button>
              <Button variant="danger" busy={busy === 'disable'} onClick={() => void turnOff()}>
                Turn off
              </Button>
            </>
          ) : (
            <Button
              variant="primary"
              busy={busy === 'enable'}
              disabled={dirty || savedCount === 0}
              onClick={() => void turnOn()}
            >
              Turn on
            </Button>
          )}
        </div>
        {!card.enabled && (dirty || savedCount === 0) && (
          <p className="mt-2 text-sm text-slate-600">
            {savedCount === 0 ? 'Choose and save at least one line first.' : 'Save your changes first.'}
          </p>
        )}
        <ErrorNote message={error} />
      </Card>

      <Card
        title="What the card shows"
        description="Lines are copied from your records when you tick them, and you can shorten or reword them. The card keeps the saved text even if a record changes later."
      >
        <div className="space-y-6">
          <NameField value={name} onChange={setName} />

          {SOURCES.map((source) => (
            <SourceGroup
              key={source.type}
              label={source.label}
              empty={source.empty}
              records={records[source.type]}
              lines={lines.filter((line) => line.type === source.type)}
              onToggle={(record) => toggleRecord(source.type, record)}
              onText={update}
              onRemove={remove}
              suggest={(record) => suggestedText(source.type, record)}
            />
          ))}

          <fieldset>
            <legend className="text-sm font-semibold text-slate-900">Other information</legend>
            <p className="mt-1 text-sm text-slate-600">For example a blood type or a care instruction you want to share.</p>
            <ul className="mt-2 space-y-2">
              {notes.map((line, index) => (
                <li key={line.key} className="flex items-start gap-2">
                  <input
                    className={`${inputClass} mt-0`}
                    aria-label={`Other information, line ${index + 1}`}
                    maxLength={MAX_TEXT}
                    value={line.text}
                    onChange={(event) => update(line.key, event.target.value)}
                  />
                  <Button
                    variant="ghost"
                    icon={Trash2}
                    aria-label={`Remove other information, line ${index + 1}`}
                    onClick={() => remove(line.key)}
                  />
                </li>
              ))}
            </ul>
            <Button
              className="mt-2"
              icon={Plus}
              onClick={() =>
                setLines((current) => [
                  ...current,
                  { key: key(), type: 'NOTE', text: '', sourceRecordId: null, sourceStatus: 'NONE' },
                ])
              }
            >
              Add a line
            </Button>
          </fieldset>
        </div>

        <div className="mt-6 flex flex-wrap items-center gap-3 border-t border-slate-200 pt-4">
          <Button variant="primary" busy={busy === 'save'} disabled={!dirty || invalid} onClick={() => void save()}>
            Save card
          </Button>
          {dirty && (
            <Button variant="ghost" onClick={() => setLines(toLines(card))}>
              Discard changes
            </Button>
          )}
          <span className="text-sm text-slate-600">
            {draft.length} of {MAX_ITEMS} lines
            {draft.length > MAX_ITEMS && ': remove some lines to save'}
            {draft.some((item) => !item.text) && '. Empty lines must be filled in or removed'}
          </span>
        </div>
      </Card>
    </div>
  )
}

function NameField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const id = useId()
  return (
    <div>
      <label htmlFor={id} className="text-sm font-semibold text-slate-900">
        Name on the card
      </label>
      <p className="mt-1 text-sm text-slate-600">For example a first name. Leave it empty to show no name.</p>
      <input
        id={id}
        className={`${inputClass} max-w-sm`}
        maxLength={MAX_TEXT}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  )
}

function SourceGroup({
  label,
  empty,
  records,
  lines,
  onToggle,
  onText,
  onRemove,
  suggest,
}: {
  label: string
  empty: string
  records: RecordItem[]
  lines: Line[]
  onToggle: (record: RecordItem) => void
  onText: (lineKey: string, value: string) => void
  onRemove: (lineKey: string) => void
  suggest: (record: RecordItem) => string
}) {
  const recordIds = new Set(records.map((record) => record.id))
  // Lines whose record was deleted, or that were typed in by hand, have no checkbox to untick.
  const orphans = lines.filter((line) => !line.sourceRecordId || !recordIds.has(line.sourceRecordId))

  return (
    <fieldset>
      <legend className="text-sm font-semibold text-slate-900">{label}</legend>
      {records.length === 0 && orphans.length === 0 && <p className="mt-1 text-sm text-slate-600">{empty}</p>}
      <ul className="mt-2 space-y-3">
        {records.map((record) => {
          const line = lines.find((item) => item.sourceRecordId === record.id)
          const suggestion = suggest(record)
          return (
            <li key={record.id}>
              <label className="flex items-start gap-2 text-sm text-slate-800">
                <input
                  type="checkbox"
                  className="mt-0.5 size-4 rounded border-slate-300 text-brand-600 focus:ring-brand-600"
                  checked={Boolean(line)}
                  onChange={() => onToggle(record)}
                />
                <span className="min-w-0 break-words">{suggestion || 'Unnamed record'}</span>
              </label>
              {line && (
                <div className="mt-2 ml-6">
                  <input
                    className={`${inputClass} mt-0`}
                    aria-label={`Card text for ${suggestion || 'unnamed record'}`}
                    maxLength={MAX_TEXT}
                    value={line.text}
                    onChange={(event) => onText(line.key, event.target.value)}
                  />
                  {line.sourceStatus === 'CHANGED' && (
                    <ChangedNote
                      message="This record has changed since you saved the line. Check the text still fits."
                      action={
                        suggestion !== line.text ? (
                          <Button size="sm" onClick={() => onText(line.key, suggestion)}>
                            Use the record's current text
                          </Button>
                        ) : null
                      }
                    />
                  )}
                </div>
              )}
            </li>
          )
        })}
        {orphans.map((line) => (
          <li key={line.key}>
            <div className="flex items-start gap-2">
              <input
                className={`${inputClass} mt-0`}
                aria-label={`Card text for ${label.toLowerCase()} line: ${line.text}`}
                maxLength={MAX_TEXT}
                value={line.text}
                onChange={(event) => onText(line.key, event.target.value)}
              />
              <Button variant="ghost" icon={Trash2} aria-label={`Remove ${line.text}`} onClick={() => onRemove(line.key)} />
            </div>
            {line.sourceStatus === 'DELETED' && (
              <ChangedNote message="The record this line came from was deleted. The card still shows the saved text until you remove it." />
            )}
          </li>
        ))}
      </ul>
    </fieldset>
  )
}

function ChangedNote({ message, action }: { message: string; action?: ReactNode }) {
  return (
    <div className="mt-2 flex flex-wrap items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
      <ShieldAlert aria-hidden="true" className="size-4 shrink-0" />
      <span className="min-w-0 flex-1">{message}</span>
      {action}
    </div>
  )
}

function LinkBox({ link }: { link: string }) {
  const id = useId()
  const toast = useToast()

  async function copy() {
    try {
      await navigator.clipboard.writeText(link)
      toast('Link copied')
    } catch {
      toast("Couldn't copy. Select the link and copy it instead")
    }
  }

  return (
    <div className="mt-4 rounded-lg border border-brand-200 bg-brand-50 p-4">
      <p className="text-sm font-semibold text-slate-900">Save this link now. It is shown only once.</p>
      <p className="mt-1 text-sm text-slate-700">
        Keep it somewhere you can reach in an emergency, or print the QR code. If you lose it, make a new link.
      </p>
      <div className="mt-3 flex flex-col gap-4 sm:flex-row sm:items-start">
        <QrCode value={link} label="QR code for the emergency card link" size={176} />
        <div className="min-w-0 flex-1">
          <label htmlFor={id} className="text-sm font-medium text-slate-900">
            Emergency card link
          </label>
          <input id={id} readOnly className={inputClass} value={link} onFocus={(event) => event.target.select()} />
          <div className="mt-2 flex flex-wrap gap-2">
            <Button icon={Copy} onClick={() => void copy()}>
              Copy link
            </Button>
            <a
              href={link}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-brand-700 hover:bg-brand-100 hover:underline"
            >
              <ExternalLink aria-hidden="true" className="size-4" />
              Open the card
            </a>
          </div>
        </div>
      </div>
    </div>
  )
}

