// Mock browser APIs for extension testing
global.browser = {
  runtime: {
    sendMessage: jest.fn(),
    onMessage: { addListener: jest.fn() },
    onInstalled: { addListener: jest.fn() },
    getURL: (path) => `chrome-extension://mock${path}`,
    lastError: null,
  },
  storage: {
    local: {
      get: jest.fn(() => Promise.resolve({})),
      set: jest.fn(() => Promise.resolve()),
      remove: jest.fn(() => Promise.resolve()),
    },
    sync: {
      get: jest.fn(() => Promise.resolve({})),
      set: jest.fn(() => Promise.resolve()),
    },
    onChanged: {
      addListener: jest.fn(),
    },
  },
  tabs: {
    query: jest.fn(() => Promise.resolve([])),
    sendMessage: jest.fn(() => Promise.resolve()),
    onUpdated: { addListener: jest.fn() },
    onRemoved: { addListener: jest.fn() },
  },
  scripting: {
    executeScript: jest.fn(() => Promise.resolve()),
  },
  contextMenus: {
    create: jest.fn(() => Promise.resolve()),
    update: jest.fn(() => Promise.resolve()),
    removeAll: jest.fn(() => Promise.resolve()),
    onClicked: { addListener: jest.fn() },
  },
  commands: {
    onCommand: { addListener: jest.fn() },
  },
};

// Mock fetch for backend API tests
global.fetch = jest.fn();

// Mock IndexedDB for translation cache
global.indexedDB = {
  open: jest.fn(() => ({
    onupgradeneeded: null,
    onsuccess: null,
    onerror: null,
    result: {
      createObjectStore: jest.fn(() => ({
        createIndex: jest.fn(),
      })),
      objectStoreNames: {
        contains: jest.fn(() => false),
      },
      transaction: jest.fn(() => ({
        objectStore: jest.fn(() => ({
          get: jest.fn(() => ({ onsuccess: null, onerror: null, result: null })),
          put: jest.fn(() => ({ onsuccess: null, onerror: null, result: null })),
          delete: jest.fn(() => ({ onsuccess: null, onerror: null, result: null })),
          clear: jest.fn(() => ({ onsuccess: null, onerror: null, result: null })),
          getAll: jest.fn(() => ({ onsuccess: null, onerror: null, result: [] })),
          getAllKeys: jest.fn(() => ({ onsuccess: null, onerror: null, result: [] })),
        })),
      })),
      close: jest.fn(),
    },
  })),
};
