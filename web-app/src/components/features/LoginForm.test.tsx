import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { LoginForm } from './LoginForm'

const mockLogin = vi.fn()
const mockToastError = vi.fn()

vi.mock('../../hooks/useAuth', () => ({
  useAuth: () => ({ login: mockLogin }),
}))

vi.mock('../ui/Toast', () => ({
  useToast: () => ({ error: mockToastError }),
}))

function renderLoginForm() {
  return render(
    <MemoryRouter>
      <LoginForm />
    </MemoryRouter>
  )
}

function getEmailInput() {
  const label = screen.getByText(/login\.email/i)
  return label.closest('div.flex')?.querySelector('input[type="email"]') as HTMLInputElement
}

function getPasswordInput() {
  const label = screen.getByText(/login\.password/i)
  return label.closest('div.flex')?.querySelector('input') as HTMLInputElement
}

function getSubmitButton() {
  return screen.getAllByRole('button').find(b => b.textContent?.includes('login.submit'))
}

function getPasswordToggle() {
  // The password toggle is the button inside the relative div, not the submit button
  const passwordField = getPasswordInput()
  return passwordField.closest('div.relative')?.querySelector('button[type="button"]') as HTMLButtonElement
}

beforeEach(() => {
  mockLogin.mockReset()
  mockToastError.mockReset()
})

describe('LoginForm', () => {
  it('renders email and password fields', () => {
    renderLoginForm()
    expect(screen.getByText(/login\.email/i)).toBeInTheDocument()
    expect(screen.getByText(/login\.password/i)).toBeInTheDocument()
  })

  it('renders submit button', () => {
    renderLoginForm()
    expect(getSubmitButton()).toBeInTheDocument()
  })

  it('renders link to register page', () => {
    renderLoginForm()
    expect(screen.getByRole('link', { name: /login\.registerNow/i })).toHaveAttribute('href', '/register')
  })

  it('renders link to forgot password page', () => {
    renderLoginForm()
    expect(screen.getByRole('link', { name: /login\.forgotPassword/i })).toHaveAttribute('href', '/forgot-password')
  })

  it('toggles password visibility', () => {
    renderLoginForm()
    const passwordInput = getPasswordInput()
    expect(passwordInput).toHaveAttribute('type', 'password')

    fireEvent.click(getPasswordToggle())

    expect(passwordInput).toHaveAttribute('type', 'text')
  })

  it('calls login with email and password on submit', () => {
    mockLogin.mockResolvedValueOnce(undefined)
    renderLoginForm()

    fireEvent.change(getEmailInput(), { target: { value: 'test@example.com' } })
    fireEvent.change(getPasswordInput(), { target: { value: 'password123' } })
    fireEvent.click(getSubmitButton()!)

    expect(mockLogin).toHaveBeenCalledWith('test@example.com', 'password123')
  })

  it('shows loading state during login', () => {
    mockLogin.mockImplementationOnce(() => new Promise(resolve => setTimeout(resolve, 100)))
    renderLoginForm()

    fireEvent.change(getEmailInput(), { target: { value: 'test@example.com' } })
    fireEvent.change(getPasswordInput(), { target: { value: 'password123' } })
    fireEvent.click(getSubmitButton()!)

    expect(getSubmitButton()).toBeDisabled()
  })

  it('shows error toast on login failure', async () => {
    mockLogin.mockRejectedValueOnce(new Error('Invalid credentials'))
    renderLoginForm()

    fireEvent.change(getEmailInput(), { target: { value: 'test@example.com' } })
    fireEvent.change(getPasswordInput(), { target: { value: 'wrong' } })
    fireEvent.click(getSubmitButton()!)

    await waitFor(() => {
      expect(mockToastError).toHaveBeenCalledWith('Invalid credentials')
    })
  })
})
