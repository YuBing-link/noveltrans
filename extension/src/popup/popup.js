// popup_new.js - 重构版Extreme Translator 浏览器扩展弹出窗口脚本
// 使用browser.storage.session的新版按钮切换功能

// 从config.js获取状态枚举
const PageStatus = GlobalConfig?.PageStatus || {
  ORIGINAL: 'original',                   // 页面未翻译
  TRANSLATING: 'translating',            // 翻译进行中
  TRANSLATED_SHOWING_TRANSLATION: 'showing_translation',  // 已翻译且显示译文
  TRANSLATED_SHOWING_ORIGINAL: 'showing_original',        // 已翻译但显示原文
  ERROR: 'error'                        // 发生错误
};

// 全局变量
let currentPageStatus = PageStatus.ORIGINAL;
let currentTabId = null;
let isUpdatingStatus = false;
let pendingTranslation = false;

/**
 * 获取当前标签页ID
 */
async function getCurrentTabId() {
  try {
    const tabs = await browser.tabs.query({ active: true, currentWindow: true });
    const tabId = tabs[0]?.id || null;
    console.log('🔍 getCurrentTabId - 获取到的tabId:', tabId);
    return tabId;
  } catch (error) {
    
    return null;
  }
}

/**
 * 查询 content script 的实际显示状态
 * @param {number} tabId - 标签页ID
 * @returns {boolean|null} _showingTranslation 值，失败返回 null
 */
async function queryContentScriptState(tabId) {
  try {
    const response = await browser.tabs.sendMessage(tabId, { action: 'getDisplayMode' });
    if (response && response.success) {
      return response.showingTranslation;
    }
    return null;
  } catch (error) {
    console.log('无法查询 content script 状态:', error.message);
    return null;
  }
}

/**
 * 从会话存储加载页面状态
 * @param {number} tabId - 标签页ID
 * @returns {string} 页面状态
 */
async function loadPageStatus(tabId) {
  try {
    const response = await browser.runtime.sendMessage({
      action: 'getPageStatus',
      tabId: tabId
    });

    if (response.success) {
      let savedStatus = response.status;

      // 如果存储的状态是旧的格式，转换为新状态
      if (savedStatus === 'translated') {
        savedStatus = PageStatus.TRANSLATED_SHOWING_TRANSLATION;
      } else if (savedStatus === 'showing_original') {
        savedStatus = PageStatus.TRANSLATED_SHOWING_ORIGINAL;
      } else if (savedStatus === 'showing_translation') {
        savedStatus = PageStatus.TRANSLATED_SHOWING_TRANSLATION;
      } else if (savedStatus === 'original') {
        savedStatus = PageStatus.ORIGINAL;
      }

      return savedStatus;
    } else {
      console.warn('获取页面状态失败，使用默认值:', response.error);
      return PageStatus.ORIGINAL;
    }
  } catch (error) {
    console.warn('无法从background获取页面状态，使用默认值:', error.message);
    return PageStatus.ORIGINAL;
  }
}

/**
 * 通过background保存页面状态
 * @param {number} tabId - 标签页ID
 * @param {string} status - 页面状态
 */
async function savePageStatus(tabId, status) {
  try {
    await browser.runtime.sendMessage({
      action: 'savePageStatus',
      tabId: tabId,
      status: status
    });
    console.log(`页面状态已通过background保存: tab ${tabId}, status: ${status}`);
  } catch (error) {
    console.error('通过background保存页面状态失败:', error);
  }
}

/**
 * 更新翻译按钮文本
 */
function updateTranslateButton() {
  const translateBtn = document.getElementById('btn-translate');
  const translateBtnText = document.querySelector('#btn-translate .btn-text');

  if (!translateBtn || !translateBtnText) return;

  switch (currentPageStatus) {
    case PageStatus.TRANSLATED_SHOWING_TRANSLATION:
      translateBtnText.textContent = '显示原文';
      break;
    case PageStatus.TRANSLATED_SHOWING_ORIGINAL:
      translateBtnText.textContent = '显示译文';
      break;
    case PageStatus.ORIGINAL:
      translateBtnText.textContent = '开始翻译';
      break;
    case PageStatus.TRANSLATING:
      translateBtnText.textContent = '翻译中...';
      break;
    default:
      translateBtnText.textContent = '开始翻译';
  }
}

/**
 * 检查当前页面的翻译状态
 */
async function checkCurrentPageStatus() {
  if (!currentTabId) return;

  try {
    // 从会话存储获取页面状态（而不是从内容脚本）
    const savedStatus = await loadPageStatus(currentTabId);

    if (savedStatus) {
      if (savedStatus !== currentPageStatus) {
        currentPageStatus = savedStatus;
        updateTranslateButton();
        console.log('页面状态已从存储更新:', savedStatus);
      }
    }
  } catch (error) {
    console.log('无法从存储获取页面状态，保持当前状态不变:', error.message);
  }
}

