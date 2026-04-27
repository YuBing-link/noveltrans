import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

const mockGlossaryGetList = vi.fn()
const mockGlossaryCreate = vi.fn()
const mockGlossaryUpdate = vi.fn()
const mockGlossaryDelete = vi.fn()
const mockGlossaryExport = vi.fn()
const mockGlossaryImport = vi.fn()
const mockToastSuccess = vi.fn()
const mockToastError = vi.fn()

vi.mock('../api/glossaries', () => ({
  glossaryApi: {
    getList: (...args: unknown[]) => mockGlossaryGetList(...args),
    create: (...args: unknown[]) => mockGlossaryCreate(...args),
    update: (...args: unknown[]) => mockGlossaryUpdate(...args),
    delete: (...args: unknown[]) => mockGlossaryDelete(...args),
    exportGlossary: (...args: unknown[]) => mockGlossaryExport(...args),
    importGlossary: (...args: unknown[]) => mockGlossaryImport(...args),
  },
}))

vi.mock('../components/ui/Toast', () => ({
  useToast: () => ({ success: mockToastSuccess, error: mockToastError }),
}))

import { GlossaryPage } from './GlossaryPage'

const mockTerms = [
  {
    id: 1,
    sourceWord: 'hello',
    targetWord: '你好',
    remark: 'Greeting',
    createTime: '2026-04-27T10:00:00Z',
    updateTime: '2026-04-27T10:00:00Z',
  },
  {
    id: 2,
    sourceWord: 'world',
    targetWord: '世界',
    remark: null,
    createTime: '2026-04-27T11:00:00Z',
    updateTime: '2026-04-27T11:00:00Z',
  },
]

function renderGlossaryPage() {
  return render(
    <MemoryRouter>
      <GlossaryPage />
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockGlossaryGetList.mockResolvedValue({ data: { list: [], totalPages: 1, total: 0 } })
})

