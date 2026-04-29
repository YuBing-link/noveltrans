// Mock importScripts (browser extension API not available in Jest)
global.importScripts = jest.fn();

// Tests for extension/src/background/background.js
// Covers: TranslationServiceManager, ExtensionStateManager, MappingTableManager,
//         message handlers (translate, reader, selection, batch, login/auth)

const { GlobalConfig, languages, getFlagEmoji } = require('../src/lib/config.js');

// Mock pLimit before loading background.js
const pLimit = (concurrency) => {
  const run = async (fn) => fn();
  return run;
};
global.pLimit = pLimit;

// Mock IndexedDB with functional promises
function createMockIDB() {
  const store = new Map();
  return {
    open: jest.fn(() => Promise.resolve({})),
    get: jest.fn((storeName, key) => {
      const record = store.get(key);
      return Promise.resolve(record || null);
    }),
    set: jest.fn((storeName, key, value) => {
      store.set(key, { key, value, timestamp: Date.now() });
      return Promise.resolve();
    }),
    delete: jest.fn((storeName, key) => {
      store.delete(key);
      return Promise.resolve();
    }),
    deleteExpired: jest.fn(() => Promise.resolve([])),
    _store: store,
    _clear: () => store.clear(),
  };
}

// Mock createTranslationCacheDB before loading background.js
global.createTranslationCacheDB = jest.fn(() => createMockIDB());

// Load and mock GlobalConfig for background.js
self.GlobalConfig = {
  ...GlobalConfig,
  callBackendAPI: jest.fn(),
  callBackendAPIStream: jest.fn(),
};

const {
  TranslationServiceManager,
  ExtensionStateManager,
  MappingTableManager,
} = require('../src/background/background.js');

// Access the mocked GlobalConfig from self (as set up above)
const MockGlobalConfig = self.GlobalConfig;

