/**
 * i18n 工具：基于 JSON 消息文件的运行时无刷新语言切换
 *
 * 存储策略：
 * - 扩展页面（popup/options/welcome）：chrome.storage.local
 * - 内容脚本（reader/content）：localStorage
 */

(function() {
  'use strict';

  var MESSAGES = {};
  var CURRENT_LANG = 'en';
  var LANGUAGES = ['zh', 'en'];
  var STORAGE_KEY = 'uiLanguage';
  var LOCALES_PATH = '/locales/';

  /**
   * 获取当前语言
   */
  function getLanguage() {
    return CURRENT_LANG;
  }

  /**
   * 翻译函数
   */
  function t(key) {
    return MESSAGES[key] || key;
  }

  /**
   * 从 URL 获取 locale JSON（同步 XHR，因为 locale 文件很小）
   */
  function loadMessages(lang, callback) {
    var url = chrome.runtime.getURL(LOCALES_PATH + lang + '/messages.json');
    var xhr = new XMLHttpRequest();
    xhr.open('GET', url, true);
    xhr.onload = function() {
      if (xhr.status === 200) {
        try {
          callback(null, JSON.parse(xhr.responseText));
        } catch (e) {
          callback(e, {});
        }
      } else {
        callback(new Error('Failed to load locale: ' + lang), {});
      }
    };
    xhr.onerror = function() { callback(new Error('Network error'), {}); };
    xhr.send();
  }

  /**
   * 初始化语言设置
   */
  function init(callback) {
    var done = function(lang) {
      loadMessages(lang, function(err, messages) {
        if (!err) {
          CURRENT_LANG = lang;
          MESSAGES = messages;
        }
        if (callback) callback(err);
      });
    };

    // 尝试从 chrome.storage.local 读取（扩展页面）
    if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
      chrome.storage.local.get([STORAGE_KEY], function(result) {
        if (result && result[STORAGE_KEY] && LANGUAGES.indexOf(result[STORAGE_KEY]) >= 0) {
          done(result[STORAGE_KEY]);
        } else {
          // 回退到浏览器语言或默认 zh
          var browserLang = (typeof chrome !== 'undefined' && chrome.i18n) ? chrome.i18n.getUILanguage() : navigator.language;
          var lang = (browserLang && browserLang.indexOf('zh') === 0) ? 'zh' : 'en';
          done(lang);
        }
      });
    } else {
      // 内容脚本使用 localStorage
      var stored = localStorage.getItem(STORAGE_KEY);
      if (stored && LANGUAGES.indexOf(stored) >= 0) {
        done(stored);
      } else {
        done('en');
      }
    }
  }

  /**
   * 切换语言
   */
  function setLanguage(lang, callback) {
    if (LANGUAGES.indexOf(lang) < 0) return;

    var persist = function() {
      if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
        chrome.storage.local.set({ uiLanguage: lang });
      }
      localStorage.setItem(STORAGE_KEY, lang);
      CURRENT_LANG = lang;
    };

    loadMessages(lang, function(err, messages) {
      if (!err) {
        MESSAGES = messages;
        persist();
      }
      if (callback) callback(err);
    });
  }

  /**
   * 对当前页面应用翻译（data-i18n 属性）
   */
  function applyI18n(root) {
    var container = root || document;
    container.querySelectorAll('[data-i18n]').forEach(function(el) {
      var key = el.getAttribute('data-i18n');
      var value = t(key);
      if (value) {
        // 处理 placeholder
        if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
          // 对于按钮输入框，改 textContent
          if (el.type === 'button' || el.type === 'submit') {
            el.value = value;
          } else {
            el.textContent = value;
          }
        } else {
          el.textContent = value;
        }
      }
    });

    container.querySelectorAll('[data-i18n-placeholder]').forEach(function(el) {
      el.placeholder = t(el.getAttribute('data-i18n-placeholder'));
    });

    container.querySelectorAll('[data-i18n-title]').forEach(function(el) {
      el.title = t(el.getAttribute('data-i18n-title'));
    });
  }

  // 导出到全局
  window.NovelTransI18n = {
    get: getLanguage,
    t: t,
    init: init,
    set: setLanguage,
    apply: applyI18n
  };
})();
