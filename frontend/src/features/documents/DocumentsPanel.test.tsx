import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { page, problem, stubApi } from '../../test/stubApi'
import { DocumentsPanel } from './DocumentsPanel'

const document = {
  id: 'd1',
  profileId: 'p1',
  originalFilename: 'prescription.jpg',
  contentType: 'image/jpeg',
  sizeBytes: 2048,
  category: 'PRESCRIPTION',
  documentOn: null,
  description: 'Synthetic prescription',
  extractionStatus: 'NOT_REQUESTED',
  uploadedAt: '2026-11-06T10:00:00Z',
}

const LIST = 'GET /profiles/p1/documents?size=100'

function chooseFile(file: File) {
  fireEvent.change(screen.getByLabelText(/^File/), { target: { files: [file] } })
}

describe('DocumentsPanel', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('listsDocumentsWithTheirTypeAndAMissingDate', async () => {
    stubApi({ [LIST]: page([document]) })

    render(<DocumentsPanel profileId="p1" canEdit canManage={false} />)

    expect(await screen.findByText('Synthetic prescription')).toBeInTheDocument()
    expect(screen.getByText('Prescription')).toBeInTheDocument()
    expect(screen.getByText('Not recorded')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Delete/ })).not.toBeInTheDocument()
  })

  it('uploadsAsMultipartWithTheChosenDetails', async () => {
    const stub = stubApi({
      [LIST]: [page([]), page([document])],
      'POST /profiles/p1/documents': { status: 201, body: document },
    })
    render(<DocumentsPanel profileId="p1" canEdit canManage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Upload a document' }))

    chooseFile(new File(['%PDF-1.7'], 'report.pdf', { type: 'application/pdf' }))
    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'LAB_REPORT' } })
    fireEvent.change(screen.getByLabelText(/^Date on the document/), { target: { value: '2026-03-04' } })
    fireEvent.click(screen.getByRole('button', { name: 'Upload' }))

    expect(await screen.findByText('Synthetic prescription')).toBeInTheDocument()
    const call = stub.mock.calls.find(([, init]) => init?.method === 'POST')!
    const form = call[1]!.body as FormData
    expect(form.get('category')).toBe('LAB_REPORT')
    expect(form.get('documentOn')).toBe('2026-03-04')
    expect((form.get('file') as File).name).toBe('report.pdf')
    expect(form.has('description')).toBe(false)
    // The browser adds the multipart boundary itself.
    expect(new Headers(call[1]!.headers).has('Content-Type')).toBe(false)
  })

  it('stopsOversizedAndUnsupportedFilesBeforeUploading', async () => {
    const stub = stubApi({ [LIST]: page([]) })
    render(<DocumentsPanel profileId="p1" canEdit canManage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Upload a document' }))

    chooseFile(new File(['<svg/>'], 'x.svg', { type: 'image/svg+xml' }))
    fireEvent.click(screen.getByRole('button', { name: 'Upload' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Only PDF, JPEG, and PNG')

    const large = new File(['x'], 'big.png', { type: 'image/png' })
    Object.defineProperty(large, 'size', { value: 10 * 1024 * 1024 + 1 })
    chooseFile(large)
    fireEvent.click(screen.getByRole('button', { name: 'Upload' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('larger than 10 MB')

    expect(stub.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
  })

  it('showsTheServersReasonWhenAnUploadIsRejected', async () => {
    stubApi({
      [LIST]: page([]),
      'POST /profiles/p1/documents': problem(415, 'UNSUPPORTED_FILE_TYPE', 'Only PDF, JPEG, and PNG files are allowed'),
    })
    render(<DocumentsPanel profileId="p1" canEdit canManage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Upload a document' }))

    chooseFile(new File(['<html>'], 'photo.png', { type: 'image/png' }))
    fireEvent.click(screen.getByRole('button', { name: 'Upload' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Only PDF, JPEG, and PNG files are allowed')
  })

  it('deletesAfterConfirmingWhenTheUserCanManage', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const stub = stubApi({
      [LIST]: [page([document]), page([])],
      'DELETE /profiles/p1/documents/d1': { status: 204 },
    })
    render(<DocumentsPanel profileId="p1" canEdit canManage />)

    fireEvent.click(await screen.findByRole('button', { name: 'Delete prescription.jpg' }))

    expect(await screen.findByText('No documents uploaded.')).toBeInTheDocument()
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('keep their data'))
    expect(stub).toHaveBeenCalledWith('/api/v1/profiles/p1/documents/d1', expect.anything())
  })

  it('opensAnImageThroughTheApi', async () => {
    URL.createObjectURL = vi.fn(() => 'blob:synthetic')
    URL.revokeObjectURL = vi.fn()
    stubApi({
      [LIST]: page([document]),
      'GET /profiles/p1/documents/d1/content': { status: 200, body: 'image-bytes' },
    })
    render(<DocumentsPanel profileId="p1" canEdit={false} canManage={false} />)

    fireEvent.click(await screen.findByRole('button', { name: 'Open prescription.jpg' }))

    expect(await screen.findByRole('img', { name: 'Synthetic prescription' })).toHaveAttribute(
      'src',
      'blob:synthetic',
    )
    expect(screen.getByRole('link', { name: 'Download' })).toHaveAttribute('download', 'prescription.jpg')
    fireEvent.click(screen.getByRole('button', { name: 'Close' }))
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:synthetic'))
  })
})