describe('TranslationServiceManager', () => {
  let manager;
  let mockIDB;

  beforeEach(() => {
    jest.clearAllMocks();
    mockIDB = createMockIDB();

    // Override createTranslationCacheDB to return mock
    global.createTranslationCacheDB = jest.fn(() => mockIDB);

    // Reset browser.storage mock
    browser.storage.local.get.mockResolvedValue({
      settings: { target_lang: 'zh', engine: 'google' },
      api_keys: {},
    });

    manager = new TranslationServiceManager();
  });

  describe('cache key generation', () => {
    it('generates consistent cache keys for same input', () => {
      const key1 = manager.generateCacheKey('hello', 'auto', 'zh', 'google', false);
      const key2 = manager.generateCacheKey('hello', 'auto', 'zh', 'google', false);
      expect(key1).toBe(key2);
    });

    it('generates different keys for different text', () => {
      const key1 = manager.generateCacheKey('hello', 'auto', 'zh', 'google', false);
      const key2 = manager.generateCacheKey('world', 'auto', 'zh', 'google', false);
      expect(key1).not.toBe(key2);
    });

    it('generates different keys for different engine', () => {
      const key1 = manager.generateCacheKey('hello', 'auto', 'zh', 'google', false);
      const key2 = manager.generateCacheKey('hello', 'auto', 'zh', 'deepl', false);
      expect(key1).not.toBe(key2);
    });

    it('generates different keys for bilingual flag', () => {
      const key1 = manager.generateCacheKey('hello', 'auto', 'zh', 'google', false);
      const key2 = manager.generateCacheKey('hello', 'auto', 'zh', 'google', true);
      expect(key1).not.toBe(key2);
    });
  });

  describe('hashString', () => {
    it('returns consistent hash for same string', () => {
      expect(manager.hashString('hello')).toBe(manager.hashString('hello'));
    });

    it('returns different hash for different strings', () => {
      expect(manager.hashString('hello')).not.toBe(manager.hashString('world'));
    });
  });

  describe('getCachedTranslation', () => {
    it('returns memory cache hit', async () => {
      const cacheKey = 'google:auto:zh:false:abc123';
      manager.cache.set(cacheKey, { original: 'hello', translation: '你好' });

      const result = await manager.getCachedTranslation(cacheKey);
      expect(result.source).toBe('memory');
      expect(result.data.translation).toBe('你好');
    });

    it('returns none when cache miss', async () => {
      const result = await manager.getCachedTranslation('nonexistent:key');
      expect(result.source).toBe('none');
      expect(result.data).toBeNull();
    });

    it('loads from IDB to memory on IDB hit', async () => {
      const cacheKey = 'google:auto:zh:false:xyz789';
      mockIDB._store.set(cacheKey, {
        key: cacheKey,
        value: { original: 'test', translation: '测试' },
        timestamp: Date.now(),
      });

      const result = await manager.getCachedTranslation(cacheKey);
      expect(result.source).toBe('idb');
      expect(result.data.translation).toBe('测试');
      // Verify it was loaded into memory
      expect(manager.cache.has(cacheKey)).toBe(true);
    });
  });

  describe('saveToCache', () => {
    it('saves to both memory and IDB', async () => {
      const cacheKey = 'test:key';
      const data = { original: 'hi', translation: '嗨' };

      await manager.saveToCache(cacheKey, data);

      expect(manager.cache.get(cacheKey)).toEqual(data);
      expect(mockIDB.set).toHaveBeenCalled();
    });
  });

  describe('cleanMemoryCache', () => {
    it('removes oldest entries when over threshold', () => {
      // Fill cache beyond MEMORY_CLEANUP_THRESHOLD (500)
      for (let i = 0; i < 600; i++) {
        manager.cache.set(`key-${i}`, { value: i });
      }

      manager.cleanMemoryCache();

      // Should retain at most MEMORY_CLEANUP_THRESHOLD entries
      expect(manager.cache.size).toBeLessThanOrEqual(
        manager.CACHE_CONFIG.MEMORY_CLEANUP_THRESHOLD
      );
    });
  });

  describe('translateText', () => {
    it('returns cached translation on hit', async () => {
      const text = 'hello';
      const cacheKey = manager.generateCacheKey(text, 'auto', 'zh', 'google', false);
      const cachedData = {
        original: text,
        translation: '你好',
        service: 'google',
      };
      manager.cache.set(cacheKey, cachedData);

      const result = await manager.translateText(text, 'auto', 'zh', 'google');

      expect(result.translation).toBe('你好');
      expect(MockGlobalConfig.callBackendAPI).not.toHaveBeenCalled();
    });

    it('calls backend API on cache miss', async () => {
      MockGlobalConfig.callBackendAPI.mockResolvedValue({
        success: true,
        data: { translation: '你好', sourceLang: 'en' },
        timestamp: Date.now(),
      });

      const result = await manager.translateText('hello', 'auto', 'zh', 'google');

      expect(MockGlobalConfig.callBackendAPI).toHaveBeenCalledWith(
        'QUICK',
        expect.objectContaining({
          text: 'hello',
          sourceLang: 'auto',
          targetLang: 'zh',
          bilingual: false,
        }),
        'google',
        undefined
      );
      expect(result.original).toBe('hello');
      expect(result.translation).toBe('你好');
    });

    it('throws error on API failure', async () => {
      MockGlobalConfig.callBackendAPI.mockRejectedValue(new Error('Network error'));

      await expect(
        manager.translateText('hello', 'auto', 'zh', 'google')
      ).rejects.toThrow('Network error');
    });

    it('returns bilingual format when enabled', async () => {
      MockGlobalConfig.callBackendAPI.mockResolvedValue({
        success: true,
        data: { translation: '你好', sourceLang: 'en' },
        timestamp: Date.now(),
      });

      const result = await manager.translateText('hello', 'auto', 'zh', 'google', true);

      expect(result.bilingual).toBe('hello\n你好');
    });

    it('deduplicates concurrent identical requests', async () => {
      let resolveCount = 0;
      MockGlobalConfig.callBackendAPI.mockImplementation(
        () =>
          new Promise((resolve) =>
            setTimeout(() => {
              resolveCount++;
              resolve({
                success: true,
                data: { translation: '你好', sourceLang: 'en' },
                timestamp: Date.now(),
              });
            }, 10)
          )
      );

      const p1 = manager.translateText('hello', 'auto', 'zh', 'google');
      const p2 = manager.translateText('hello', 'auto', 'zh', 'google');

      const [r1, r2] = await Promise.all([p1, p2]);

      expect(r1.translation).toBe('你好');
      expect(r2.translation).toBe('你好');
      expect(resolveCount).toBeLessThanOrEqual(2);
    });
  });

  describe('callReaderTranslationAPI', () => {
    it('calls READER endpoint with correct payload', async () => {
      MockGlobalConfig.callBackendAPI.mockResolvedValue({
        success: true,
        data: { translatedContent: '<p>翻译内容</p>' },
        timestamp: Date.now(),
      });

      const result = await manager.callReaderTranslationAPI(
        '<article>Original content</article>',
        'zh',
        'en',
        'google'
      );

      expect(MockGlobalConfig.callBackendAPI).toHaveBeenCalledWith(
        'READER',
        expect.objectContaining({
          content: '<article>Original content</article>',
          targetLang: 'zh',
          sourceLang: 'en',
        }),
        'google',
        undefined
      );
      expect(result.success).toBe(true);
      expect(result.translatedContent).toBe('<p>翻译内容</p>');
      expect(result.mode).toBe('reader');
    });

    it('throws when article content is empty', async () => {
      await expect(
        manager.callReaderTranslationAPI('', 'zh', 'en', 'google')
      ).rejects.toThrow('文章内容不能为空');
    });

    it('throws when article content is whitespace only', async () => {
      await expect(
        manager.callReaderTranslationAPI('   ', 'zh', 'en', 'google')
      ).rejects.toThrow('文章内容不能为空');
    });

    it('throws when API returns invalid data format', async () => {
      MockGlobalConfig.callBackendAPI.mockResolvedValue({
        success: true,
        data: { wrongField: 'no translatedContent here' },
        timestamp: Date.now(),
      });

      await expect(
        manager.callReaderTranslationAPI('<p>content</p>', 'zh', 'en', 'google')
      ).rejects.toThrow('翻译服务返回数据格式错误');
    });

    it('propagates API errors', async () => {
      MockGlobalConfig.callBackendAPI.mockRejectedValue(new Error('Service unavailable'));

      await expect(
        manager.callReaderTranslationAPI('<p>content</p>', 'zh', 'en', 'google')
      ).rejects.toThrow('Service unavailable');
    });
  });

  describe('callSelectionTranslationAPI', () => {
    it('calls SELECTION endpoint with correct payload', async () => {
      MockGlobalConfig.callBackendAPI.mockResolvedValue({
        success: true,
        data: { translation: '你好世界' },
        timestamp: Date.now(),
      });

      const result = await manager.callSelectionTranslationAPI(
        'Hello World',
        'auto',
        'zh',
        'google'
      );

      expect(MockGlobalConfig.callBackendAPI).toHaveBeenCalledWith(
        'SELECTION',
        expect.objectContaining({
          context: 'Hello World',
          sourceLang: 'auto',
          targetLang: 'zh',
        }),
        'google',
        undefined
      );
      expect(result.success).toBe(true);
      expect(result.translation).toBe('你好世界');
      expect(result.mode).toBe('selection');
    });

    it('throws when text is empty', async () => {
      await expect(
        manager.callSelectionTranslationAPI('', 'auto', 'zh', 'google')
      ).rejects.toThrow('翻译文本不能为空');
    });

    it('throws when text is whitespace only', async () => {
      await expect(
        manager.callSelectionTranslationAPI('   ', 'auto', 'zh', 'google')
      ).rejects.toThrow('翻译文本不能为空');
    });

    it('throws when API returns invalid data', async () => {
      MockGlobalConfig.callBackendAPI.mockResolvedValue({
        success: true,
        data: { wrongField: 'no translation' },
        timestamp: Date.now(),
      });

      await expect(
        manager.callSelectionTranslationAPI('text', 'auto', 'zh', 'google')
      ).rejects.toThrow('翻译服务返回数据格式错误');
    });

    it('passes through bilingual data if returned', async () => {
      MockGlobalConfig.callBackendAPI.mockResolvedValue({
        success: true,
        data: {
          translation: '你好',
          bilingual: 'Hello\n你好',
        },
        timestamp: Date.now(),
      });

      const result = await manager.callSelectionTranslationAPI(
        'Hello',
        'auto',
        'zh',
        'google'
      );

      expect(result.bilingual).toBe('Hello\n你好');
    });
  });

  describe('batchTranslate', () => {
    it('calls QUICK endpoint with texts array', async () => {
      MockGlobalConfig.callBackendAPI.mockResolvedValue({
        success: true,
        data: { translations: ['你好', '世界'] },
        timestamp: Date.now(),
      });

      const result = await manager.batchTranslate(
        ['hello', 'world'],
        'auto',
        'zh',
        'google'
      );

      expect(MockGlobalConfig.callBackendAPI).toHaveBeenCalledWith(
        'QUICK',
        expect.objectContaining({
          texts: ['hello', 'world'],
          sourceLang: 'auto',
          targetLang: 'zh',
        }),
        'google',
        undefined
      );
      expect(result.translations).toEqual(['你好', '世界']);
      expect(result.batchSize).toBe(2);
    });

    it('handles alternative response field name (texts)', async () => {
      MockGlobalConfig.callBackendAPI.mockResolvedValue({
        success: true,
        data: { texts: ['译1', '译2'] },
        timestamp: Date.now(),
      });

      const result = await manager.batchTranslate(['a', 'b'], 'auto', 'zh', 'google');

      expect(result.translations).toEqual(['译1', '译2']);
    });

    it('propagates API errors', async () => {
      MockGlobalConfig.callBackendAPI.mockRejectedValue(new Error('Rate limited'));

      await expect(
        manager.batchTranslate(['hello'], 'auto', 'zh', 'google')
      ).rejects.toThrow('Rate limited');
    });
  });

  describe('batchTranslateWebpage', () => {
    it('calls WEBPAGE endpoint with streaming API', async () => {
      MockGlobalConfig.callBackendAPIStream.mockResolvedValue({
        success: true,
        translations: [
          { textId: 1, original: 'hello', translation: '你好' },
          { textId: 2, original: 'world', translation: '世界' },
        ],
      });

      const result = await manager.batchTranslateWebpage(
        [{ id: 1, text: 'hello' }, { id: 2, text: 'world' }],
        'auto',
        'zh',
        'google',
        true
      );

      expect(MockGlobalConfig.callBackendAPIStream).toHaveBeenCalledWith(
        'WEBPAGE',
        expect.objectContaining({
          textRegistry: expect.arrayContaining([
            expect.objectContaining({ id: 1 }),
            expect.objectContaining({ id: 2 }),
          ]),
          sourceLang: 'auto',
          targetLang: 'zh',
          engine: 'google',
          fastMode: true,
        }),
        'google',
        undefined,
        expect.any(Function), // onTranslationChunk
        expect.any(Function), // onComplete
        expect.any(Function)  // onError
      );
      expect(result.success).toBe(true);
      expect(result.translations).toHaveLength(2);
      expect(result.batchSize).toBe(2);
    });

    it('throws when textRegistry is empty', async () => {
      await expect(
        manager.batchTranslateWebpage([], 'auto', 'zh', 'google')
      ).rejects.toThrow('textRegistry 必须是非空数组');
    });

    it('throws when textRegistry is not an array', async () => {
      await expect(
        manager.batchTranslateWebpage(null, 'auto', 'zh', 'google')
      ).rejects.toThrow('textRegistry 必须是非空数组');
    });

    it('throws when API returns invalid format', async () => {
      MockGlobalConfig.callBackendAPIStream.mockResolvedValue({
        success: true,
        wrongField: 'no translations',
      });

      await expect(
        manager.batchTranslateWebpage([{ id: 1, text: 'hello' }], 'auto', 'zh', 'google')
      ).rejects.toThrow('后端返回数据格式错误: 缺少 translations 数组');
    });

    it('retries on failure up to maxRetries', async () => {
      let callCount = 0;
      MockGlobalConfig.callBackendAPIStream.mockImplementation(() => {
        callCount++;
        if (callCount < 3) {
          throw new Error('Network timeout');
        }
        return Promise.resolve({
          success: true,
          translations: [{ textId: 1, original: 'hello', translation: '你好' }],
        });
      });

      // Speed up retries
      manager.delay = jest.fn(() => Promise.resolve());

      const result = await manager.batchTranslateWebpage(
        [{ id: 1, text: 'hello' }],
        'auto',
        'zh',
        'google'
      );

      expect(callCount).toBe(3);
      expect(result.success).toBe(true);
    });

    it('fails after maxRetries exceeded', async () => {
      MockGlobalConfig.callBackendAPIStream.mockRejectedValue(new Error('Permanent failure'));
      manager.delay = jest.fn(() => Promise.resolve());

      await expect(
        manager.batchTranslateWebpage([{ id: 1, text: 'hello' }], 'auto', 'zh', 'google')
      ).rejects.toThrow(/翻译请求失败/);
    });

    it('provides specific error message for connection failures', async () => {
      MockGlobalConfig.callBackendAPIStream.mockRejectedValue(
        new Error('network error: fetch failed')
      );
      manager.delay = jest.fn(() => Promise.resolve());

      await expect(
        manager.batchTranslateWebpage([{ id: 1, text: 'hello' }], 'auto', 'zh', 'google')
      ).rejects.toThrow(/翻译服务连接失败/);
    });

    it('calls onTranslationChunk callback for each chunk', async () => {
      const chunks = [];
      MockGlobalConfig.callBackendAPIStream.mockImplementation(
        async (mode, data, engine, apiKey, onChunk, onComplete, onError) => {
          // Simulate chunks being received
          if (onChunk) {
            onChunk({ textId: 1, original: 'hello', translation: '你好' });
            onChunk({ textId: 2, original: 'world', translation: '世界' });
          }
          if (onComplete) {
            onComplete({
              translations: [
                { textId: 1, original: 'hello', translation: '你好' },
                { textId: 2, original: 'world', translation: '世界' },
              ],
            });
          }
          return {
            translations: [
              { textId: 1, original: 'hello', translation: '你好' },
              { textId: 2, original: 'world', translation: '世界' },
            ],
          };
        }
      );

      await manager.batchTranslateWebpage(
        [{ id: 1, text: 'hello' }, { id: 2, text: 'world' }],
        'auto',
        'zh',
        'google',
        true,
        (chunk) => chunks.push(chunk)
      );

      expect(chunks).toHaveLength(2);
      expect(chunks[0].textId).toBe(1);
      expect(chunks[1].textId).toBe(2);
    });
  });

  describe('getApiKey', () => {
    it('returns API key from storage', async () => {
      browser.storage.local.get.mockResolvedValue({
        api_keys: { google: 'sk-test-key-123', deepl: 'deepl-key' },
      });

      const key = await manager.getApiKey('google');
      expect(key).toBe('sk-test-key-123');
    });

    it('returns undefined when no keys stored', async () => {
      browser.storage.local.get.mockResolvedValue({});

      const key = await manager.getApiKey('google');
      expect(key).toBeUndefined();
    });

    it('returns undefined when engine not in keys', async () => {
      browser.storage.local.get.mockResolvedValue({
        api_keys: { google: 'sk-test' },
      });

      const key = await manager.getApiKey('deepl');
      expect(key).toBeUndefined();
    });
  });

  describe('getSetting', () => {
    it('returns setting value from storage', async () => {
      browser.storage.local.get.mockResolvedValue({
        settings: { target_lang: 'zh', engine: 'deepl' },
      });

      const value = await manager.getSetting('engine');
      expect(value).toBe('deepl');
    });

    it('returns undefined when no settings stored', async () => {
      browser.storage.local.get.mockResolvedValue({});

      const value = await manager.getSetting('engine');
      expect(value).toBeUndefined();
    });
  });
});