/**
 * 设置当前页面状态
 */
async function setCurrentPageStatus(status) {
  if (currentPageStatus !== status) {
    currentPageStatus = status;
    // 实时保存到本地存储，而不是等到popup关闭时
    await savePageStatus(currentTabId, status);
    updateTranslateButton();
    console.log(`页面状态已更新并实时保存: ${status}`);
  }
}

/**
 * 初始化会话存储状态
 */
async function initializeSessionState() {
  currentTabId = await getCurrentTabId();
  if (currentTabId) {
    // 从会话存储加载当前标签页的状态
    currentPageStatus = await loadPageStatus(currentTabId);
    console.log('从会话存储加载页面状态:', currentPageStatus);

    // 立即更新UI以反映加载的状态
    updateTranslateButton();

    // 同步所有相关的UI元素
    syncAllUIElements();
  }
}

/**
 * 同步所有UI元素以反映当前状态
 */
function syncAllUIElements() {
  // 更新翻译按钮
  updateTranslateButton();

  // 如果有其他状态相关的UI元素，也要同步
  // 这里可以根据需要添加更多UI同步逻辑
}

/**
 * 处理翻译按钮点击事件
 */
async function handleTranslate() {
  console.log('🚀 handleTranslate - 翻译按钮被点击');
  console.log('   - currentTabId:', currentTabId);
  console.log('   - currentPageStatus:', currentPageStatus);
  console.log('   - pendingTranslation:', pendingTranslation);

  if (!currentTabId) {
    
    return;
  }

  if (pendingTranslation) {
    
    return;
  }

  console.log('处理翻译请求, 当前状态:', currentPageStatus);

  pendingTranslation = true;

  try {
    // 根据当前按钮文本确定用户意图
    const buttonText = document.querySelector('#btn-translate .btn-text')?.textContent;

    if (currentPageStatus === PageStatus.ORIGINAL) {
      // 开始翻译
      console.log('🚀 开始翻译操作');

      // 获取当前设置
      const targetLang = await getTargetLangCode();

      

      // 从background获取设置
      let isBilingual = false;
      let engine = 'google';
      let expertMode = false;
      try {
        console.log('📡 请求获取设置...');
        const response = await browser.runtime.sendMessage({
          action: 'getSettings'
        });

        console.log('📤 收到设置响应:', response);

        if (response.success && response.settings) {
          const settings = response.settings.settings || {};
          isBilingual = settings.bilingual !== false;
          engine = settings.engine || 'google';
          expertMode = settings.expertMode === true;
        } else {
          // 如果background请求失败，从本地存储和UI获取
          console.log('⚠️ background请求失败，使用本地存储');
          const result = await browser.storage.local.get(['settings']);
          const localSettings = result.settings || {};
          isBilingual = localSettings.bilingual !== false;
          engine = localSettings.engine || 'google';
          expertMode = localSettings.expertMode === true;
        }
      } catch (error) {
        // 从UI控件和本地存储获取
        const bilingualCheck = document.getElementById('bilingual-check');
        isBilingual = bilingualCheck ? bilingualCheck.checked : true;
        const expertModeCheck = document.getElementById('expert-mode-check');
        expertMode = expertModeCheck ? expertModeCheck.checked : false;
        const result = await browser.storage.local.get(['settings']);
        const localSettings = result.settings || {};
        engine = localSettings.engine || 'google';
      }

      console.log('📤 准备发送翻译请求:', {
        currentTabId,
        action: 'translateWebPage',
        targetLang,
        sourceLang: 'auto',
        engine,
        bilingual: isBilingual
      });

      // 发送翻译请求
      try {
        await browser.tabs.sendMessage(currentTabId, {
          action: 'translateWebPage',
          targetLang,
          sourceLang: 'auto',
          engine,
          bilingual: isBilingual
        });
        
      } catch (sendError) {
        
        pendingTranslation = false;
        return;
      }

      // 更新状态为翻译中
      await setCurrentPageStatus(PageStatus.TRANSLATING);
      updateTranslateButton();

      console.log('⏳ 等待翻译完成...');

      // 等待翻译完成并更新状态
      setTimeout(async () => {
        // 一律从本地存储读取状态
        const finalStatus = await loadPageStatus(currentTabId);
        console.log('📥 收到最终状态:', finalStatus);

        if (finalStatus && finalStatus !== PageStatus.TRANSLATING) {
          currentPageStatus = finalStatus;
          // 实时保存状态到本地存储
          await savePageStatus(currentTabId, finalStatus);
          updateTranslateButton();
          
        }
        pendingTranslation = false;
      }, 2000);

    } else if (buttonText === '显示原文' || currentPageStatus === PageStatus.TRANSLATED_SHOWING_TRANSLATION) {
      // 切换到显示原文
      console.log('切换到显示原文');

      const response = await browser.tabs.sendMessage(currentTabId, {
        action: 'toggleDisplayMode'
      });

      // 根据 content script 返回的实际模式更新本地状态
      if (response && response.success && response.displayMode) {
        switch (response.displayMode) {
          case 'original':
            await setCurrentPageStatus(PageStatus.TRANSLATED_SHOWING_ORIGINAL);
            break;
          case 'translation':
          case 'bilingual':
          default:
            await setCurrentPageStatus(PageStatus.TRANSLATED_SHOWING_TRANSLATION);
            break;
        }
      } else {
        // 备用方案：假设切换成功
        await setCurrentPageStatus(PageStatus.TRANSLATED_SHOWING_ORIGINAL);
      }

      pendingTranslation = false;

    } else if (buttonText === '显示译文' || currentPageStatus === PageStatus.TRANSLATED_SHOWING_ORIGINAL) {
      // 切换到显示译文
      console.log('切换到显示译文');

      const response = await browser.tabs.sendMessage(currentTabId, {
        action: 'toggleDisplayMode'
      });

      // 根据 content script 返回的实际模式更新本地状态
      if (response && response.success && response.displayMode) {
        switch (response.displayMode) {
          case 'translation':
          case 'bilingual':
            await setCurrentPageStatus(PageStatus.TRANSLATED_SHOWING_TRANSLATION);
            break;
          case 'original':
          default:
            await setCurrentPageStatus(PageStatus.TRANSLATED_SHOWING_ORIGINAL);
            break;
        }
      } else {
        // 备用方案：假设切换成功
        await setCurrentPageStatus(PageStatus.TRANSLATED_SHOWING_TRANSLATION);
      }

      pendingTranslation = false;
    }
  } catch (error) {
    console.error('处理翻译操作失败:', error);
    pendingTranslation = false;

    // 如果出现错误，恢复到原始状态
    await setCurrentPageStatus(PageStatus.ORIGINAL);
  }
}


