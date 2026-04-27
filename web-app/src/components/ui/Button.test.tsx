import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Button } from './Button'

describe('Button', () => {
  it('renders with correct text', () => {
    render(<Button>Click me</Button>)
    expect(screen.getByRole('button', { name: /click me/i })).toBeInTheDocument()
  })

  it('applies primary variant by default', () => {
    render(<Button>Primary</Button>)
    const button = screen.getByRole('button')
    expect(button).toHaveClass('bg-accent')
  })

  it.each<[string, string]>([
    ['primary', 'bg-accent'],
    ['secondary', 'bg-surface-secondary'],
    ['ghost', 'bg-transparent'],
    ['danger', 'bg-red'],
    ['link', 'bg-transparent'],
  ])('applies %s variant class', (variant, expectedClass) => {
    render(<Button variant={variant as any}>{variant}</Button>)
    expect(screen.getByRole('button')).toHaveClass(expectedClass)
  })

  it.each<[string, string]>([
    ['sm', 'px-3 py-1.5 text-xs'],
    ['md', 'px-5 py-2 text-sm'],
    ['lg', 'px-7 py-2.5 text-base'],
  ])('applies %s size class', (size, expectedClass) => {
    render(<Button size={size as any}>{size}</Button>)
    expect(screen.getByRole('button')).toHaveClass(...expectedClass.split(' '))
  })

  it('is disabled when disabled prop is true', () => {
    render(<Button disabled>Disabled</Button>)
    expect(screen.getByRole('button')).toBeDisabled()
  })

  it('is disabled when loading prop is true', () => {
    render(<Button loading>Loading</Button>)
    expect(screen.getByRole('button')).toBeDisabled()
  })

  it('shows spinner when loading', () => {
    render(<Button loading>Loading</Button>)
    expect(screen.getByRole('button').querySelector('svg.animate-spin')).toBeInTheDocument()
  })

  it('does not show spinner when not loading', () => {
    render(<Button>Normal</Button>)
    expect(screen.queryByRole('button')?.querySelector('svg.animate-spin')).not.toBeInTheDocument()
  })

  it('calls onClick when clicked', async () => {
    const user = userEvent.setup()
    const handleClick = vi.fn()
    render(<Button onClick={handleClick}>Click</Button>)

    await user.click(screen.getByRole('button'))

    expect(handleClick).toHaveBeenCalledTimes(1)
  })

  it('does not call onClick when disabled', async () => {
    const user = userEvent.setup()
    const handleClick = vi.fn()
    render(<Button disabled onClick={handleClick}>Click</Button>)

    await user.click(screen.getByRole('button'))

    expect(handleClick).not.toHaveBeenCalled()
  })

  it('does not call onClick when loading', async () => {
    const user = userEvent.setup()
    const handleClick = vi.fn()
    render(<Button loading onClick={handleClick}>Click</Button>)

    await user.click(screen.getByRole('button'))

    expect(handleClick).not.toHaveBeenCalled()
  })

  it('applies custom className', () => {
    render(<Button className="custom-class">Custom</Button>)
    expect(screen.getByRole('button')).toHaveClass('custom-class')
  })

  it('forwards ref', () => {
    const ref = vi.fn()
    render(<Button ref={ref as any}>Ref</Button>)
    expect(ref).toHaveBeenCalledWith(expect.any(Element))
  })
})