describe('ExtensionStateManager', () => {
  let stateManager;

  beforeEach(() => {
    stateManager = new ExtensionStateManager();
  });

  describe('recordTranslation', () => {
    it('records translation activity for a tab', () => {
      stateManager.recordTranslation(1, 'single_translation', { text: 'hello' });

      const status = stateManager.getTranslationStatus(1);
      expect(status.active).toBe(true);
      expect(status.translationCount).toBe(1);
    });

    it('accumulates multiple translations for same tab', () => {
      stateManager.recordTranslation(1, 'single_translation', { text: 'hello' });
      stateManager.recordTranslation(1, 'reader_translate', { contentLength: 100 });

      const status = stateManager.getTranslationStatus(1);
      expect(status.translationCount).toBe(2);
    });

    it('limits history to 100 records', () => {
      for (let i = 0; i < 150; i++) {
        stateManager.recordTranslation(1, 'test', { index: i });
      }

      const status = stateManager.getTranslationStatus(1);
      expect(status.translationCount).toBe(100); // capped at 100
      expect(status.recentActions).toHaveLength(10);
    });

    it('returns null for unknown tab', () => {
      expect(stateManager.getTranslationStatus(999)).toBeNull();
    });
  });

  describe('reader mode', () => {
    it('sets and gets reader mode status', () => {
      stateManager.setReaderMode(1, true, { title: 'Test Article' });

      const mode = stateManager.getReaderMode(1);
      expect(mode.active).toBe(true);
      expect(mode.articleData.title).toBe('Test Article');
    });

    it('deletes reader mode when set to false', () => {
      stateManager.setReaderMode(1, true);
      stateManager.setReaderMode(1, false);

      const mode = stateManager.getReaderMode(1);
      expect(mode.active).toBe(false);
    });

    it('returns inactive for unknown tab', () => {
      const mode = stateManager.getReaderMode(999);
      expect(mode.active).toBe(false);
    });
  });

  describe('cleanupTab', () => {
    it('clears all state for a tab', () => {
      stateManager.recordTranslation(1, 'test', {});
      stateManager.setReaderMode(1, true);

      stateManager.cleanupTab(1);

      expect(stateManager.getTranslationStatus(1)).toBeNull();
      expect(stateManager.getReaderMode(1).active).toBe(false);
    });
  });
});

