import '@testing-library/jest-dom'

import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

afterEach(() => {
  cleanup()
})

vi.mock('react-i18next', async () => {
  const { initReactI18next } = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, params?: Record<string, unknown>) => {
        const placeholder = params ? `__${Object.entries(params).map(([k, v]) => `${k}=${v}`).join('&')}__` : ''
        return key + (placeholder ? ` [${placeholder}]` : '')
      },
      i18n: {
        language: 'zh',
        t: (key: string) => key,
        changeLanguage: vi.fn(),
        resolvedLanguage: 'zh',
      },
    }),
    initReactI18next,
  }
})

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})
