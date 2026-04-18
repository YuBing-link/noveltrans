/**
 * NovaTrans 认证中间件
 * 用于在页面加载时检查用户认证状态
 *
 * 用法：
 * 1. 在需要保护的页面中引入此脚本
 * 2. 调用 requireAuth() 函数来保护整个页面
 * 3. 或调用 checkAuth() 函数来检查但不强制重定向
 */

// 认证相关配置（使用 config.js 中的 PAGE 配置）
// 如果 config.js 已定义 PAGE，则使用，否则使用默认值
// 注意：登录是通过首页的模态框实现的，所以 LOGIN_PAGE 设置为 index.html
const AUTH_CONFIG = {
  LOGIN_PAGE: (typeof PAGE !== 'undefined' && PAGE.LOGIN) || 'index.html',
  INDEX_PAGE: (typeof PAGE !== 'undefined' && PAGE.INDEX) || 'index.html'
};

document.addEventListener('DOMContentLoaded', function() {
  // 检查页面是否需要认证
  const protectedPage = document.body.getAttribute('data-protected');

  if (protectedPage === 'true') {
    requireAuth();
  }
});

/**
 * 检查用户认证状态
 * @returns {boolean} 用户是否已认证
 */
function checkAuth() {
  // 检查JWT令牌是否存在且未过期
  const token = localStorage.getItem('authToken');

  if (!token) {
    return false;
  }

  try {
    // 解析JWT载荷以检查过期时间
    const payload = JSON.parse(atob(token.split('.')[1]));
    const currentTime = Math.floor(Date.now() / 1000);

    // 检查令牌是否过期
    if (payload.exp <= currentTime) {
      // 令牌已过期，清除本地存储
      localStorage.removeItem('authToken');
      localStorage.removeItem('userInfo');
      return false;
    }

    return true;
  } catch (e) {
    // 令牌格式无效，清除本地存储
    localStorage.removeItem('authToken');
    localStorage.removeItem('userInfo');
    return false;
  }
}

/**
 * 强制要求用户已认证，否则重定向到登录页
 */
function requireAuth() {
  if (!checkAuth()) {
    // 保存当前页面URL以便登录后重定向回原页面
    localStorage.setItem('redirectAfterLogin', window.location.pathname + window.location.search);

    // 重定向到登录页面
    window.location.href = AUTH_CONFIG.LOGIN_PAGE;
    return false;
  }

  return true;
}

/**
 * 如果用户已认证则重定向到主页
 * 适用于登录、注册等页面
 */
function redirectToHomeIfAuth() {
  if (checkAuth()) {
    window.location.href = AUTH_CONFIG.INDEX_PAGE;
    return true;
  }

  return false;
}

/**
 * 登出用户并重定向
 * @param {string} redirectUrl - 登出后重定向的URL，默认为首页
 */
function logoutUser(redirectUrl = AUTH_CONFIG.INDEX_PAGE) {
  // 清除所有认证相关的本地存储
  localStorage.removeItem('authToken');
  localStorage.removeItem('userInfo');
  localStorage.removeItem('rememberMe');

  // 触发登出事件
  window.dispatchEvent(new CustomEvent('userLoggedOut'));

  // 重定向到指定页面
  window.location.href = redirectUrl;
}

/**
 * 获取重定向URL（如果存在）
 * @returns {string|null} 登录前尝试访问的页面URL
 */
function getRedirectAfterLogin() {
  return localStorage.getItem('redirectAfterLogin');
}

/**
 * 清除重定向URL
 */
function clearRedirectAfterLogin() {
  localStorage.removeItem('redirectAfterLogin');
}

/**
 * 登录成功后处理重定向
 */
function handlePostLoginRedirect() {
  const redirectUrl = getRedirectAfterLogin();
  if (redirectUrl && redirectUrl !== window.location.pathname) {
    clearRedirectAfterLogin();
    window.location.href = redirectUrl;
  } else {
    // 默认重定向到首页
    window.location.href = AUTH_CONFIG.INDEX_PAGE;
  }
}

// 导出函数以便在其他地方使用
window.AuthMiddleware = {
  checkAuth,
  requireAuth,
  redirectToHomeIfAuth,
  logoutUser,
  getRedirectAfterLogin,
  clearRedirectAfterLogin,
  handlePostLoginRedirect
};