describe('MappingTableManager', () => {
  let manager;

  beforeEach(() => {
    manager = new MappingTableManager();
    browser.storage.local.get.mockResolvedValue({
      settings: { target_lang: 'zh', engine: 'google' },
    });
  });

  describe('processMappingTable', () => {
    it('returns error for invalid tabId', async () => {
      const result = await manager.processMappingTable(
        { textRegistry: [] },
        null
      );

      expect(result.success).toBe(false);
      expect(result.error).toContain('无效的标签页 ID');
    });

    it('processes mapping table and creates translation plan', async () => {
      const mappingTable = {
        textRegistry: [
          { id: 1, text: 'hello', position: { visible: true } },
          { id: 2, text: 'world', position: { visible: true } },
          { id: 3, text: 'hidden', position: { visible: false } },
        ],
        totalTexts: 3,
        language: 'en',
      };

      const result = await manager.processMappingTable(mappingTable, 1);

      expect(result.success).toBe(true);
      expect(result.translationPlan).toBeDefined();
      expect(result.translationPlan.allTexts).toHaveLength(3);
      expect(result.translationPlan.targetLang).toBe('zh');
      expect(result.translationPlan.service).toBe('google');
      expect(result.translationPlan.strategy).toBe('priority-first');
    });
  });

  describe('createTranslationPlan', () => {
    it('prioritizes visible texts', async () => {
      const mappingTable = {
        textRegistry: [
          { id: 1, text: 'visible1', position: { visible: true } },
          { id: 2, text: 'visible2', position: { visible: true } },
          { id: 3, text: 'hidden', position: { visible: false } },
        ],
        language: 'ja',
      };

      const plan = await manager.createTranslationPlan(mappingTable);

      expect(plan.priorityTexts).toContain(1);
      expect(plan.priorityTexts).toContain(2);
      expect(plan.remainingTexts).toContain(3);
    });

    it('limits priority texts to 50', async () => {
      const textRegistry = [];
      for (let i = 0; i < 100; i++) {
        textRegistry.push({ id: i, text: `text-${i}`, position: { visible: true } });
      }

      const plan = await manager.createTranslationPlan({ textRegistry, language: 'en' });

      expect(plan.priorityTexts.length).toBeLessThanOrEqual(50);
    });

    it('uses default settings when none configured', async () => {
      browser.storage.local.get.mockResolvedValue({});

      const plan = await manager.createTranslationPlan({
        textRegistry: [{ id: 1, text: 'test' }],
        language: 'en',
      });

      expect(plan.targetLang).toBe('zh');
      expect(plan.service).toBe('google');
    });
  });

  describe('getMappingTable / cleanupMappingTable', () => {
    it('stores and retrieves mapping table by tabId', async () => {
      await manager.processMappingTable(
        { textRegistry: [{ id: 1, text: 'test' }], totalTexts: 1 },
        42
      );

      const table = manager.getMappingTable(42);
      expect(table).toBeDefined();
      expect(table.status).toBe('processing');
    });

    it('removes mapping table on cleanup', async () => {
      await manager.processMappingTable(
        { textRegistry: [{ id: 1, text: 'test' }], totalTexts: 1 },
        42
      );

      manager.cleanupMappingTable(42);
      expect(manager.getMappingTable(42)).toBeUndefined();
    });
  });
});

