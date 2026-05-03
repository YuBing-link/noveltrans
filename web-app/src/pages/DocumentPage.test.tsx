import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

const mockGetList = vi.fn()
const mockUpload = vi.fn()
const mockDelete = vi.fn()
const mockDownload = vi.fn()
const mockRetry = vi.fn()
const mockCancel = vi.fn()
const mockToastSuccess = vi.fn()
const mockToastError = vi.fn()

vi.mock('../api/documents', () => ({
  documentApi: {
    getList: (...args: unknown[]) => mockGetList(...args),
    upload: (...args: unknown[]) => mockUpload(...args),
    delete: (...args: unknown[]) => mockDelete(...args),
    download: (...args: unknown[]) => mockDownload(...args),
    retry: (...args: unknown[]) => mockRetry(...args),
    cancel: (...args: unknown[]) => mockCancel(...args),
  },
}))

vi.mock('../components/ui/Toast', () => ({
  useToast: () => ({ success: mockToastSuccess, error: mockToastError }),
}))

import { DocumentPage } from './DocumentPage'

const mockDocuments = [
  {
    id: 1,
    name: 'test-novel.txt',
    fileName: 'test-novel.txt',
    fileType: '.txt',
    fileSize: 102400,
    sourceLang: 'en',
    targetLang: 'zh',
    status: 'completed',
    progress: 100,
    createTime: '2026-04-27T10:00:00Z',
    completedTime: '2026-04-27T10:30:00Z',
    errorMessage: null,
  },
  {
    id: 2,
    name: 'chapter-2.epub',
    fileName: 'chapter-2.epub',
    fileType: '.epub',
    fileSize: 524288,
    sourceLang: 'ja',
    targetLang: 'zh',
    status: 'processing',
    progress: 45,
    createTime: '2026-04-27T11:00:00Z',
    completedTime: null,
    errorMessage: null,
  },
  {
    id: 3,
    name: 'failed-doc.pdf',
    fileName: 'failed-doc.pdf',
    fileType: '.pdf',
    fileSize: 2048000,
    sourceLang: 'en',
    targetLang: 'zh',
    status: 'failed',
    progress: 20,
    createTime: '2026-04-27T09:00:00Z',
    completedTime: null,
    errorMessage: 'Translation engine timeout',
  },
]

function renderDocumentPage() {
  return render(
    <MemoryRouter>
      <DocumentPage />
    </MemoryRouter>
  )
}

function createMockFile(name: string, size: number, type: string = 'text/plain') {
  // Create a file with the specified size by padding content
  const content = 'x'.repeat(size)
  return new File([content], name, { type })
}

beforeEach(() => {
  vi.clearAllMocks()
  // Default: return empty list for any unmocked getList call
  mockGetList.mockResolvedValue({ data: { list: [], total: 0 } })
})

