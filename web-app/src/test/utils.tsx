import { render, RenderOptions } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../components/ui/Toast'

interface AllProvidersProps {
  children: React.ReactNode
  initialEntries?: string[]
}

function AllProviders({ children, initialEntries = ['/'] }: AllProvidersProps) {
  return (
    <MemoryRouter initialEntries={initialEntries}>
      <ToastProvider>
        {children}
      </ToastProvider>
    </MemoryRouter>
  )
}

function renderWithProviders(
  ui: React.ReactElement,
  options?: RenderOptions & { initialEntries?: string[] }
) {
  const { initialEntries = ['/'], ...renderOptions } = options || {}
  return render(ui, {
    wrapper: ({ children }) => (
      <AllProviders initialEntries={initialEntries}>{children}</AllProviders>
    ),
    ...renderOptions,
  })
}

export { renderWithProviders }