describe('GlossaryPage', () => {
  describe('header and search', () => {
    it('renders page title and subtitle', () => {
      renderGlossaryPage()
      expect(screen.getByText('glossary.title')).toBeInTheDocument()
      expect(screen.getByText('glossary.subtitle')).toBeInTheDocument()
    })

    it('renders search input', () => {
      renderGlossaryPage()
      const searchInput = screen.getByPlaceholderText('glossary.search')
      expect(searchInput).toBeInTheDocument()
    })

    it('renders import, export, and add buttons', () => {
      renderGlossaryPage()
      expect(screen.getByText('glossary.actions.import')).toBeInTheDocument()
      expect(screen.getByText('glossary.actions.export')).toBeInTheDocument()
      expect(screen.getByText('glossary.actions.add')).toBeInTheDocument()
    })
  })

  describe('term list', () => {
    it('shows empty state when no terms', async () => {
      renderGlossaryPage()
      await waitFor(() => {
        expect(screen.getByText('glossary.empty.title')).toBeInTheDocument()
      })
    })

    it('renders terms in table', async () => {
      mockGlossaryGetList.mockResolvedValue({ data: { list: mockTerms, totalPages: 1, total: 2 } })
      renderGlossaryPage()

      await waitFor(() => {
        expect(screen.getByText('hello')).toBeInTheDocument()
      })
      expect(screen.getByText('你好')).toBeInTheDocument()
      expect(screen.getByText('world')).toBeInTheDocument()
      expect(screen.getByText('世界')).toBeInTheDocument()
    })

    it('shows remark for terms with notes', async () => {
      mockGlossaryGetList.mockResolvedValue({ data: { list: mockTerms, totalPages: 1, total: 2 } })
      renderGlossaryPage()

      await waitFor(() => {
        expect(screen.getByText('hello')).toBeInTheDocument()
      })
      expect(screen.getByText('Greeting')).toBeInTheDocument()
    })

    it('shows dash for terms without notes', async () => {
      mockGlossaryGetList.mockResolvedValue({ data: { list: mockTerms, totalPages: 1, total: 2 } })
      renderGlossaryPage()

      await waitFor(() => {
        expect(screen.getByText('world')).toBeInTheDocument()
      })
      const dashElements = screen.getAllByText('-')
      expect(dashElements.length).toBeGreaterThan(0)
    })

    it('shows total count in footer', async () => {
      mockGlossaryGetList.mockResolvedValue({ data: { list: mockTerms, totalPages: 1, total: 5 } })
      renderGlossaryPage()

      await waitFor(() => {
        expect(screen.getByText('hello')).toBeInTheDocument()
      })
      expect(screen.getByText(/5/)).toBeInTheDocument()
    })
  })

  describe('add term', () => {
    it('opens modal when add button clicked', async () => {
      renderGlossaryPage()

      fireEvent.click(screen.getByText('glossary.actions.add'))

      expect(screen.getByText('glossary.form.source')).toBeInTheDocument()
      expect(screen.getByText('glossary.form.target')).toBeInTheDocument()
    })

    it('creates new term and closes modal', async () => {
      mockGlossaryCreate.mockResolvedValueOnce({ data: { id: 3 } })
      mockGlossaryGetList
        .mockResolvedValueOnce({ data: { list: [], totalPages: 1, total: 0 } })
        .mockResolvedValue({ data: { list: [{ id: 3, sourceWord: 'new', targetWord: '新', remark: null }], totalPages: 1, total: 1 } })
      renderGlossaryPage()

      fireEvent.click(screen.getByText('glossary.actions.add'))

      const sourceInput = screen.getByPlaceholderText('glossary.form.sourcePlaceholder')
      const targetInput = screen.getByPlaceholderText('glossary.form.targetPlaceholder')
      fireEvent.change(sourceInput, { target: { value: 'new' } })
      fireEvent.change(targetInput, { target: { value: '新' } })

      const saveButton = screen.getByText('glossary.buttons.save')
      fireEvent.click(saveButton)

      await waitFor(() => {
        expect(mockGlossaryCreate).toHaveBeenCalledWith({
          sourceWord: 'new',
          targetWord: '新',
          remark: '',
        })
      })
      await waitFor(() => {
        expect(mockToastSuccess).toHaveBeenCalled()
      })
    })

    it('shows error when required fields are empty', async () => {
      renderGlossaryPage()

      fireEvent.click(screen.getByText('glossary.actions.add'))

      const saveButton = screen.getByText('glossary.buttons.save')
      fireEvent.click(saveButton)

      await waitFor(() => {
        expect(mockToastError).toHaveBeenCalled()
      })
      expect(mockGlossaryCreate).not.toHaveBeenCalled()
    })
  })

  describe('edit term', () => {
    it('opens modal with term data when edit clicked', async () => {
      mockGlossaryGetList.mockResolvedValue({ data: { list: mockTerms, totalPages: 1, total: 2 } })
      renderGlossaryPage()

      await waitFor(() => {
        expect(screen.getByText('hello')).toBeInTheDocument()
      })

      const editButtons = screen.getAllByRole('button')
      const editButton = editButtons.find(btn =>
        btn.querySelector('svg')?.getAttribute('class')?.includes('lucide-pencil')
      )

      expect(editButton).toBeInTheDocument()
      fireEvent.click(editButton!)

      expect(screen.getByText('glossary.actions.edit')).toBeInTheDocument()
      const sourceInput = screen.getByDisplayValue('hello')
      expect(sourceInput).toBeInTheDocument()
      const targetInput = screen.getByDisplayValue('你好')
      expect(targetInput).toBeInTheDocument()
    })

    it('updates term on save', async () => {
      mockGlossaryGetList.mockResolvedValue({ data: { list: mockTerms, totalPages: 1, total: 2 } })
      mockGlossaryUpdate.mockResolvedValueOnce({ data: null })
      renderGlossaryPage()

      await waitFor(() => {
        expect(screen.getByText('hello')).toBeInTheDocument()
      })

      const editButtons = screen.getAllByRole('button')
      const editButton = editButtons.find(btn =>
        btn.querySelector('svg')?.getAttribute('class')?.includes('lucide-pencil')
      )
      fireEvent.click(editButton!)

      const sourceInput = screen.getByDisplayValue('hello')
      fireEvent.change(sourceInput, { target: { value: 'hello updated' } })

      const saveButton = screen.getByText('glossary.buttons.save')
      fireEvent.click(saveButton)

      await waitFor(() => {
        expect(mockGlossaryUpdate).toHaveBeenCalledWith(1, {
          sourceWord: 'hello updated',
          targetWord: '你好',
          remark: 'Greeting',
        })
      })
    })
  })

  describe('delete term', () => {
    it('deletes term on confirm', async () => {
      mockGlossaryGetList
        .mockResolvedValueOnce({ data: { list: mockTerms, totalPages: 1, total: 2 } })
      mockGlossaryDelete.mockResolvedValueOnce({ data: null })
      renderGlossaryPage()

      await waitFor(() => {
        expect(screen.getByText('hello')).toBeInTheDocument()
      })

      const deleteButtons = screen.getAllByRole('button')
      const deleteButton = deleteButtons.find(btn =>
        btn.querySelector('svg')?.getAttribute('class')?.includes('lucide-trash-2')
      )

      fireEvent.click(deleteButton!)

      await waitFor(() => {
        expect(mockGlossaryDelete).toHaveBeenCalledWith(1)
      })
      await waitFor(() => {
        expect(mockToastSuccess).toHaveBeenCalled()
      })
    })
  })

  describe('export', () => {
    it('calls export API on export button click', async () => {
      mockGlossaryExport.mockResolvedValueOnce(undefined)
      renderGlossaryPage()

      fireEvent.click(screen.getByText('glossary.actions.export'))

      await waitFor(() => {
        expect(mockGlossaryExport).toHaveBeenCalled()
      })
    })
  })

  describe('search', () => {
    it('resets page to 1 when search changes', async () => {
      renderGlossaryPage()

      const searchInput = screen.getByPlaceholderText('glossary.search')
      fireEvent.change(searchInput, { target: { value: 'test' } })

      expect(screen.getByPlaceholderText('glossary.search')).toHaveValue('test')
    })
  })
})