/**
 * 获取选中的目标语言代码
 */
async function getTargetLangCode() {
  const langNameEl = document.getElementById('target-lang-display');
  if (langNameEl) {
    const targetLangName = langNameEl.textContent;
    console.log('🔍 getTargetLangCode - 目标语言标签文本:', targetLangName);

    // 从GlobalConfig.languages中查找对应的代码（如果定义的话）
    if (typeof languages !== 'undefined') {
      console.log('🔍 languages变量存在，开始查找...');
      for (const lang of languages) {
        if (lang.name === targetLangName || lang.name.includes(targetLangName) || targetLangName.includes(lang.name)) {
          
          return lang.code;
        }
      }
      
    } else if (typeof GlobalConfig !== 'undefined' && typeof GlobalConfig.LANGUAGES !== 'undefined') {
      // 备用方案：从GlobalConfig.LANGUAGES中查找对应的代码
      console.log('🔍 使用GlobalConfig.LANGUAGES查找...');
      for (const [code, name] of Object.entries(GlobalConfig.LANGUAGES)) {
        if (name === targetLangName || name.includes(targetLangName) || targetLangName.includes(name)) {
          
          return code;
        }
      }
      
    }

    // 如果在config中找不到，从background获取
    try {
      console.log('🔍 从background获取目标语言设置...');
      const response = await browser.runtime.sendMessage({
        action: 'getSettings'
      });

      if (response.success && response.settings) {
        const settings = response.settings.settings || {};
        
        return settings.target_lang || 'zh';
      } else {
        // 如果background请求失败，从本地存储获取
        console.log('🔍 从本地存储获取目标语言设置...');
        const result = await browser.storage.local.get(['settings']);
        const settings = result.settings || {};
        
        return settings.target_lang || 'zh';
      }
    } catch (error) {
      console.error('获取目标语言设置失败:', error);

      // 从本地存储获取
      try {
        const result = await browser.storage.local.get(['settings']);
        const settings = result.settings || {};
        return settings.target_lang || 'zh';
      } catch (localError) {
        console.error('获取本地目标语言设置失败:', localError);
        return 'zh';
      }
    }
  }

  // 如果界面上没有找到，从background获取
  try {
    console.log('🔍 从background获取目标语言设置（界面未找到）...');
    const response = await browser.runtime.sendMessage({
      action: 'getSettings'
    });

    if (response.success && response.settings) {
      const settings = response.settings.settings || {};
      
      return settings.target_lang || 'zh';
    } else {
      // 如果background请求失败，从本地存储获取
      console.log('🔍 从本地存储获取目标语言设置（background失败）...');
      const result = await browser.storage.local.get(['settings']);
      const settings = result.settings || {};
      
      return settings.target_lang || 'zh';
    }
  } catch (error) {
    console.error('获取目标语言设置失败:', error);

    // 从本地存储获取
    try {
      const result = await browser.storage.local.get(['settings']);
      const settings = result.settings || {};
      return settings.target_lang || 'zh';
    } catch (localError) {
      console.error('获取本地目标语言设置失败:', localError);
      return 'zh';
    }
  }
}

