import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AiBadge, SourceBadge } from './Badge'
import { Button } from './Button'
import { useConfirm } from './Dialog'
import { EmptyState, Loading } from './Feedback'
import { PasswordInput } from './PasswordInput'
import { ToastProvider, useToast } from './Toast'

function ConfirmHarness() {
  const [confirm, dialog] = useConfirm()
  const [result, setResult] = useState('none')
  return (
    <>
      {dialog}
      <button
        type="button"
        onClick={async () =>
          setResult(
            String(await confirm({ title: 'Delete it?', message: 'Gone for good.', confirmLabel: 'Delete' })),
          )
        }
      >
        Ask
      </button>
      <output>{result}</output>
    </>
  )
}

describe('design system components', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('labelsEachSourceInWordsAsWellAsColour', () => {
    render(
      <>
        <SourceBadge sourceType="USER_REPORTED" />
        <SourceBadge sourceType="DOCUMENTED" sourceDocumentDeletedAt="2026-01-01T00:00:00Z" />
        <AiBadge />
      </>,
    )

    expect(screen.getByText('User reported')).toBeInTheDocument()
    expect(screen.getByText(/From document · source document deleted/)).toBeInTheDocument()
    expect(screen.getByText('AI generated')).toBeInTheDocument()
  })

  it('disablesABusyButtonAndMarksItBusy', () => {
    const onClick = vi.fn()
    render(
      <Button busy onClick={onClick}>
        Save
      </Button>,
    )

    const button = screen.getByRole('button', { name: 'Save' })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')
  })

  it('confirmsOnlyWhenTheUserChoosesTheAction', async () => {
    render(<ConfirmHarness />)

    fireEvent.click(screen.getByRole('button', { name: 'Ask' }))
    const dialog = screen.getByRole('dialog', { name: 'Delete it?' })
    expect(dialog).toHaveTextContent('Gone for good.')
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus()
    fireEvent.keyDown(dialog, { key: 'Escape' })
    expect(await screen.findByText('false')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Ask' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete' }))
    expect(await screen.findByText('true')).toBeInTheDocument()
  })

  it('showsAToastThatGoesAway', () => {
    vi.useFakeTimers()
    function Saver() {
      const toast = useToast()
      return (
        <button type="button" onClick={() => toast('Saved')}>
          Save
        </button>
      )
    }
    render(
      <ToastProvider>
        <Saver />
      </ToastProvider>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(screen.getByRole('status')).toHaveTextContent('Saved')
    act(() => vi.advanceTimersByTime(4000))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('announcesLoadingAndExplainsEmptyStates', () => {
    render(
      <>
        <Loading />
        <EmptyState title="No documents yet">Upload a prescription to keep it with the profile.</EmptyState>
      </>,
    )

    expect(screen.getByRole('status')).toHaveTextContent('Loading…')
    expect(screen.getByText('No documents yet')).toBeInTheDocument()
  })
})

describe('PasswordInput', () => {
  it('showsAndHidesWhatWasTyped', () => {
    render(
      <label>
        Password
        <PasswordInput defaultValue="synthetic-secret" />
      </label>,
    )
    const input = screen.getByLabelText('Password')
    expect(input).toHaveAttribute('type', 'password')

    fireEvent.click(screen.getByRole('button', { name: 'Show password' }))
    expect(input).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Show password' })).toHaveAttribute('aria-pressed', 'true')

    fireEvent.click(screen.getByRole('button', { name: 'Show password' }))
    expect(input).toHaveAttribute('type', 'password')
  })
})
