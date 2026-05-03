// options.js - 设置页逻辑
const DEFAULTS = {
  autoTranslate: true,
  showTransliteration: false,
  translationQuality: 'balanced',
  preferredService: 'google'
};

const qs = (s) => document.querySelector(s);

async function load() {
  try {
    const res = await browser.storage.local.get(['extremeTranslatorSettings']);
    let settings = DEFAULTS;
    if (res && res.extremeTranslatorSettings) {
      try { settings = JSON.parse(res.extremeTranslatorSettings); } catch(e) {}
    }

    qs('#autoTranslate').checked = !!settings.autoTranslate;
    qs('#showTranslit').checked = !!settings.showTransliteration;
    qs('#translationQuality').value = settings.translationQuality || DEFAULTS.translationQuality;
    qs('#preferredService').value = settings.preferredService || DEFAULTS.preferredService;
  } catch (e) {
    console.warn('browser.storage 不可用，尝试 localStorage');
    const s = localStorage.getItem('extremeTranslatorSettings');
    if (s) {
      const parsed = JSON.parse(s);
      qs('#autoTranslate').checked = !!parsed.autoTranslate;
      qs('#showTranslit').checked = !!parsed.showTransliteration;
      qs('#translationQuality').value = parsed.translationQuality || DEFAULTS.translationQuality;
      qs('#preferredService').value = parsed.preferredService || DEFAULTS.preferredService;
    }
  }
}

async function save() {
  const settings = {
    autoTranslate: qs('#autoTranslate').checked,
    showTransliteration: qs('#showTranslit').checked,
    translationQuality: qs('#translationQuality').value,
    preferredService: qs('#preferredService').value
  };

  try {
    await browser.storage.local.set({ extremeTranslatorSettings: JSON.stringify(settings) });
    alert(NovelTransI18n.t('saved'));
  } catch (e) {
    localStorage.setItem('extremeTranslatorSettings', JSON.stringify(settings));
    alert(NovelTransI18n.t('saved_local'));
  }
}

async function exportSettings() {
  try {
    const res = await browser.storage.local.get(['extremeTranslatorSettings']);
    if (res && res.extremeTranslatorSettings) {
      qs('#exportArea').value = res.extremeTranslatorSettings;
    } else if (localStorage.getItem('extremeTranslatorSettings')) {
      qs('#exportArea').value = localStorage.getItem('extremeTranslatorSettings');
    } else {
      qs('#exportArea').value = JSON.stringify(DEFAULTS, null, 2);
    }
  } catch (e) {
    qs('#exportArea').value = localStorage.getItem('extremeTranslatorSettings') || JSON.stringify(DEFAULTS, null, 2);
  }
}

async function importSettings() {
  const text = qs('#exportArea').value.trim();
  if (!text) { alert(NovelTransI18n.t('input_json')); return; }
  try {
    const parsed = JSON.parse(text);
    await browser.storage.local.set({ extremeTranslatorSettings: JSON.stringify(parsed) });
    alert(NovelTransI18n.t('import_success'));
    await load();
  } catch (e) {
    alert(NovelTransI18n.t('invalid_json'));
  }
}

async function resetDefaults() {
  if (!confirm(NovelTransI18n.t('confirm_reset'))) return;
  await browser.storage.local.set({ extremeTranslatorSettings: JSON.stringify(DEFAULTS) });
  await load();
  alert(NovelTransI18n.t('reset_done'));
}

// 动态生成翻译服务选项
function populateServiceOptions() {
  const select = document.getElementById('preferredService');
  if (!select) return;

  while (select.options.length > 0) {
    select.remove(0);
  }

  const engineMap = GlobalConfig?.TRANSLATION_ENGINES || {};
  for (const [id, config] of Object.entries(engineMap)) {
    const option = document.createElement('option');
    option.value = id;
    option.textContent = config.name;
    select.appendChild(option);
  }
}

// 初始化
document.addEventListener('DOMContentLoaded', async function() {
  // 初始化 i18n
  NovelTransI18n.init(() => {
    NovelTransI18n.apply(document);

    // 加载语言选择器当前值
    const langSelect = document.getElementById('uiLanguage');
    if (langSelect) {
      langSelect.value = NovelTransI18n.get();
    }
  });

  populateServiceOptions();
  load();

  // 事件绑定
  document.getElementById('btn-save').addEventListener('click', save);
  document.getElementById('btn-reset').addEventListener('click', resetDefaults);
  document.getElementById('btn-export').addEventListener('click', exportSettings);
  document.getElementById('btn-import').addEventListener('click', importSettings);

  // 语言切换
  const langSelect = document.getElementById('uiLanguage');
  if (langSelect) {
    langSelect.addEventListener('change', function() {
      NovelTransI18n.set(this.value, () => {
        NovelTransI18n.apply(document);
      });
    });
  }
});
