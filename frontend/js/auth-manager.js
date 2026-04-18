/**
 * NovaTrans 认证管理器
 * 统一管理用户的认证状态和 JWT 令牌
 */

class AuthManager {
  /**
   * 检查用户是否已认证
   * @returns {boolean} 用户是否已认证且令牌未过期
   */
  static isAuthenticated() {
    const token = localStorage.getItem('authToken');
    if (!token) return false;

    try {
      // 检查是否为标准 JWT 格式 (xxx.xxx.xxx)
      const parts = token.split('.');
      if (parts.length !== 3) {
        // 非标准 JWT 格式，只要有 token 就认为已认证
        console.log('Token 格式非标准，但仍视为有效');
        return true;
      }

      // 解析 JWT 载荷检查是否过期
      const payload = JSON.parse(atob(parts[1]));
      const currentTime = Math.floor(Date.now() / 1000);
      return payload.exp > currentTime;
    } catch (e) {
      console.error('JWT 解析错误:', e);
      // 解析失败时，只要有 token 就认为已认证（兼容非标准 token）
      return true;
    }
  }

  /**
   * 获取当前用户的 JWT 令牌
   * @returns {string|null} JWT 令牌或 null
   */
  static getAuthToken() {
    return localStorage.getItem('authToken');
  }

  /**
   * 获取当前用户信息
   * @returns {Object|null} 用户信息对象或 null
   */
  static getCurrentUser() {
    const userInfo = localStorage.getItem('userInfo');
    return userInfo ? JSON.parse(userInfo) : null;
  }

  /**
   * 执行认证请求 - 在请求中自动添加 JWT 认证头
   * @param {string} url - 请求 URL
   * @param {Object} options - fetch 选项
   * @returns {Promise} fetch 响应
   */
  static async makeAuthenticatedRequest(url, options = {}) {
    const token = this.getAuthToken();

    const authenticatedOptions = {
      ...options,
      headers: {
        ...options.headers,
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    };

    return fetch(url, authenticatedOptions);
  }

  /**
   * 登出 - 清除所有认证信息
   */
  static logout() {
    localStorage.removeItem('authToken');
    localStorage.removeItem('userInfo');
    localStorage.removeItem('rememberMe');

    // 触发登出事件，以便其他组件可以响应
    window.dispatchEvent(new CustomEvent('userLoggedOut'));
  }

  /**
   * 登录 - 存储认证信息
   * @param {Object} authData - 包含令牌和用户信息的对象
   */
  static login(authData) {
    const { token, user } = authData;

    localStorage.setItem('authToken', token);
    localStorage.setItem('userInfo', JSON.stringify(user));
  }

  /**
   * 检查是否有记住我标记
   * @returns {boolean} 是否有记住我标记
   */
  static hasRememberMe() {
    return localStorage.getItem('rememberMe') === 'true';
  }

  /**
   * 检查特定 API 端点的访问权限
   * @param {string} endpoint - API 端点路径
   * @returns {Promise<boolean>} 是否有权限访问
   */
  static async checkPermission(endpoint) {
    if (!this.isAuthenticated()) {
      return false;
    }

    try {
      const baseUrl = (typeof CONFIG !== 'undefined') ? CONFIG.API_BASE_URL : 'http://127.0.0.1:7341';
      const response = await this.makeAuthenticatedRequest(`${baseUrl}/api/check-permission?endpoint=${encodeURIComponent(endpoint)}`);
      return response.ok;
    } catch (error) {
      console.error('权限检查失败:', error);
      return false;
    }
  }

  /**
   * 刷新 JWT 令牌
   * @returns {Promise<boolean>} 刷新是否成功
   */
  static async refreshToken() {
    if (!this.getAuthToken()) {
      return false;
    }

    try {
      const baseUrl = (typeof CONFIG !== 'undefined') ? CONFIG.API_BASE_URL : 'http://127.0.0.1:7341';
      const userApiPath = (typeof CONFIG !== 'undefined') ? CONFIG.USER_API_PATH : '/user';
      const refreshUrl = `${baseUrl}${userApiPath}/refresh-token`;
      const refreshResponse = await fetch(refreshUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.getAuthToken()}`
        },
        body: JSON.stringify({ refreshToken: this.getAuthToken() })
      });

      if (refreshResponse.ok) {
        const result = await refreshResponse.json();
        // 根据 API 文档，token 在响应根级别
        if (result.success && result.token) {
          localStorage.setItem('authToken', result.token);
          return true;
        }
        // 或者在 data 中
        if (result.success && result.data && result.data.token) {
          localStorage.setItem('authToken', result.data.token);
          return true;
        }
      }
      return false;
    } catch (error) {
      console.error('刷新令牌失败:', error);
      return false;
    }
  }
}

// 页面加载时验证 JWT 令牌的有效性
document.addEventListener('DOMContentLoaded', function() {
  // 检查 JWT 令牌是否过期
  if (AuthManager.isAuthenticated()) {
    console.log('用户已认证');
    // 触发登录事件，更新 UI
    window.dispatchEvent(new CustomEvent('userLoggedIn'));
  } else {
    // 检查是否有过期的令牌，如果有则清除
    const token = localStorage.getItem('authToken');
    if (token) {
      try {
        const parts = token.split('.');
        if (parts.length === 3) {
          const payload = JSON.parse(atob(parts[1]));
          const currentTime = Math.floor(Date.now() / 1000);

          if (payload.exp <= currentTime) {
            console.log('JWT 令牌已过期，正在清除...');
            AuthManager.logout();
          }
        }
      } catch (e) {
        // 非标准 JWT 格式，不清除
        console.log('Token 格式非标准，保留');
      }
    }
  }
});

// 导出 AuthManager 类
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { AuthManager };
} else {
  window.AuthManager = AuthManager;
}