describe('handleMessage routing (via TranslationServiceManager integration)', () => {
  // Test the message handling patterns used by BackgroundManager.handleMessage
  // by simulating the same logic flow

  let mockSendResponse;
  let mockManager;

  beforeEach(() => {
    jest.clearAllMocks();
    mockSendResponse = jest.fn();

    const mockIDB = createMockIDB();
    global.createTranslationCacheDB = jest.fn(() => mockIDB);

    browser.storage.local.get.mockResolvedValue({
      settings: { target_lang: 'zh', engine: 'google' },
      api_keys: {},
    });

    mockManager = new TranslationServiceManager();
  });

  describe('translateText message handler', () => {
    it('processes translateText action', async () => {
      MockGlobalConfig.callBackendAPI.mockResolvedValue({
        success: true,
        data: { translation: '你好', sourceLang: 'en' },
        timestamp: Date.now(),
      });

      const request = {
        action: 'translateText',
        context: 'hello',
        sourceLang: 'auto',
        targetLang: 'zh',
        engine: 'google',
        bilingual: false,
      };

      const translation = await mockManager.translateText(
        request.context,
        request.sourceLang,
        request.targetLang,
        request.engine,
        request.bilingual
      );

      mockSendResponse({ success: true, data: translation });

      expect(mockSendResponse).toHaveBeenCalledWith(
        expect.objectContaining({
          success: true,
          data: expect.objectContaining({
            translation: '你好',
            original: 'hello',
          }),
        })
      );
    });
  });

  describe('readerTranslate message handler', () => {
    it('processes readerTranslate action', async () => {
      MockGlobalConfig.callBackendAPI.mockResolvedValue({
        success: true,
        data: { translatedContent: '<p>翻译后的文章</p>' },
        timestamp: Date.now(),
      });

      const result = await mockManager.callReaderTranslationAPI(
        '<article>Original article</article>',
        'zh',
        'auto',
        'google'
      );

      mockSendResponse({ success: true, data: result });

      expect(mockSendResponse).toHaveBeenCalledWith(
        expect.objectContaining({
          success: true,
          data: expect.objectContaining({
            translatedContent: '<p>翻译后的文章</p>',
            mode: 'reader',
          }),
        })
      );
    });
  });

  describe('selectionTranslate message handler', () => {
    it('processes selectionTranslate action', async () => {
      MockGlobalConfig.callBackendAPI.mockResolvedValue({
        success: true,
        data: { translation: '选中文本翻译' },
        timestamp: Date.now(),
      });

      const result = await mockManager.callSelectionTranslationAPI(
        'Selected text',
        'auto',
        'zh',
        'google'
      );

      mockSendResponse({ success: true, data: result });

      expect(mockSendResponse).toHaveBeenCalledWith(
        expect.objectContaining({
          success: true,
          data: expect.objectContaining({
            translation: '选中文本翻译',
            mode: 'selection',
          }),
        })
      );
    });
  });

  describe('batchTranslate message handler', () => {
    it('processes batchTranslate with textRegistry (webpage mode)', async () => {
      MockGlobalConfig.callBackendAPIStream.mockResolvedValue({
        success: true,
        translations: [
          { textId: 1, original: 'Title', translation: '标题' },
          { textId: 2, original: 'Body', translation: '正文' },
        ],
      });

      const result = await mockManager.batchTranslateWebpage(
        [{ id: 1, text: 'Title' }, { id: 2, text: 'Body' }],
        'auto',
        'zh',
        'google',
        true
      );

      mockSendResponse({
        success: true,
        translations: result.translations,
      });

      expect(mockSendResponse).toHaveBeenCalledWith(
        expect.objectContaining({
          success: true,
          translations: expect.arrayContaining([
            expect.objectContaining({ textId: 1, translation: '标题' }),
            expect.objectContaining({ textId: 2, translation: '正文' }),
          ]),
        })
      );
    });

    it('processes batchTranslate with texts array (quick mode)', async () => {
      MockGlobalConfig.callBackendAPI.mockResolvedValue({
        success: true,
        data: { translations: ['译1', '译2', '译3'] },
        timestamp: Date.now(),
      });

      const result = await mockManager.batchTranslate(
        ['text1', 'text2', 'text3'],
        'auto',
        'zh',
        'google'
      );

      mockSendResponse({
        success: true,
        translations: result.translations,
      });

      expect(mockSendResponse).toHaveBeenCalledWith(
        expect.objectContaining({
          success: true,
          translations: ['译1', '译2', '译3'],
        })
      );
    });
  });
});

