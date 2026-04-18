/**
 * 登录页面功能实现
 * 保留：记住我自动填充、密码可见性切换
 * 移除：表单提交处理（已由 index.html 统一处理，避免重复监听）
 */

document.addEventListener('DOMContentLoaded', function() {
  // 页面配置（使用 config.js 中的 PAGE 配置）
  const PAGE_CONFIG = {
    INDEX_PAGE: (typeof PAGE !== 'undefined' && PAGE.INDEX) || 'index.html'
  };

  // 检查记住我状态，自动填充登录表单
  const rememberMe = localStorage.getItem('rememberMe');
  if (rememberMe === 'true') {
    const userInfo = AuthManager.getCurrentUser();
    if (userInfo && userInfo.email) {
      const emailInput = document.getElementById('loginEmail');
      if (emailInput) {
        emailInput.value = userInfo.email;
      }
      const rememberMeCheckbox = document.getElementById('rememberMe');
      if (rememberMeCheckbox) {
        rememberMeCheckbox.checked = true;
      }
    }
  }

  // 获取表单元素
  const loginForm = document.getElementById('loginForm');
  const passwordInput = document.getElementById('loginPassword');
  const togglePassword = document.querySelector('#loginPassword + .input-wrapper .toggle-icon');

  // 设置密码可见性切换功能
  if (togglePassword && passwordInput) {
    togglePassword.addEventListener('click', function () {
      const type = passwordInput.getAttribute('type') === 'password' ? 'text' : 'password';
      passwordInput.setAttribute('type', type);
    });
  }

  // 注意：表单提交事件已由 index.html 统一处理，避免重复监听
});
