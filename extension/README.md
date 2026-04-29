# NovelTrans Chrome Extension

> A Chrome browser extension providing three AI-powered translation modes: full-page translation, reader mode, and selection translation.

## Features

### Three Translation Modes

| Mode | Shortcut | Description |
|------|----------|-------------|
| **Full-Page Translation** | `Ctrl+Shift+Y` / `Cmd+Shift+Y` | Traverses page DOM, extracts translatable text, progressively updates via SSE streaming |
| **Reader Mode** | Click extension icon → Reader Mode | Uses Readability to extract article HTML, translates and displays in a clean reading interface |
| **Selection Translation** | Select text with mouse | Listens for user text selection, shows translation in a floating panel |

### Technical Highlights

- **Manifest V3** — Uses Service Worker as the background script
- **DOM-Aware** — Intelligently identifies translatable text nodes, filters out navigation bars, ads, cookie popups, and other non-content areas
- **Progressive Rendering** — Receives translation results via SSE streaming, updates DOM nodes incrementally
- **Layout Preservation** — Injected translations maintain original DOM structure and CSS styles
- **IndexedDB Local Storage** — Uses `idb-simple` to cache translation results locally
- **Concurrency Control** — Uses `p-limit` to manage batch translation request concurrency
- **HTML Sanitization** — Uses DOMPurify for XSS protection

## Installation

### Developer Mode

1. Open Chrome and navigate to `chrome://extensions/`
2. Enable **Developer mode** in the top-right corner
3. Click **Load unpacked**
4. Select the `extension/` directory
5. The extension icon should appear in the browser toolbar

### Configuring the API

1. Click the extension icon → Settings
2. Enter the NovelTrans backend URL (default: `http://127.0.0.1:7341`)
3. Enter an API Key (optional; anonymous mode is available for unauthenticated users)
4. Save settings

## Project Structure

```
extension/
├── manifest.json           # Extension configuration (Manifest V3)
├── package.json            # Dependencies and scripts
├── jest.config.js          # Jest test configuration
├── src/
│   ├── background/
│   │   └── background.js   # Service Worker — message routing, API coordination
│   ├── content/
│   │   ├── content.js      # Full-page translation — DOM traversal, text extraction, result injection
│   │   ├── read.js         # Reader mode — Readability integration
│   │   ├── selection.js    # Selection translation — floating panel, event listening
│   │   ├── content-styles.css
│   │   └── selection-styles.css
│   ├── popup/
│   │   ├── popup.html      # Extension popup UI
│   │   └── popup.js
│   ├── options/
│   │   ├── options.html    # Settings page
│   │   └── welcome.html    # Welcome page
│   ├── lib/
│   │   ├── config.js       # Global configuration
│   │   ├── browser-polyfill.js
│   │   ├── Readability.js  # Article extraction
│   │   ├── purify.js       # HTML sanitization
│   │   └── vendor/
│   │       ├── idb-simple.js    # IndexedDB simplified library
│   │       └── p-limit.js       # Concurrency control
│   └── assets/
│       └── icons/            # Extension icons
└── tests/                    # Unit tests
```

## Development

```bash
# Install dependencies
npm install

# Run tests
npm test

# Build (if needed)
npm run build
```

## Architecture Data Flow

```
User triggers translation (shortcut / icon click)
  │
  ▼
Content Script traverses the DOM
  │ Extracts text nodes, generates {textId, originalText, context} mapping
  ▼
Service Worker receives batched text
  │ Sends via chrome.runtime.sendMessage
  ▼
HTTP POST to backend /v1/translate/webpage
  │ SSE streaming response
  ▼
Content Script receives results incrementally
  │ Locates DOM nodes by textId, replaces text content
  ▼
Page progressively updates, preserving original layout
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| Readability | Mozilla's article extraction algorithm |
| DOMPurify | HTML/XSS protection |
| idb-simple | IndexedDB local caching |
| p-limit | Request concurrency control |

---

**Last updated**: 2026-04-29