/**
 * 获取选中的翻译引擎 ID
 */
async function getSelectedModelId() {
  const serviceNameEl = document.querySelector('.service-name');
  if (serviceNameEl) {
    const serviceName = serviceNameEl.textContent.trim();
    console.log('🔍 getSelectedModelId - 引擎名称:', serviceName);

    // 从 GlobalConfig.TRANSLATION_ENGINES 中查找对应的 ID
    if (typeof GlobalConfig !== 'undefined' && typeof GlobalConfig.TRANSLATION_ENGINES !== 'undefined') {
      for (const [id, config] of Object.entries(GlobalConfig.TRANSLATION_ENGINES)) {
        if (config.name === serviceName) {
          console.log('🔍 找到匹配的引擎 ID:', id);
          return id;
        }
      }
    }
  }

  // 如果界面上没有找到，从 background 获取
  try {
    console.log('🔍 从 background 获取引擎设置...');
    const response = await browser.runtime.sendMessage({
      action: 'getSettings'
    });

    if (response.success && response.settings) {
      const settings = response.settings.settings || {};
      console.log('🔍 从 background 获取到的引擎:', settings.engine);
      return settings.engine || 'google';
    }
  } catch (error) {
    console.error('从 background 获取引擎失败:', error);
  }

  // 默认返回 google
  return 'google';
}

/**
 * 绑定所有事件监听器
 */
function bindEvents() {
  // 翻译按钮
  const btnTranslate = document.getElementById('btn-translate');
  if (btnTranslate) {
    
    btnTranslate.addEventListener('click', handleTranslate);
    btnTranslate.addEventListener('click', function(e) {
      console.log('🔘 翻译按钮点击事件触发（测试监听器）');
    });
  } else {
    
  }

  const btnReader = document.getElementById('btn-reader');
  if (btnReader) {
    
    btnReader.addEventListener('click', handleReader);
  }

  const btnModelSelect = document.getElementById('btn-model-select');
  if (btnModelSelect) {
    btnModelSelect.addEventListener('click', handleModelSelect);
  }

  // 目标语言选择按钮
  const btnTargetLang = document.getElementById('btn-target-lang');
  if (btnTargetLang) {
    btnTargetLang.addEventListener('click', (e) => {
      e.stopPropagation();
      e.preventDefault();
      console.log('打开语言选择下拉菜单');
      showLanguageDropdown(btnTargetLang);
    });
  }

  // 登录按钮
  const loginBtn = document.getElementById('btn-login');
  if (loginBtn) {
    loginBtn.addEventListener('click', async function() {
      const isLoggedIn = await checkLoginState();
      if (isLoggedIn) {
        // 已登录，跳转到用户中心
        browser.tabs.create({ url: `${GlobalConfig.API_BASE_URL}/user` });
        window.close();
      } else {
        openLoginPage();
      }
    });
  }

  // 双语模式切换
  const bilingualCheck = document.getElementById('bilingual-check');
  if (bilingualCheck) {
    bilingualCheck.addEventListener('change', async function() {
      await saveSetting('bilingual', this.checked);
      try {
        await browser.runtime.sendMessage({
          action: 'updateSetting',
          key: 'bilingual',
          value: this.checked
        });
      } catch (error) {
        console.error('同步双语模式到 background 失败:', error);
      }
      console.log('双语模式:', this.checked ? '开启' : '关闭');
    });
  }

  // 专家模式切换
  const expertModeCheck = document.getElementById('expert-mode-check');
  if (expertModeCheck) {
    expertModeCheck.addEventListener('change', async function() {
      await saveSetting('expertMode', this.checked);
      try {
        await browser.runtime.sendMessage({
          action: 'updateSetting',
          key: 'expertMode',
          value: this.checked
        });
      } catch (error) {
        console.error('同步专家模式到 background 失败:', error);
      }
      console.log('专家模式:', this.checked ? '开启' : '关闭');
    });
  }

  // 未登录时点击专家模式开关，提示登录
  const expertModeToggle = document.getElementById('expert-mode-toggle');
  if (expertModeToggle) {
    expertModeToggle.addEventListener('click', async function(e) {
      const expertCheck = document.getElementById('expert-mode-check');
      if (expertCheck && expertCheck.disabled) {
        e.preventDefault();
        e.stopPropagation();
        openLoginPage();
      }
    });
  }
}

// 保存设置到本地存储
async function saveSetting(key, value) {
  try {
    const result = await browser.storage.local.get(['settings']);
    const settings = result.settings || {};
    settings[key] = value;
    await browser.storage.local.set({ settings });
    console.log(`设置已保存：${key} = ${value}`);
  } catch (error) {
    console.error('保存设置失败:', error);
  }
}

// 检查登录状态
async function checkLoginState() {
  try {
    const result = await browser.storage.local.get(['auth_token']);
    return !!(result.auth_token);
  } catch (error) {
    console.error('检查登录状态失败:', error);
    return false;
  }
}

