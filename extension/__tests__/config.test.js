// Tests for extension/src/lib/config.js
const { GlobalConfig, languages, getFlagEmoji } = require('../src/lib/config.js');

describe('GlobalConfig', () => {
  beforeEach(() => {
    global.fetch.mockReset();
  });

  describe('getApiUrl', () => {
    it('returns correct URL for WEBPAGE mode', () => {
      expect(GlobalConfig.getApiUrl('WEBPAGE')).toBe(
        'http://localhost:7341/v1/translate/webpage'
      );
    });

    it('returns correct URL for READER mode', () => {
      expect(GlobalConfig.getApiUrl('READER')).toBe(
        'http://localhost:7341/v1/translate/reader'
      );
    });

    it('returns correct URL for SELECTION mode', () => {
      expect(GlobalConfig.getApiUrl('SELECTION')).toBe(
        'http://localhost:7341/v1/translate/selection'
      );
    });

    it('returns fallback URL for unknown mode', () => {
      const consoleSpy = jest.spyOn(console, 'error').mockImplementation();
      expect(GlobalConfig.getApiUrl('UNKNOWN')).toBe(
        'http://localhost:7341/translate/quick'
      );
      consoleSpy.mockRestore();
    });
  });

  describe('getEngineConfig', () => {
    it('returns config for valid engine', () => {
      const config = GlobalConfig.getEngineConfig('google');
      expect(config.name).toBe('Google 翻译');
      expect(config.color).toBe('#4285f4');
    });

    it('falls back to google for unknown engine', () => {
      const config = GlobalConfig.getEngineConfig('unknown_engine');
      expect(config.id).toBe('google');
    });
  });

  describe('getAllEngines', () => {
    it('returns all engines', () => {
      const engines = GlobalConfig.getAllEngines();
      expect(engines.length).toBe(7);
      expect(engines.map(e => e.id)).toContain('google');
      expect(engines.map(e => e.id)).toContain('deepl');
    });
  });

  describe('buildTranslationRequestBody', () => {
    it('builds WEBPAGE request body', () => {
      const body = GlobalConfig.buildTranslationRequestBody('WEBPAGE', {
        textRegistry: [{ id: 1, text: 'hello' }],
        fastMode: true,
      }, 'deepl');

      expect(body).toEqual({
        targetLang: 'zh',
        sourceLang: 'auto',
        engine: 'deepl',
        fastMode: true,
        textRegistry: [{ id: 1, text: 'hello' }],
      });
    });

    it('builds READER request body', () => {
      const body = GlobalConfig.buildTranslationRequestBody('READER', {
        content: '<article>test</article>',
        targetLang: 'en',
      }, 'google');

      expect(body).toEqual({
        content: '<article>test</article>',
        targetLang: 'en',
        sourceLang: 'auto',
        engine: 'google',
      });
    });

    it('builds SELECTION request body', () => {
      const body = GlobalConfig.buildTranslationRequestBody('SELECTION', {
        text: 'selected text',
        targetLang: 'ja',
      }, 'openai');

      expect(body).toEqual({
        sourceLang: 'auto',
        targetLang: 'ja',
        engine: 'openai',
        context: 'selected text',
      });
    });

    it('builds QUICK request body', () => {
      const body = GlobalConfig.buildTranslationRequestBody('QUICK', {
        text: 'quick text',
        targetLang: 'fr',
        bilingual: true,
      }, 'baidu');

      expect(body).toEqual({
        engine: 'baidu',
        sourceLang: 'auto',
        targetLang: 'fr',
        text: 'quick text',
        texts: undefined,
        bilingual: true,
      });
    });

    it('uses default engine when not specified', () => {
      const body = GlobalConfig.buildTranslationRequestBody('QUICK', {
        text: 'hello',
      });

      expect(body.engine).toBe('google');
    });
  });

  describe('callBackendAPI', () => {
    it('sends correct request and returns result', async () => {
      global.fetch.mockResolvedValue({
        ok: true,
        json: async () => ({ success: true, translated: '你好' }),
      });

      const result = await GlobalConfig.callBackendAPI('SELECTION', {
        text: 'hello',
        targetLang: 'zh',
      }, 'google');

      expect(global.fetch).toHaveBeenCalled();
      const callArgs = global.fetch.mock.calls[0];
      expect(callArgs[1].method).toBe('POST');

      const requestBody = JSON.parse(callArgs[1].body);
      expect(requestBody.context).toBe('hello');
      expect(requestBody.engine).toBe('google');

      expect(result.success).toBe(true);
      expect(result.engine).toBe('google');
    });

    it('throws on API error', async () => {
      global.fetch.mockResolvedValue({
        ok: false,
        status: 500,
        text: async () => 'Internal Server Error',
        statusText: 'Internal Server Error',
      });

      await expect(
        GlobalConfig.callBackendAPI('SELECTION', { text: 'hello' })
      ).rejects.toThrow('API 错误 (500)');
    });

    it('adds Authorization header when apiKey provided', async () => {
      global.fetch.mockResolvedValue({
        ok: true,
        json: async () => ({ success: true }),
      });

      await GlobalConfig.callBackendAPI('SELECTION', { text: 'hello' }, 'google', 'test-key');

      const headers = global.fetch.mock.calls[0][1].headers;
      expect(headers['Authorization']).toBe('Bearer test-key');
    });
  });

  describe('constants', () => {
    it('has correct ENGINES mapping', () => {
      expect(GlobalConfig.ENGINES.GOOGLE).toBe('google');
      expect(GlobalConfig.ENGINES.DEEPL).toBe('deepl');
      expect(GlobalConfig.ENGINES.OPENAI).toBe('openai');
    });

    it('has correct TRANSLATION_MODES', () => {
      expect(GlobalConfig.TRANSLATION_MODES.WEBPAGE.endpoint).toBe('/v1/translate/webpage');
      expect(GlobalConfig.TRANSLATION_MODES.READER.endpoint).toBe('/v1/translate/reader');
      expect(GlobalConfig.TRANSLATION_MODES.SELECTION.endpoint).toBe('/v1/translate/selection');
    });

    it('has correct DEFAULT_SETTINGS', () => {
      expect(GlobalConfig.DEFAULT_SETTINGS.engine).toBe('google');
      expect(GlobalConfig.DEFAULT_SETTINGS.source_lang).toBe('auto');
      expect(GlobalConfig.DEFAULT_SETTINGS.target_lang).toBe('zh');
    });

    it('has correct API_TIMEOUT', () => {
      expect(GlobalConfig.API_TIMEOUT).toBe(30000);
    });
  });
});

describe('languages', () => {
  it('has Chinese Simplified', () => {
    const zh = languages.find(l => l.code === 'zh');
    expect(zh).toBeDefined();
    expect(zh.name).toBe('简体中文');
  });

  it('has English', () => {
    const en = languages.find(l => l.code === 'en');
    expect(en).toBeDefined();
    expect(en.name).toBe('English');
  });

  it('has 29 languages', () => {
    expect(languages.length).toBe(29);
  });
});

describe('getFlagEmoji', () => {
  it('converts US to flag', () => {
    expect(getFlagEmoji('us')).toBe('🇺🇸');
  });

  it('converts GB to flag', () => {
    expect(getFlagEmoji('gb')).toBe('🇬🇧');
  });
});
