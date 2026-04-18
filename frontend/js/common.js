/**
 * NovaTrans 页面通用脚本
 * 提供认证和其他通用功能
 */

// 在DOM加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
  // 初始化认证相关的功能
  initializeAuthFeatures();

  // 其他页面初始化逻辑...
});

/**
 * 初始化认证相关功能
 */
function initializeAuthFeatures() {
  // 检查并更新页面上的认证UI
  if (typeof updateAuthUI !== 'undefined') {
    updateAuthUI();
  }

  // 设置认证导航
  if (typeof setupAuthNavigation !== 'undefined') {
    setupAuthNavigation();
  }

  // 检查当前页面是否需要认证
  const protectedPage = document.body.getAttribute('data-protected');
  if (protectedPage === 'true') {
    if (typeof requireAuth !== 'undefined') {
      requireAuth();
    } else if (typeof AuthMiddleware !== 'undefined') {
      AuthMiddleware.requireAuth();
    }
  }
}

/**
 * 显示加载状态
 * @param {HTMLElement} element - 要显示加载状态的元素
 */
function showLoading(element) {
  const originalContent = element.innerHTML;
  element.innerHTML = '<i class="ri-loader-4-line ri-spin"></i> 处理中...';
  element.disabled = true;

  // 返回一个函数来恢复原始内容
  return function() {
    element.innerHTML = originalContent;
    element.disabled = false;
  };
}

/**
 * 验证JWT令牌是否过期
 * @param {string} token - JWT令牌
 * @returns {boolean} 令牌是否有效且未过期
 */
function isTokenValid(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    const currentTime = Math.floor(Date.now() / 1000);
    return payload.exp > currentTime;
  } catch (e) {
    console.error('JWT验证失败:', e);
    return false;
  }
}

/**
 * 显示错误消息
 * @param {string} message - 错误消息
 */
function showError(message) {
  if (typeof UITools !== 'undefined') {
    UITools.showNotification('error', '操作失败', message);
  } else if (typeof showAuthNotification !== 'undefined') {
    showAuthNotification('error', '操作失败', message);
  } else {
    UITools.alertDialog('操作失败', message, 'error');
  }
}

/**
 * 显示成功消息
 * @param {string} message - 成功消息
 */
function showSuccess(message) {
  if (typeof UITools !== 'undefined') {
    UITools.showNotification('success', '操作成功', message);
  } else if (typeof showAuthNotification !== 'undefined') {
    showAuthNotification('success', '操作成功', message);
  } else {
    UITools.alertDialog('操作成功', message, 'success');
  }
}

/**
 * 显示警告消息
 * @param {string} message - 警告消息
 */
function showWarning(message) {
  if (typeof UITools !== 'undefined') {
    UITools.showNotification('warning', '注意', message);
  } else if (typeof showAuthNotification !== 'undefined') {
    showAuthNotification('warning', '注意', message);
  } else {
    UITools.alertDialog('注意', message, 'warning');
  }
}

/**
 * 验证邮箱格式
 * @param {string} email - 邮箱地址
 * @returns {boolean} 邮箱格式是否正确
 */
function isValidEmail(email) {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email);
}

/**
 * 验证密码强度
 * @param {string} password - 密码
 * @returns {boolean} 密码强度是否足够
 */
function isStrongPassword(password) {
  // 至少8个字符，包含大小写字母、数字和特殊字符
  const strongPasswordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
  return strongPasswordRegex.test(password);
}

// 暴露函数到全局作用域（如果未被模块化环境处理）
if (typeof window !== 'undefined') {
  window.initializeAuthFeatures = initializeAuthFeatures;
  window.showLoading = showLoading;
  window.isTokenValid = isTokenValid;
  window.showError = showError;
  window.showSuccess = showSuccess;
  window.showWarning = showWarning;
  window.isValidEmail = isValidEmail;
  window.isStrongPassword = isStrongPassword;
}