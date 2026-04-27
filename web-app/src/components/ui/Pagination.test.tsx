import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Pagination } from './Pagination'

function getPaginationButtons(container: HTMLElement) {
  const buttons = container.querySelectorAll('button')
  return Array.from(buttons)
}

function getPrevButton(container: HTMLElement) {
  return getPaginationButtons(container)[0] ?? null
}

function getNextButton(container: HTMLElement) {
  const buttons = getPaginationButtons(container)
  return buttons[buttons.length - 1] ?? null
}

describe('Pagination', () => {
  it('does not render when totalPages is 1', () => {
    const { container } = render(<Pagination page={1} totalPages={1} onPageChange={vi.fn()} />)
    expect(container.querySelector('button')).not.toBeInTheDocument()
  })

  it('does not render when totalPages is 0', () => {
    const { container } = render(<Pagination page={1} totalPages={0} onPageChange={vi.fn()} />)
    expect(container.querySelector('button')).not.toBeInTheDocument()
  })

  it('renders navigation buttons when totalPages > 1', () => {
    const { container } = render(<Pagination page={1} totalPages={3} onPageChange={vi.fn()} />)
    const buttons = screen.getAllByRole('button')
    expect(buttons.length).toBeGreaterThan(0)
  })

  it('disables previous button on first page', () => {
    const { container } = render(<Pagination page={1} totalPages={5} onPageChange={vi.fn()} />)
    const prevBtn = getPrevButton(container)
    expect(prevBtn).toBeDisabled()
  })

  it('disables next button on last page', () => {
    const { container } = render(<Pagination page={5} totalPages={5} onPageChange={vi.fn()} />)
    const nextBtn = getNextButton(container)
    expect(nextBtn).toBeDisabled()
  })

  it('calls onPageChange with previous page when previous button clicked', async () => {
    const user = userEvent.setup()
    const handleChange = vi.fn()
    const { container } = render(<Pagination page={3} totalPages={5} onPageChange={handleChange} />)

    const prevBtn = getPrevButton(container)
    if (prevBtn) await user.click(prevBtn)

    expect(handleChange).toHaveBeenCalledWith(2)
  })

  it('calls onPageChange with next page when next button clicked', async () => {
    const user = userEvent.setup()
    const handleChange = vi.fn()
    const { container } = render(<Pagination page={3} totalPages={5} onPageChange={handleChange} />)

    const nextBtn = getNextButton(container)
    if (nextBtn) await user.click(nextBtn)

    expect(handleChange).toHaveBeenCalledWith(4)
  })

  it('renders page number buttons', () => {
    render(<Pagination page={1} totalPages={3} onPageChange={vi.fn()} />)
    expect(screen.getByRole('button', { name: '1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '2' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '3' })).toBeInTheDocument()
  })

  it('highlights current page', () => {
    render(<Pagination page={2} totalPages={5} onPageChange={vi.fn()} />)
    const currentPageBtn = screen.getByRole('button', { name: '2' })
    expect(currentPageBtn).toHaveClass('bg-accent')
  })

  it('respects windowSize parameter', () => {
    render(<Pagination page={5} totalPages={20} onPageChange={vi.fn()} windowSize={3} />)
    const buttons = screen.getAllByRole('button')
    const pageButtons = buttons.filter(btn => {
      const text = btn.textContent
      return text && /^\d+$/.test(text)
    })
    expect(pageButtons.length).toBe(3)
  })

  it('calls onPageChange when a page number is clicked', async () => {
    const user = userEvent.setup()
    const handleChange = vi.fn()
    render(<Pagination page={1} totalPages={5} onPageChange={handleChange} />)

    await user.click(screen.getByRole('button', { name: '3' }))

    expect(handleChange).toHaveBeenCalledWith(3)
  })

  it('renders correct page range at start', () => {
    render(<Pagination page={1} totalPages={10} onPageChange={vi.fn()} windowSize={5} />)
    expect(screen.getByRole('button', { name: '1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '5' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '6' })).not.toBeInTheDocument()
  })

  it('renders correct page range at end', () => {
    render(<Pagination page={10} totalPages={10} onPageChange={vi.fn()} windowSize={5} />)
    expect(screen.getByRole('button', { name: '6' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '10' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '5' })).not.toBeInTheDocument()
  })
})