// 打开登录页面
function openLoginPage() {
  const loginUrl = `${GlobalConfig.API_BASE_URL}/login`;
  browser.tabs.create({ url: loginUrl }).then((tab) => {
    // 使用轮询方式检测登录成功：每隔 1 秒检查一次 localStorage
    let pollCount = 0;
    const maxPolls = 60; // 最多轮询 60 秒

    const pollForToken = async () => {
      pollCount++;
      if (pollCount > maxPolls) {
        console.log('⏰ 登录检测超时，停止轮询');
        return;
      }

      try {
        const results = await browser.scripting.executeScript({
          target: { tabId: tab.id },
          func: () => {
            const token = localStorage.getItem('authToken');
            const userInfo = localStorage.getItem('userInfo');
            return { token, userInfo };
          }
        });

        if (results && results[0] && results[0].result && results[0].result.token) {
          const { token, userInfo } = results[0].result;
          await browser.storage.local.set({
            auth_token: token,
            auth_user: userInfo ? JSON.parse(userInfo) : {}
          });
          console.log('✅ 登录状态已同步到插件');
          await updateLoginButtonState();
          // 登录成功后停止轮询
          return;
        }
      } catch (e) {
        // 跨域或脚本注入失败，忽略
        console.log('⚠️ 轮询检测失败:', e.message);
      }

      // 1 秒后继续轮询
      setTimeout(pollForToken, 1000);
    };

    // 等待页面初始加载完成后开始轮询
    setTimeout(pollForToken, 2000);
  });
  window.close();
}

// 更新登录按钮视觉状态
async function updateLoginButtonState() {
  const loginBtn = document.getElementById('btn-login');
  if (!loginBtn) return;

  const isLoggedIn = await checkLoginState();
  if (isLoggedIn) {
    loginBtn.classList.add('logged-in');
    loginBtn.title = '用户中心';
    // 登录后启用专家模式开关
    const expertCheck = document.getElementById('expert-mode-check');
    if (expertCheck) {
      expertCheck.disabled = false;
    }
  } else {
    loginBtn.classList.remove('logged-in');
    loginBtn.title = '登录';
    // 未登录时禁用专家模式开关
    const expertCheck = document.getElementById('expert-mode-check');
    if (expertCheck) {
      expertCheck.disabled = true;
    }
  }
}

// 根据语言代码获取语言名称
function getLanguageNameByCode(code) {
  if (typeof languages !== 'undefined') {
    const lang = languages.find(l => l.code === code);
    if (lang) return lang.name;
  }
  const langMap = {
    'zh': '简体中文',
    'en': 'English',
    'ja': '日本語',
    'ko': '한국어',
    'fr': 'Français',
    'de': 'Deutsch',
    'es': 'Español',
    'ru': 'Русский'
  };
  return langMap[code] || code;
}

// 根据引擎 ID 获取引擎信息
function getEngineInfoById(id) {
  if (typeof GlobalConfig !== 'undefined' && typeof GlobalConfig.TRANSLATION_ENGINES !== 'undefined') {
    const config = GlobalConfig.TRANSLATION_ENGINES[id];
    if (config) {
      return {
        id: id,
        name: config.name,
        icon: config.icon || 'ri-translate',
        color: config.color || '#64748b'
      };
    }
  }
  return null;
}

// 处理阅读模式
async function handleReader() {
  console.log('📖 点击阅读模式按钮');

  if (!currentTabId) {
    console.error('❌ 当前 Tab ID 为空');
    return;
  }

  console.log('📖 当前 Tab ID:', currentTabId);

  const targetLang = await getTargetLangCode();
  const engine = await getSelectedModelId();

  console.log('📖 发送激活请求:', { targetLang, engine });

  try {
    // 先尝试通过 background.js 转发消息到 content script
    const response = await browser.runtime.sendMessage({
      action: 'activateReaderMode',
      tabId: currentTabId,
      targetLang,
      sourceLang: 'auto',
      engine
    });

    console.log('✅ 阅读模式响应:', response);
    console.log('✅ 响应类型:', typeof response, '값:', JSON.stringify(response));

    // 检查响应是否成功（兼容多种返回格式）
    const isSuccess = response === true ||
                      (response && typeof response === 'object' && response.success === true);

    console.log('✅ isSuccess:', isSuccess);
    if (isSuccess) {
      console.log('🎉 阅读模式激活成功');
      window.close();
    } else {
      // 显示错误信息
      const errorMsg = response?.error || response?.message || '未知错误';
      console.error('❌ 阅读模式激活失败:', errorMsg);
      alert(`阅读模式激活失败：${errorMsg}`);
    }
  } catch (error) {
    console.error('❌ 发送阅读模式命令失败:', error);

    // 如果是连接错误，尝试动态注入 script
    if (error.message && error.message.includes('Could not establish connection')) {
      console.log('⚠️ 尝试动态注入 content script...');
      try {
        await injectReaderScript(currentTabId);

        // 重试
        const retryResponse = await browser.runtime.sendMessage({
          action: 'activateReaderMode',
          tabId: currentTabId,
          targetLang,
          sourceLang: 'auto',
          engine
        });

        const retrySuccess = retryResponse === true ||
                            (retryResponse && typeof retryResponse === 'object' && retryResponse.success === true);

        if (retrySuccess) {
          console.log('🎉 阅读模式激活成功（重试后）');
          window.close();
          return;
        } else {
          const errorMsg = retryResponse?.error || retryResponse?.message || '未知错误';
          alert(`阅读模式激活失败：${errorMsg}`);
        }
      } catch (injectError) {
        console.error('动态注入失败:', injectError);
        alert('无法与当前页面通信。请确保页面已完全加载，然后重试。\n\n注意：浏览器内部页面（如 chrome://、about:）和扩展页面不支持此功能。');
      }
    } else {
      alert(`无法与当前页面通信。请刷新页面后重试：${error.message}`);
    }
  }
}

