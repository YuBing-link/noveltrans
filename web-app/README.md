# NovelTrans Web App

> The NovelTrans React web dashboard — a DeepL-style translation experience with document upload, terminology management, and team collaboration.

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| React | 19 | UI framework |
| TypeScript | 6.0 | Type safety |
| Vite | 8.0 | Build tool |
| TailwindCSS | 4.2 | Styling system |
| React Router | 7 | Client-side routing |
| i18next | - | Internationalization (Chinese/English) |
| Lucide Icons | - | Icon library |
| Vitest | - | Unit testing |
| Playwright | - | E2E testing |

## Feature Modules

| Module | Description | Page File |
|--------|-------------|-----------|
| **Authentication** | Email registration, login, password reset, OTP verification | `LoginPage`, `RegisterPage` |
| **Translation Workspace** | Text translation, document translation (SSE streaming), history | `TranslatePage` |
| **Document Management** | Upload, translation task tracking, result download | `DocumentsPage` |
| **Glossary** | Create/manage terminology, improve translation consistency | `GlossaryPage` |
| **Collaboration Space** | Project management, chapter assignment, review workflow | `CollabPage` |
| **Subscription Center** | Stripe Checkout, payment status, quota viewing | `SubscriptionPage` |
| **User Center** | Profile, preferences, API key management | `UserCenterPage` |
| **Platform Stats** | Translation volume, active users, system health dashboard | `PlatformStatsPage` |

## Project Structure

```
web-app/
├── index.html
├── vite.config.ts
├── package.json
├── tsconfig.json
├── src/
│   ├── main.tsx              # Application entry point
│   ├── App.tsx               # Root component + route configuration
│   ├── api/                  # API client modules (15 modules)
│   │   ├── client.ts         # Axios instance configuration
│   │   ├── auth.ts           # Authentication API
│   │   ├── translate.ts      # Translation API
│   │   ├── documents.ts      # Document management API
│   │   ├── glossaries.ts     # Glossary API
│   │   ├── collab.ts         # Collaboration project API
│   │   ├── subscription.ts   # Subscription payment API
│   │   └── ...
│   ├── components/           # UI components
│   │   ├── layout/           # Layout components (sidebar, topbar)
│   │   ├── ui/               # Base components (Button/Input/Modal)
│   │   └── feature/          # Feature components
│   ├── context/              # React Context
│   │   ├── AuthContext.tsx   # User authentication state
│   │   └── ThemeContext.tsx  # Theme switching
│   ├── hooks/                # Custom Hooks
│   ├── pages/                # Page components (15+ pages)
│   ├── i18n/                 # Internationalization
│   │   └── locales/          # zh.json, en.json
│   └── types/                # TypeScript type definitions
└── tests/                    # Playwright E2E tests
    ├── extension-api.spec.ts
    ├── frontend-api.spec.ts
    ├── collab-workspace.spec.ts
    └── team-translation.spec.ts
```

## Development

```bash
# Install dependencies
npm install

# Start development server
npm run dev
# Access at http://localhost:5173

# Build for production
npm run build

# Preview production build
npm run preview

# Run unit tests
npm run test

# Run E2E tests
npx playwright test
```

## API Communication

The web app communicates with the backend through the Nginx gateway. In development mode, a Vite proxy is configured:

```ts
// vite.config.ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:7341',
      changeOrigin: true,
    },
  },
}
```

In production mode, Nginx serves the `dist/` static files directly and proxies API requests to the backend.

## State Management

| Layer | Solution | Description |
|-------|----------|-------------|
| Authentication | React Context (AuthContext) | JWT token, user info, login/logout |
| Theme | React Context (ThemeContext) | Light/dark mode toggle |
| Server State | Direct API calls + React state | Translation tasks, document lists, etc. |
| Form State | Controlled components | Login form, registration form, etc. |

## Internationalization

Supports Chinese (zh) and English (en) using i18next:

```ts
// Usage
import { useTranslation } from 'react-i18next'
const { t } = useTranslation()
// <h1>{t('auth.login')}</h1>
```

---

**Last updated**: 2026-04-29