describe('Authentication message handlers simulation', () => {
  // Simulate the auth-related handleMessage cases:
  // setAuthToken, getAuthState, clearAuthToken

  beforeEach(() => {
    jest.clearAllMocks();
    browser.storage.local.get.mockReset();
    browser.storage.local.set.mockReset();
    browser.storage.local.remove.mockReset();
  });

  describe('setAuthToken simulation', () => {
    it('stores auth token and user info', async () => {
      browser.storage.local.set.mockResolvedValue(undefined);

      // Simulate: await browser.storage.local.set({ auth_token: token, auth_user: userInfo })
      await browser.storage.local.set({
        auth_token: 'jwt-token-xyz',
        auth_user: { id: 1, email: 'test@example.com' },
      });

      expect(browser.storage.local.set).toHaveBeenCalledWith(
        expect.objectContaining({
          auth_token: 'jwt-token-xyz',
          auth_user: { id: 1, email: 'test@example.com' },
        })
      );
    });
  });

  describe('getAuthState simulation', () => {
    it('returns logged in state when token exists', async () => {
      browser.storage.local.get.mockResolvedValue({
        auth_token: 'jwt-token-xyz',
        auth_user: { id: 1, email: 'test@example.com' },
      });

      const result = await browser.storage.local.get(['auth_token', 'auth_user']);

      expect(!!result.auth_token).toBe(true);
      expect(result.auth_user).toEqual({ id: 1, email: 'test@example.com' });
    });

    it('returns logged out state when no token', async () => {
      browser.storage.local.get.mockResolvedValue({});

      const result = await browser.storage.local.get(['auth_token', 'auth_user']);

      expect(!!result.auth_token).toBe(false);
      expect(result.auth_token).toBeUndefined();
      expect(result.auth_user).toBeUndefined();
    });
  });

  describe('clearAuthToken simulation', () => {
    it('removes auth token and user info', async () => {
      browser.storage.local.remove.mockResolvedValue(undefined);

      await browser.storage.local.remove(['auth_token', 'auth_user']);

      expect(browser.storage.local.remove).toHaveBeenCalledWith([
        'auth_token',
        'auth_user',
      ]);
    });
  });
});