// 动态注入阅读器脚本
async function injectReaderScript(tabId) {
  try {
    // 按顺序注入依赖项
    await browser.scripting.executeScript({
      target: { tabId },
      files: [
        'src/lib/browser-polyfill.js',
        'src/lib/config.js',
        'src/lib/purify.js',
        'src/lib/Readability.js',
        'src/content/read.js'
      ]
    });
    console.log('✅ 阅读器脚本及依赖注入成功');
    // 等待脚本初始化和消息监听器注册
    await new Promise(resolve => setTimeout(resolve, 500));
  } catch (error) {
    console.error('❌ 注入阅读器脚本失败:', error);
    throw error;
  }
}

// 处理模型选择
function handleModelSelect(e) {
  e.stopPropagation();
  e.preventDefault();
  const modelSelector = document.getElementById('btn-model-select');
  if (modelSelector) {
    console.log('打开翻译服务下拉菜单');
    showModelDropdown(modelSelector);
  }
}

// 全局变量，追踪当前打开的下拉菜单
let currentDropdown = null;
let currentAnchorEl = null;

// 关闭当前打开的下拉菜单
function closeCurrentDropdown() {
  if (currentDropdown) {
    currentDropdown.remove();
    document.removeEventListener('click', currentDropdown.clickHandler);
    document.removeEventListener('keydown', currentDropdown.keyHandler);
    currentDropdown = null;
    currentAnchorEl = null;
  }
}

// 语言选择下拉菜单
function showLanguageDropdown(anchorEl) {
  // 如果点击的是已展开的选择栏，直接关闭
  if (currentDropdown && currentAnchorEl === anchorEl) {
    closeCurrentDropdown();
    return;
  }

  // 先关闭已打开的下拉菜单
  closeCurrentDropdown();

  let langList = [];
  if (typeof languages !== 'undefined') {
    langList = languages.map(lang => ({
      code: lang.code,
      name: lang.name,
      flag: lang.flag || getFlagEmoji(lang.code)
    }));
  } else if (typeof GlobalConfig !== 'undefined' && typeof GlobalConfig.LANGUAGES !== 'undefined') {
    langList = Object.entries(GlobalConfig.LANGUAGES).map(([code, name]) => ({
      code: code,
      name: name,
      flag: getFlagEmoji(code)
    }));
  }

  if (langList.length === 0) {
    console.warn('没有可用的语言列表');
    return;
  }

  const rect = anchorEl.getBoundingClientRect();
  const selector = document.createElement('div');
  selector.className = 'language-selector';

  // 始终向下展开
  const top = rect.bottom + 8;

  selector.style.cssText = `
    position: fixed;
    top: ${top}px;
    left: ${rect.left}px;
    width: ${rect.width}px;
    background: var(--card-bg);
    border: 1px solid var(--border-light);
    border-radius: var(--radius-md);
    box-shadow: 0 12px 40px rgba(0,0,0,0.15);
    z-index: 10000;
    max-height: 280px;
    overflow-y: auto;
    animation: slideDown 0.2s ease;
  `;

  // 当前选中的语言代码
  const currentLangCode = document.getElementById('target-lang-display')?.textContent || '';
  const currentLangName = getLanguageNameByCode(currentLangCode) || currentLangCode;

  langList.forEach((lang, index) => {
    const isSelected = lang.name === currentLangName || lang.code === currentLangCode;

    const langItem = document.createElement('div');
    langItem.className = 'language-item';
    if (isSelected) {
      langItem.classList.add('selected');
    }

    langItem.innerHTML = `
      <span class="flag">${lang.flag}</span>
      <span class="name">${lang.name}</span>
      ${isSelected ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color:#6366f1"><polyline points="20 6 9 17 4 12"/></svg>' : ''}
    `;

    langItem.addEventListener('click', async (e) => {
      e.stopPropagation();
      e.preventDefault();

      // 更新目标语言显示
      const langNameEl = document.getElementById('target-lang-display');
      if (langNameEl) {
        langNameEl.textContent = lang.name;
      }

      await saveSetting('target_lang', lang.code);

      try {
        await browser.runtime.sendMessage({
          action: 'updateSetting',
          key: 'target_lang',
          value: lang.code
        });
      } catch (error) {
        console.error('发送设置更新到 background 失败:', error);
      }

      closeCurrentDropdown();
      console.log(`目标语言已切换至：${lang.name} (${lang.code})`);
    });

    selector.appendChild(langItem);
  });

  document.body.appendChild(selector);

  // 点击外部关闭
  const handleClickOutside = (event) => {
    if (!selector.contains(event.target) && !anchorEl.contains(event.target)) {
      closeCurrentDropdown();
    }
  };

  // ESC 键关闭
  const handleEscape = (event) => {
    if (event.key === 'Escape') {
      closeCurrentDropdown();
    }
  };

  selector.clickHandler = handleClickOutside;
  selector.keyHandler = handleEscape;

  // 延迟绑定，避免立即触发关闭
  setTimeout(() => {
    document.addEventListener('click', handleClickOutside);
    document.addEventListener('keydown', handleEscape);
  }, 10);

  currentDropdown = selector;
  currentAnchorEl = anchorEl;
}

