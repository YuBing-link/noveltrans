/**
 * NovaTrans 通用认证工具
 * 用于在不同页面之间共享认证相关功能
 */

// 页面配置（使用 config.js 中的 PAGE 配置）
const PAGE_CONFIG = {
  LOGIN_PAGE: (typeof PAGE !== 'undefined' && PAGE.LOGIN) || 'login.html',
  INDEX_PAGE: (typeof PAGE !== 'undefined' && PAGE.INDEX) || 'index.html'
};

// 在 DOM 加载完成后执行初始化
document.addEventListener('DOMContentLoaded', function() {
  // 检查并更新页面上的认证 UI
  updateAuthUI();

  // 监听登录/登出事件
  window.addEventListener('userLoggedIn', function() {
    updateAuthUI();
  });

  window.addEventListener('userLoggedOut', function() {
    updateAuthUI();
  });

  // 设置导航栏认证相关元素
  setupAuthNavigation();
});

/**
 * 更新页面上的认证相关 UI 元素
 */
function updateAuthUI() {
  // 更新头部的用户菜单
  const userMenuElement = document.getElementById('user-menu');
  const loginLink = document.getElementById('login-link');
  const logoutLink = document.getElementById('logout-link');
  const userNameElement = document.getElementById('user-name');
  const userAvatarElement = document.getElementById('user-avatar');

  if (AuthManager.isAuthenticated()) {
    // 用户已登录
    if (loginLink) {
      loginLink.style.display = 'none';
    }

    if (logoutLink) {
      logoutLink.style.display = 'inline-flex';
      logoutLink.style.alignItems = 'center';
      logoutLink.style.justifyContent = 'center';
    }

    const currentUser = AuthManager.getCurrentUser();
    if (currentUser && userNameElement) {
      // 优先使用用户名，支持 userName 和 username 两种字段名
      const name = currentUser.userName || currentUser.username || currentUser.email || currentUser.nickname || '用户';
      userNameElement.textContent = name;

      // 更新头像首字母
      if (userAvatarElement) {
        const firstChar = name.charAt(0).toUpperCase();
        userAvatarElement.textContent = firstChar;
      }
    }

    if (userMenuElement) {
      userMenuElement.style.display = 'flex';
    }
  } else {
    // 用户未登录
    if (loginLink) {
      loginLink.style.display = 'flex';
    }

    if (logoutLink) {
      logoutLink.style.display = 'none';
    }

    if (userMenuElement) {
      userMenuElement.style.display = 'none';
    }
  }
}

/**
 * 设置导航栏中的认证相关链接
 */
function setupAuthNavigation() {
  const logoutLink = document.getElementById('logout-link');

  if (logoutLink) {
    logoutLink.addEventListener('click', function(e) {
      e.preventDefault();

      // 使用自定义确认对话框
      if (typeof UITools !== 'undefined' && typeof UITools.confirmDialog === 'function') {
        UITools.confirmDialog('确认退出', '确定要退出登录吗？').then((confirmed) => {
          if (confirmed) {
            performLogout();
          }
        });
      } else if (confirm('确定要退出登录吗？')) {
        performLogout();
      }
    });
  }
}

/**
 * 执行登出操作
 */
function performLogout() {
  // 清除认证信息
  AuthManager.logout();
  // 重定向到首页
  window.location.href = PAGE_CONFIG.INDEX_PAGE;
}

/**
 * 保护路由 - 检查用户是否有权访问当前页面
 * @param {Array} allowedRoles - 允许访问的角色列表（可选）
 * @returns {boolean} 是否有权访问
 */
function protectRoute(allowedRoles = []) {
  if (!AuthManager.isAuthenticated()) {
    // 未认证用户重定向到登录页
    window.location.href = PAGE_CONFIG.LOGIN_PAGE;
    return false;
  }

  // 如果指定了允许的角色，检查用户角色
  if (allowedRoles.length > 0) {
    const currentUser = AuthManager.getCurrentUser();
    if (currentUser && currentUser.role) {
      if (!allowedRoles.includes(currentUser.role)) {
        // 用户角色无权访问此页面
        window.location.href = PAGE_CONFIG.INDEX_PAGE; // 或者显示 403 页面
        return false;
      }
    }
  }

  return true;
}

/**
 * 显示认证相关的通知
 * @param {string} type - 通知类型 ('success', 'warning', 'error')
 * @param {string} title - 通知标题
 * @param {string} message - 通知消息
 */
