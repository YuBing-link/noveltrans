import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Modal } from './Modal'

describe('Modal', () => {
  it('renders when open is true', () => {
    render(
      <Modal open onClose={vi.fn()} title="Test Modal">
        <p>Content</p>
      </Modal>
    )
    expect(screen.getByText('Test Modal')).toBeInTheDocument()
    expect(screen.getByText('Content')).toBeInTheDocument()
  })

  it('does not render when open is false', () => {
    render(
      <Modal open={false} onClose={vi.fn()} title="Test Modal">
        <p>Content</p>
      </Modal>
    )
    expect(screen.queryByText('Test Modal')).not.toBeInTheDocument()
    expect(screen.queryByText('Content')).not.toBeInTheDocument()
  })

  it('calls onClose when close button is clicked', async () => {
    const user = userEvent.setup()
    const handleClose = vi.fn()
    render(
      <Modal open onClose={handleClose} title="Close Me">
        <p>Content</p>
      </Modal>
    )

    const closeButtons = screen.getAllByRole('button')
    const xButton = closeButtons.find(btn => btn.textContent === '')
    if (xButton) await user.click(xButton)

    expect(handleClose).toHaveBeenCalled()
  })

  it('renders children', () => {
    render(
      <Modal open onClose={vi.fn()}>
        <div data-testid="child">Test Child</div>
      </Modal>
    )
    expect(screen.getByTestId('child')).toBeInTheDocument()
  })

  it.each<[string, string]>([
    ['sm', 'max-w-sm'],
    ['md', 'max-w-md'],
    ['lg', 'max-w-2xl'],
  ])('applies %s size class', (size, expectedClass) => {
    render(
      <Modal open onClose={vi.fn()} size={size as any}>
        <p>Content</p>
      </Modal>
    )
    const modalContent = screen.getByText('Content').closest('.relative')
    expect(modalContent).toHaveClass(expectedClass)
  })

  it('uses md size by default', () => {
    render(
      <Modal open onClose={vi.fn()}>
        <p>Content</p>
      </Modal>
    )
    const modalContent = screen.getByText('Content').closest('.relative')
    expect(modalContent).toHaveClass('max-w-md')
  })

  it('renders without title', () => {
    const { container } = render(
      <Modal open onClose={vi.fn()}>
        <p>No Title</p>
      </Modal>
    )
    expect(container.querySelector('h3')).not.toBeInTheDocument()
    expect(screen.getByText('No Title')).toBeInTheDocument()
  })
})
