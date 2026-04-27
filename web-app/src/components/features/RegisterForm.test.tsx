import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { RegisterForm } from './RegisterForm'

const mockRegister = vi.fn()
const mockSendCode = vi.fn()
const mockToastSuccess = vi.fn()
const mockToastError = vi.fn()

vi.mock('../../hooks/useAuth', () => ({
  useAuth: () => ({ register: mockRegister, sendCode: mockSendCode }),
}))

vi.mock('../ui/Toast', () => ({
  useToast: () => ({ success: mockToastSuccess, error: mockToastError }),
}))

function renderRegisterForm() {
  return render(
    <MemoryRouter>
      <RegisterForm />
    </MemoryRouter>
  )
}

function getEmailInput() {
  // Input label is not associated via htmlFor, so find input near the label
  const emailLabel = screen.getByText(/register\.email/i)
  return emailLabel.closest('div.flex')?.querySelector('input[type="email"]') as HTMLInputElement
}

function getVerificationInput() {
  const label = screen.getByText(/register\.verificationCode/i)
  return label.closest('div.flex')?.querySelector('input') as HTMLInputElement
}

function getPasswordInput() {
  const label = screen.getByText(/register\.password/i)
  return label.closest('div.flex')?.querySelector('input') as HTMLInputElement
}

function getUsernameInput() {
  const label = screen.getByText(/register\.username/i)
  return label.closest('div.flex')?.querySelector('input') as HTMLInputElement
}

function getSubmitButton() {
  return screen.getAllByRole('button').find(b => b.textContent?.includes('register.submit'))
}

function getSendCodeButton() {
  // Send code button has 'bg-surface-secondary' class (secondary variant), unlike password toggle
  const buttons = screen.getAllByRole('button')
  return buttons.find(b => b.className.includes('bg-surface-secondary') || b.textContent === '60s' || b.textContent?.includes('register.sendCode'))
}

beforeEach(() => {
  mockRegister.mockReset()
  mockSendCode.mockReset()
  mockToastSuccess.mockReset()
  mockToastError.mockReset()
})

describe('RegisterForm', () => {
  it('renders all form fields', () => {
    renderRegisterForm()
    expect(screen.getByText(/register\.email/i)).toBeInTheDocument()
    expect(screen.getByText(/register\.verificationCode/i)).toBeInTheDocument()
    expect(screen.getByText(/register\.username/i)).toBeInTheDocument()
    expect(screen.getByText(/register\.password/i)).toBeInTheDocument()
  })

  it('renders submit and send code buttons', () => {
    renderRegisterForm()
    expect(getSubmitButton()).toBeInTheDocument()
    expect(getSendCodeButton()).toBeInTheDocument()
  })

  it('renders link to login page', () => {
    renderRegisterForm()
    expect(screen.getByRole('link', { name: /register\.loginNow/i })).toHaveAttribute('href', '/login')
  })

  it('shows error when sending code without email', () => {
    renderRegisterForm()
    fireEvent.click(getSendCodeButton()!)

    expect(mockToastError).toHaveBeenCalled()
    expect(mockSendCode).not.toHaveBeenCalled()
  })

  it('sends verification code when email is provided', async () => {
    mockSendCode.mockResolvedValueOnce(undefined)
    renderRegisterForm()

    fireEvent.change(getEmailInput(), { target: { value: 'test@example.com' } })
    fireEvent.click(getSendCodeButton()!)

    await waitFor(() => {
      expect(mockSendCode).toHaveBeenCalledWith('test@example.com')
    })
    await waitFor(() => {
      expect(mockToastSuccess).toHaveBeenCalled()
    })
  })

  it('disables send code button during countdown', async () => {
    mockSendCode.mockResolvedValueOnce(undefined)
    renderRegisterForm()

    fireEvent.change(getEmailInput(), { target: { value: 'test@example.com' } })
    fireEvent.click(getSendCodeButton()!)

    await waitFor(() => {
      expect(getSendCodeButton()).toBeDisabled()
      expect(getSendCodeButton()).toHaveTextContent(/60s/)
    })
  })

  it('calls register with correct data on submit', () => {
    mockRegister.mockResolvedValueOnce(undefined)
    renderRegisterForm()

    fireEvent.change(getEmailInput(), { target: { value: 'test@example.com' } })
    fireEvent.change(getVerificationInput(), { target: { value: '123456' } })
    fireEvent.change(getUsernameInput(), { target: { value: 'testuser' } })
    fireEvent.change(getPasswordInput(), { target: { value: 'password123' } })

    fireEvent.click(getSubmitButton()!)

    expect(mockRegister).toHaveBeenCalledWith(
      'test@example.com',
      'password123',
      '123456',
      'testuser'
    )
  })

  it('calls register with undefined username when empty', () => {
    mockRegister.mockResolvedValueOnce(undefined)
    renderRegisterForm()

    fireEvent.change(getEmailInput(), { target: { value: 'test@example.com' } })
    fireEvent.change(getVerificationInput(), { target: { value: '123456' } })
    fireEvent.change(getPasswordInput(), { target: { value: 'password123' } })

    fireEvent.click(getSubmitButton()!)

    expect(mockRegister).toHaveBeenCalledWith(
      'test@example.com',
      'password123',
      '123456',
      undefined
    )
  })

  it('shows error toast on register failure', async () => {
    mockRegister.mockRejectedValueOnce(new Error('Email already exists'))
    renderRegisterForm()

    fireEvent.change(getEmailInput(), { target: { value: 'test@example.com' } })
    fireEvent.change(getVerificationInput(), { target: { value: '123456' } })
    fireEvent.change(getPasswordInput(), { target: { value: 'password123' } })

    fireEvent.click(getSubmitButton()!)

    await waitFor(() => {
      expect(mockToastError).toHaveBeenCalledWith('Email already exists')
    })
  })
})
