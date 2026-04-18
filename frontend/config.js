/**
 * NovelTrans 全局配置
 * 统一管理 API 接口路径和配置项
 *
 * 引入顺序：在所有 JS 文件之前引入此文件
 * <script src="config.js"></script>
 */

const CONFIG = {
  // API 基础 URL
  // 优先级：1. window.API_BASE_URL (外部配置) > 2. 环境变量 > 3. 默认值
  API_BASE_URL: (typeof window !== 'undefined' && window.API_BASE_URL) ||
                'http://127.0.0.1:7341',

  // 用户 API 基础路径
  USER_API_PATH: '/user',

  // 翻译 API 基础路径
  TRANSLATE_API_PATH: '/v1/translate',
};

// 页面路由配置
const PAGE = {
  // 首页
  INDEX: 'index.html',
  // 首页（备用路径）
  HOME: 'home.html',
  // 翻译页
  TRANSLATION: 'translation.html',
  // 用户中心
  USER: 'user.html',
  // 帮助页
  HELP: 'help.html',
  // 关于页
  ABOUT: 'about.html',
  // 历史页
  HISTORY: 'history.html',
  // 设置页
  SETTINGS: 'settings.html',
  // 术语库页
  GLOSSARY: 'glossary.html',
  // 隐私政策
  PRIVACY: 'privacy.html',
  // 服务条款
  TERMS: 'terms.html',
  // 登录页（登录是通过首页的模态框实现的）
  LOGIN: 'index.html?modal=login',
  // 注册页（注册是通过首页的模态框实现的）
  REGISTER: 'index.html?modal=register',
  // 验证码页
  VERIFICATION: 'verification.html',
};

// 用户认证模块
const API = {
  // 发送验证码
  SEND_CODE: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/send-code',

  // 用户注册
  REGISTER: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/register',

  // 用户登录
  LOGIN: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/login',

  // 第三方登录
  OAUTH: (provider) => `${CONFIG.API_BASE_URL}${CONFIG.USER_API_PATH}/oauth/${provider}`,

  // 刷新令牌
  REFRESH_TOKEN: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/refresh-token',

  // 获取用户信息
  PROFILE: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/profile',

  // 更新用户信息
  UPDATE_PROFILE: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/profile',

  // 修改密码
  CHANGE_PASSWORD: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/change-password',

  // 重置密码
  RESET_PASSWORD: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/reset-password',

  // 退出登录
  LOGOUT: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/logout',

  // 获取用户配额
  QUOTA: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/quota',

  // 获取用户统计
  STATISTICS: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/statistics',

  // 获取翻译历史
  TRANSLATION_HISTORY: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/translation-history',

  // 获取文档列表
  DOCUMENTS: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/documents',

  // 获取文档详情
  DOCUMENT: (docId) => `${CONFIG.API_BASE_URL}${CONFIG.USER_API_PATH}/documents/${docId}`,

  // 删除文档
  DELETE_DOCUMENT: (docId) => `${CONFIG.API_BASE_URL}${CONFIG.USER_API_PATH}/documents/${docId}`,

  // 重新翻译
  RETRY_DOCUMENT: (docId) => `${CONFIG.API_BASE_URL}${CONFIG.USER_API_PATH}/documents/${docId}/retry`,

  // 上传文档
  UPLOAD_DOCUMENT: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/documents/upload',

  // 下载文档
  DOWNLOAD_DOCUMENT: (docId) => `${CONFIG.API_BASE_URL}${CONFIG.USER_API_PATH}/documents/${docId}/download`,

  // 注册设备
  REGISTER_DEVICE: CONFIG.API_BASE_URL + CONFIG.USER_API_PATH + '/register-device',

  // 获取设备 Token
  GET_TOKEN_DEVICE: (deviceId) => `${CONFIG.API_BASE_URL}${CONFIG.USER_API_PATH}/get-token/${deviceId}`,
};

// 翻译模块
const TRANSLATE_API = {
  // 文本翻译
  TEXT: CONFIG.API_BASE_URL + CONFIG.TRANSLATE_API_PATH + '/text',

  // 选中翻译
  SELECTION: CONFIG.API_BASE_URL + CONFIG.TRANSLATE_API_PATH + '/selection',

  // 阅读器翻译
  READER: CONFIG.API_BASE_URL + CONFIG.TRANSLATE_API_PATH + '/reader',

  // 网页翻译（流式）
  WEBPAGE: CONFIG.API_BASE_URL + CONFIG.TRANSLATE_API_PATH + '/webpage',

  // 文档翻译
  DOCUMENT: CONFIG.API_BASE_URL + CONFIG.TRANSLATE_API_PATH + '/document',

  // 查询任务状态
  TASK: (taskId) => `${CONFIG.API_BASE_URL}${CONFIG.TRANSLATE_API_PATH}/task/${taskId}`,

  // 开始文档翻译
  START_TASK: (docId) => `${CONFIG.API_BASE_URL}${CONFIG.USER_API_PATH}/documents/${docId}/start`,

  // 取消任务
  CANCEL_TASK: (taskId) => `${CONFIG.API_BASE_URL}${CONFIG.TRANSLATE_API_PATH}/task/${taskId}`,

  // 获取翻译结果
  TASK_RESULT: (taskId) => `${CONFIG.API_BASE_URL}${CONFIG.TRANSLATE_API_PATH}/task/${taskId}/result`,

  // 下载翻译结果
  TASK_DOWNLOAD: (taskId) => `${CONFIG.API_BASE_URL}${CONFIG.TRANSLATE_API_PATH}/task/${taskId}/download`,

  // 高级翻译（认证用户）
  PREMIUM_SELECTION: CONFIG.API_BASE_URL + CONFIG.TRANSLATE_API_PATH + '/premium-selection',
  PREMIUM_READER: CONFIG.API_BASE_URL + CONFIG.TRANSLATE_API_PATH + '/premium-reader',
};

// 平台模块
const PLATFORM_API = {
  // 平台统计
  STATS: CONFIG.API_BASE_URL + '/platform/stats',

  // 平台配置
  CONFIG: CONFIG.API_BASE_URL + '/platform/config',
};

// 上传配置
const UPLOAD_CONFIG = {
  // 支持的文件类型
  ALLOWED_TYPES: ['.txt', '.epub', '.docx', '.pdf'],

  // 文件大小限制（MB）
  MAX_SIZE_MB: 50,

  // 支持的语言
  SUPPORTED_LANGUAGES: {
    source: [
      { value: 'auto', label: '自动检测' },
      { value: 'ja', label: '日语' },
      { value: 'en', label: '英语' },
      { value: 'ko', label: '韩语' },
      { value: 'zh', label: '中文' }
    ],
    target: [
      { value: 'zh', label: '中文' },
      { value: 'en', label: '英语' },
      { value: 'ja', label: '日语' },
      { value: 'ko', label: '韩语' }
    ]
  },

  // 翻译引擎
  TRANSLATION_ENGINES: [
    { value: 'ai', label: 'AI 智能翻译（推荐）' },
    { value: 'neural', label: '神经网络翻译' },
    { value: 'statistical', label: '统计翻译' }
  ]
};

// 导出配置
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { CONFIG, API, TRANSLATE_API, PAGE, UPLOAD_CONFIG };
} else {
  window.CONFIG = CONFIG;
  window.API = API;
  window.TRANSLATE_API = TRANSLATE_API;
  window.PAGE = PAGE;
  window.UPLOAD_CONFIG = UPLOAD_CONFIG;
}