describe('DocumentPage', () => {
  describe('header and filters', () => {
    it('renders page title and subtitle', () => {
      mockGetList.mockResolvedValueOnce({ data: { list: [], total: 0 } })
      renderDocumentPage()
      expect(screen.getByText('document.title')).toBeInTheDocument()
      expect(screen.getByText('document.subtitle')).toBeInTheDocument()
    })

    it('renders source and target language selectors', () => {
      mockGetList.mockResolvedValueOnce({ data: { list: [], total: 0 } })
      renderDocumentPage()
      const selects = screen.getAllByRole('combobox')
      expect(selects).toHaveLength(2) // source, target
    })
  })

  describe('upload area', () => {
    it('renders upload drop zone with supported format labels', () => {
      mockGetList.mockResolvedValueOnce({ data: { list: [], total: 0 } })
      renderDocumentPage()
      expect(screen.getByText('document.upload.drag')).toBeInTheDocument()
      expect(screen.getByText('document.upload.formats')).toBeInTheDocument()
      expect(screen.getByText('TXT')).toBeInTheDocument()
      expect(screen.getByText('EPUB')).toBeInTheDocument()
      expect(screen.getByText('DOCX')).toBeInTheDocument()
      expect(screen.getByText('PDF')).toBeInTheDocument()
    })

    it('shows error for unsupported file type', async () => {
      renderDocumentPage()

      const dropzone = screen.getByText('document.upload.drag').closest('div[class*="border-dashed"]')!
      const file = createMockFile('image.png', 1024)

      fireEvent.drop(dropzone, { dataTransfer: { files: [file] } })

      await waitFor(() => {
        expect(mockToastError).toHaveBeenCalledWith(expect.stringContaining('不支持'))
      })
    })

    it('shows error for oversized file', async () => {
      renderDocumentPage()

      const dropzone = screen.getByText('document.upload.drag').closest('div[class*="border-dashed"]')!
      const file = createMockFile('huge.txt', 60 * 1024 * 1024)

      fireEvent.drop(dropzone, { dataTransfer: { files: [file] } })

      await waitFor(() => {
        expect(mockToastError).toHaveBeenCalledWith(expect.stringContaining('文件过大'))
      })
    })

    it('calls upload API with valid file', async () => {
      mockUpload.mockResolvedValueOnce({ data: { id: 1 } })
      renderDocumentPage()

      const dropzone = screen.getByText('document.upload.drag').closest('div[class*="border-dashed"]')!
      const file = createMockFile('novel.txt', 1024)

      fireEvent.drop(dropzone, { dataTransfer: { files: [file] } })

      await waitFor(() => {
        expect(mockUpload).toHaveBeenCalled()
      })
      await waitFor(() => {
        expect(mockToastSuccess).toHaveBeenCalled()
      })
    })

    it('shows uploading indicator during upload', () => {
      mockUpload.mockImplementationOnce(() => new Promise(() => {})) // never resolves
      renderDocumentPage()

      const dropzone = screen.getByText('document.upload.drag').closest('div[class*="border-dashed"]')!
      const file = createMockFile('novel.txt', 1024)

      fireEvent.drop(dropzone, { dataTransfer: { files: [file] } })

      expect(screen.getByText('document.upload.uploading')).toBeInTheDocument()
    })
  })

  describe('document list', () => {
    it('shows empty state when no documents', async () => {
      mockGetList.mockResolvedValueOnce({ data: { list: [], total: 0 } })
      renderDocumentPage()

      await waitFor(() => {
        expect(screen.getByText('document.empty.title')).toBeInTheDocument()
      })
      expect(screen.getByText('document.empty.subtitle')).toBeInTheDocument()
    })

    it('renders document list with name, language, size, and status', async () => {
      mockGetList.mockResolvedValue({ data: { list: mockDocuments, total: 3 } })
      renderDocumentPage()

      await waitFor(() => {
        expect(screen.getByText('test-novel.txt')).toBeInTheDocument()
      })
      expect(screen.getByText('chapter-2.epub')).toBeInTheDocument()
      expect(screen.getByText('failed-doc.pdf')).toBeInTheDocument()
    })

    it('shows progress bar for processing documents', async () => {
      mockGetList.mockResolvedValue({ data: { list: mockDocuments, total: 3 } })
      renderDocumentPage()

      await waitFor(() => {
        expect(screen.getByText('chapter-2.epub')).toBeInTheDocument()
      })

      expect(screen.getByText('45%')).toBeInTheDocument()
    })

    it('shows error message for failed documents', async () => {
      mockGetList.mockResolvedValue({ data: { list: mockDocuments, total: 3 } })
      renderDocumentPage()

      await waitFor(() => {
        expect(screen.getByText('failed-doc.pdf')).toBeInTheDocument()
      })

      expect(screen.getByText('Translation engine timeout')).toBeInTheDocument()
    })

    it('formats file size correctly', async () => {
      mockGetList.mockResolvedValue({ data: { list: mockDocuments, total: 3 } })
      renderDocumentPage()

      await waitFor(() => {
        expect(screen.getByText('test-novel.txt')).toBeInTheDocument()
      })

      expect(screen.getByText('100.0 KB')).toBeInTheDocument()
      expect(screen.getByText('512.0 KB')).toBeInTheDocument()
      expect(screen.getByText('2.0 MB')).toBeInTheDocument()
    })
  })

  describe('actions', () => {
    it('deletes document and shows success', async () => {
      mockGetList.mockResolvedValue({ data: { list: mockDocuments, total: 3 } })
      mockDelete.mockResolvedValueOnce({ data: null })
      renderDocumentPage()

      await waitFor(() => {
        expect(screen.getByText('test-novel.txt')).toBeInTheDocument()
      })

      const deleteButtons = screen.getAllByRole('button')
      const deleteButton = deleteButtons.find(btn =>
        btn.querySelector('svg')?.getAttribute('class')?.includes('lucide-trash-2')
      )

      fireEvent.click(deleteButton!)

      await waitFor(() => {
        expect(mockDelete).toHaveBeenCalledWith(1)
      })
      await waitFor(() => {
        expect(mockToastSuccess).toHaveBeenCalled()
      })
    })

    it('downloads completed document', async () => {
      mockGetList.mockResolvedValue({ data: { list: mockDocuments, total: 3 } })
      mockDownload.mockResolvedValueOnce(new Blob(['translated content'], { type: 'text/plain' }))
      renderDocumentPage()

      await waitFor(() => {
        expect(screen.getByText('test-novel.txt')).toBeInTheDocument()
      })

      const buttons = screen.getAllByRole('button')
      const downloadButton = buttons.find(btn =>
        btn.querySelector('svg')?.getAttribute('class')?.includes('lucide-download')
      )

      fireEvent.click(downloadButton!)

      await waitFor(() => {
        expect(mockDownload).toHaveBeenCalledWith(1)
      })
    })

    it('retries failed document', async () => {
      mockGetList.mockResolvedValue({ data: { list: mockDocuments, total: 3 } })
      mockRetry.mockResolvedValueOnce({ data: null })
      renderDocumentPage()

      await waitFor(() => {
        expect(screen.getByText('failed-doc.pdf')).toBeInTheDocument()
      })

      const buttons = screen.getAllByRole('button')
      const retryButton = buttons.find(btn =>
        btn.querySelector('svg')?.getAttribute('class')?.includes('lucide-refresh-cw')
      )

      fireEvent.click(retryButton!)

      await waitFor(() => {
        expect(mockRetry).toHaveBeenCalledWith(3)
      })
      await waitFor(() => {
        expect(mockToastSuccess).toHaveBeenCalled()
      })
    })

    it('cancels processing document', async () => {
      mockGetList.mockResolvedValue({ data: { list: mockDocuments, total: 3 } })
      mockCancel.mockResolvedValueOnce({ data: null })
      renderDocumentPage()

      await waitFor(() => {
        expect(screen.getByText('chapter-2.epub')).toBeInTheDocument()
      })

      // Find the cancel button by title attribute pattern
      const cancelButton = screen.queryByTitle('document.actions.cancel')
      if (!cancelButton) {
        // Fallback: find by x-circle svg class
        const allButtons = screen.getAllByRole('button')
        const cancelBtn = allButtons.find(btn =>
          btn.querySelector('.lucide-x-circle')
        )
        expect(cancelBtn).toBeDefined()
        fireEvent.click(cancelBtn!)
      } else {
        fireEvent.click(cancelButton)
      }

      await waitFor(() => {
        expect(mockCancel).toHaveBeenCalledWith(2)
      })
    })
  })

  describe('refresh', () => {
    it('calls loadDocuments on refresh button click', async () => {
      mockGetList.mockResolvedValue({ data: { list: mockDocuments, total: 3 } })
      renderDocumentPage()

      await waitFor(() => {
        expect(screen.getByText('test-novel.txt')).toBeInTheDocument()
      })

      const initialCallCount = mockGetList.mock.calls.length
      const refreshButton = screen.getByText('common.refresh')
      fireEvent.click(refreshButton)

      await waitFor(() => {
        expect(mockGetList.mock.calls.length).toBeGreaterThan(initialCallCount)
      })
    })
  })
})