function showAuthNotification(type, title, message) {
  // 优先使用 UITools
  if (typeof UITools !== 'undefined') {
    UITools.showNotification(type, title, message);
    return;
  }

  // 检查是否存在通知元素，如果没有则创建
  let notification = document.getElementById('notification');
  if (!notification) {
    // 创建通知元素
    notification = document.createElement('div');
    notification.id = 'notification';
    notification.className = 'notification';

    const icon = document.createElement('i');
    icon.className = 'ri-checkbox-circle-fill notif-icon';

    const content = document.createElement('div');
    content.className = 'notif-content';

    const titleEl = document.createElement('strong');
    titleEl.id = 'notif-title';

    const msgEl = document.createElement('p');
    msgEl.id = 'notif-msg';

    content.appendChild(titleEl);
    content.appendChild(msgEl);
    notification.appendChild(icon);
    notification.appendChild(content);

    // 添加样式
    const style = document.createElement('style');
    style.textContent = `
      .notification {
        position: fixed;
        top: 80px;
        right: 24px;
        background: white;
        padding: 16px;
        border-radius: 8px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        border-left: 4px solid #3b82f6;
        display: flex;
        align-items: center;
        gap: 12px;
        transform: translateX(150%);
        transition: transform 0.3s ease-out;
        z-index: 1000;
        min-width: 300px;
      }
      .notification.show {
        transform: translateX(0);
      }
      .notification.success {
        border-left-color: #10b981;
      }
      .notification.warning {
        border-left-color: #f59e0b;
      }
      .notification.error {
        border-left-color: #ef4444;
      }
      .notif-icon {
        font-size: 20px;
      }
      .success .notif-icon {
        color: #10b981;
      }
      .warning .notif-icon {
        color: #f59e0b;
      }
      .error .notif-icon {
        color: #ef4444;
      }
      .notif-content strong {
        display: block;
        font-size: 14px;
        margin-bottom: 2px;
      }
      .notif-content p {
        margin: 0;
        font-size: 12px;
        color: #64748b;
      }
    `;
    document.head.appendChild(style);

    document.body.appendChild(notification);
  }

  const titleEl = document.getElementById('notif-title') || notification.querySelector('#notif-title') || notification.querySelector('.notif-content strong');
  const msgEl = document.getElementById('notif-msg') || notification.querySelector('#notif-msg') || notification.querySelector('.notif-content p');
  const icon = notification.querySelector('.notif-icon');

  // 更新内容
  if (titleEl) titleEl.textContent = title;
  if (msgEl) msgEl.textContent = message;

  // 更新样式和图标
  notification.className = `notification show ${type}`;

  if (icon) {
    if (type === 'success') {
      icon.className = 'ri-checkbox-circle-fill notif-icon';
    } else if (type === 'warning') {
      icon.className = 'ri-alert-line notif-icon';
    } else if (type === 'error') {
      icon.className = 'ri-error-warning-fill notif-icon';
    }
  }

  // 自动隐藏通知
  setTimeout(() => {
    notification.classList.remove('show');
  }, 3000);
}

/**
 * 向需要认证的 API 端点发出请求
 * @param {string} url - API 端点 URL
 * @param {Object} options - fetch 请求选项
 * @returns {Promise} API 响应结果
 */
async function makeAuthenticatedApiCall(url, options = {}) {
  // 检查认证状态
  if (!AuthManager.isAuthenticated()) {
    window.location.href = PAGE_CONFIG.LOGIN_PAGE;
    return { success: false, error: '未认证用户' };
  }

  try {
    // 使用认证管理器发起带认证头的请求
    const response = await AuthManager.makeAuthenticatedRequest(url, options);

    if (response.status === 401) {
      // 认证失败，可能是令牌过期
      showAuthNotification('warning', '认证失败', '登录状态已过期，请重新登录');
      AuthManager.logout();
      window.location.href = PAGE_CONFIG.LOGIN_PAGE;
      return { success: false, error: '认证失败' };
    }

    const result = await response.json();
    return result;
  } catch (error) {
    console.error('API 调用错误:', error);
    return { success: false, error: error.message };
  }
}

// 暴露函数到全局作用域
window.updateAuthUI = updateAuthUI;
window.setupAuthNavigation = setupAuthNavigation;
window.protectRoute = protectRoute;
window.showAuthNotification = showAuthNotification;
window.makeAuthenticatedApiCall = makeAuthenticatedApiCall;