// 初始化
document.addEventListener('DOMContentLoaded', async () => {
  currentTabId = await getCurrentTabId();
  console.log('popup 已加载，当前 tabId:', currentTabId);

  // 绑定事件
  bindEvents();

  // 加载保存的设置
  await loadSavedSettings();

  // 加载当前页面状态
  if (currentTabId) {
    currentPageStatus = await loadPageStatus(currentTabId);
    console.log('加载页面状态 (storage):', currentPageStatus);

    // 查询 content script 的实际状态，优先使用真实状态
    const scriptState = await queryContentScriptState(currentTabId);
    if (scriptState !== null) {
      // 以 content script 的实际 DOM 状态为准
      const realStatus = scriptState
        ? PageStatus.TRANSLATED_SHOWING_TRANSLATION
        : PageStatus.TRANSLATED_SHOWING_ORIGINAL;
      if (realStatus !== currentPageStatus) {
        console.log('popup 状态与 content script 不一致，已同步:', currentPageStatus, '→', realStatus);
        currentPageStatus = realStatus;
      }
    }

    updateTranslateButton();
  }

  // 更新登录状态
  await updateLoginButtonState();
});

// 翻译服务下拉菜单
function showModelDropdown(modelSelector) {
  // 如果点击的是已展开的选择栏，直接关闭
  if (currentDropdown && currentAnchorEl === modelSelector) {
    closeCurrentDropdown();
    return;
  }

  // 先关闭已打开的下拉菜单
  closeCurrentDropdown();

  let services = [];
  if (typeof GlobalConfig !== 'undefined' && typeof GlobalConfig.TRANSLATION_ENGINES !== 'undefined') {
    services = Object.entries(GlobalConfig.TRANSLATION_ENGINES).map(([id, config]) => ({
      id: id,
      name: config.name,
      icon: config.icon || 'ri-translate',
      color: config.color || '#64748b'
    }));
  }

  if (services.length === 0) {
    console.warn('没有可用的翻译服务列表');
    return;
  }

  const rect = modelSelector.getBoundingClientRect();
  const selector = document.createElement('div');
  selector.className = 'model-selector-dropdown';

  // 始终向下展开
  const top = rect.bottom + 8;

  selector.style.cssText = `
    position: fixed;
    top: ${top}px;
    left: ${rect.left}px;
    width: ${rect.width}px;
    background: var(--card-bg);
    border: 1px solid var(--border-light);
    border-radius: var(--radius-md);
    box-shadow: 0 12px 40px rgba(0,0,0,0.15);
    z-index: 10000;
    max-height: 280px;
    overflow-y: auto;
    animation: slideDown 0.2s ease;
  `;

  // 当前选中的服务 ID
  const currentServiceName = document.querySelector('.service-name')?.textContent || '';

  services.forEach((service) => {
    const isSelected = service.name === currentServiceName || service.id === currentServiceName;

    const serviceItem = document.createElement('div');
    serviceItem.className = 'service-item';
    if (isSelected) {
      serviceItem.classList.add('selected');
    }

    serviceItem.innerHTML = `
      <svg class="service-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="${service.color || '#64748b'}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg>
      <span class="service-name">${service.name}</span>
      ${isSelected ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color:#6366f1"><polyline points="20 6 9 17 4 12"/></svg>' : ''}
    `;

    serviceItem.addEventListener('click', async (e) => {
      e.stopPropagation();
      e.preventDefault();

      const serviceInfo = document.querySelector('.service-info');
      if (serviceInfo) {
        const serviceIcon = serviceInfo.querySelector('.service-icon');
        const serviceName = serviceInfo.querySelector('.service-name');

        if (serviceIcon) {
          serviceIcon.setAttribute('stroke', service.color || '#64748b');
        }

        if (serviceName) {
          serviceName.textContent = service.name;
        }
      }

      await saveSetting('selectedModel', service.name);
      await saveSetting('selectedModelId', service.id);

      try {
        await browser.runtime.sendMessage({
          action: 'updateSetting',
          key: 'engine',
          value: service.id
        });
      } catch (error) {
        console.error('发送设置更新到 background 失败:', error);
      }

      closeCurrentDropdown();
      console.log(`模型已切换至：${service.name} (${service.id})`);
    });

    selector.appendChild(serviceItem);
  });

  document.body.appendChild(selector);

  // 点击外部关闭
  const handleClickOutside = (event) => {
    if (!selector.contains(event.target) && !modelSelector.contains(event.target)) {
      closeCurrentDropdown();
    }
  };

  // ESC 键关闭
  const handleEscape = (event) => {
    if (event.key === 'Escape') {
      closeCurrentDropdown();
    }
  };

  selector.clickHandler = handleClickOutside;
  selector.keyHandler = handleEscape;

  // 延迟绑定，避免立即触发关闭
  setTimeout(() => {
    document.addEventListener('click', handleClickOutside);
    document.addEventListener('keydown', handleEscape);
  }, 10);

  currentDropdown = selector;
  currentAnchorEl = modelSelector;
}

// 从 background 加载全局设置
async function loadGlobalSettings() {
  try {
    const response = await browser.runtime.sendMessage({
      action: 'getSettings'
    });

    if (response.success && response.settings) {
      const settings = response.settings.settings || {};

      // 更新目标语言显示
      if (settings.target_lang) {
        const langNameEl = document.getElementById('target-lang-display');
        if (langNameEl) {
          const langName = getLanguageNameByCode(settings.target_lang) || settings.target_lang;
          langNameEl.textContent = langName;
        }
      }

      // 更新引擎选择显示
      if (settings.engine) {
        const serviceIcon = document.querySelector('.service-icon');
        const serviceName = document.querySelector('.service-name');

        if (serviceIcon && serviceName) {
          let engineInfo = getEngineInfoById(settings.engine);
          if (engineInfo) {
            if (serviceIcon.tagName === 'svg') {
              serviceIcon.setAttribute('stroke', engineInfo.color || '#64748b');
            } else {
              serviceIcon.style.color = engineInfo.color || '#64748b';
            }
            serviceName.textContent = engineInfo.name;
          }
        }
      }

      // 更新双语开关状态
      const bilingualCheck = document.getElementById('bilingual-check');
      if (bilingualCheck) {
        bilingualCheck.checked = settings.bilingual !== false;
      }

      // 更新专家模式开关状态
      const expertModeCheck = document.getElementById('expert-mode-check');
      if (expertModeCheck) {
        expertModeCheck.checked = settings.expertMode === true;
        expertModeCheck.disabled = false;
      }

      console.log('已从 background 加载全局设置:', settings);
      return settings;
    }
  } catch (error) {
    console.error('加载全局设置失败:', error);
  }
  return {};
}

// 从本地存储加载设置
async function loadLocalSettings() {
  try {
    const result = await browser.storage.local.get(['settings']);
    const settings = result.settings || {};

    if (settings.target_lang) {
      const langNameEl = document.getElementById('target-lang-display');
      if (langNameEl) {
        const langName = getLanguageNameByCode(settings.target_lang) || settings.target_lang;
        langNameEl.textContent = langName;
      }
    }

    if (settings.engine) {
      const serviceIcon = document.querySelector('.service-icon');
      const serviceName = document.querySelector('.service-name');

      if (serviceIcon && serviceName) {
        let engineInfo = getEngineInfoById(settings.engine);
        if (engineInfo) {
          if (serviceIcon.tagName === 'svg') {
            serviceIcon.setAttribute('stroke', engineInfo.color || '#64748b');
          } else {
            serviceIcon.style.color = engineInfo.color || '#64748b';
          }
          serviceName.textContent = engineInfo.name;
        }
      }
    }

    // 更新双语开关状态
    const bilingualCheck = document.getElementById('bilingual-check');
    if (bilingualCheck) {
      bilingualCheck.checked = settings.bilingual !== false;
    }

    // 更新专家模式开关状态
    const expertModeCheck = document.getElementById('expert-mode-check');
    if (expertModeCheck) {
      expertModeCheck.checked = settings.expertMode === true;
    }

    console.log('已从本地存储加载设置:', settings);
    return settings;
  } catch (error) {
    console.error('加载本地设置失败:', error);
    return {};
  }
}

// 加载保存的设置
async function loadSavedSettings() {
  await loadGlobalSettings();
  await loadLocalSettings();
}
