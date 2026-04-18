/**
 * 注册页面功能实现
 * 实现注册表单提交和 API 交互逻辑
 */

// 全局变量存储表单元素引用
window.regEmailInput = null;
window.regSendCodeButton = null;

document.addEventListener('DOMContentLoaded', function() {
  // 页面配置（使用 config.js 中的 PAGE 配置）
  const PAGE_CONFIG = {
    LOGIN_PAGE: (typeof PAGE !== 'undefined' && PAGE.LOGIN) || 'login.html'
  };

  // 获取表单元素
  const registerForm = document.getElementById('registerForm');
  const emailInput = document.getElementById('regEmail');
  const passwordInput = document.getElementById('regPassword');
  const confirmPasswordInput = document.getElementById('regConfirmPassword');
  const verificationCodeInput = document.getElementById('regCode');
  const usernameInput = document.getElementById('regUsername');
  const termsCheckbox = document.querySelector('.terms-checkbox input[type="checkbox"]');
  const togglePassword = document.querySelector('#regPassword + .input-wrapper .toggle-icon');
  const toggleConfirmPassword = document.querySelector('#regConfirmPassword + .input-wrapper .toggle-icon');
  const sendCodeButton = document.getElementById('sendCodeBtn');

  // 存储到全局供 onclick 调用
  window.regEmailInput = emailInput;
  window.regSendCodeButton = sendCodeButton;

  // 设置密码可见性切换功能
  if (togglePassword && passwordInput) {
    togglePassword.addEventListener('click', function () {
      const type = passwordInput.getAttribute('type') === 'password' ? 'text' : 'password';
      passwordInput.setAttribute('type', type);
    });
  }

  if (toggleConfirmPassword && confirmPasswordInput) {
    toggleConfirmPassword.addEventListener('click', function () {
      const type = confirmPasswordInput.getAttribute('type') === 'password' ? 'text' : 'password';
      confirmPasswordInput.setAttribute('type', type);
    });
  }

  // 发送验证码功能 - 绑定到全局函数
  window.sendVerificationCode = async function() {
    if (!window.regEmailInput || !window.regSendCodeButton) {
      alert('表单未加载完成，请稍后重试');
      return;
    }

    const btn = window.regSendCodeButton;
    if (btn.disabled) return;

    const email = window.regEmailInput.value.trim();

    // 验证邮箱
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!email) {
      showRegNotification('warning', '验证失败', '请先输入邮箱地址');
      return;
    }
    if (!emailRegex.test(email)) {
      showRegNotification('warning', '验证失败', '邮箱格式不正确');
      return;
    }

    // 显示加载状态
    btn.disabled = true;
    const originalText = btn.textContent;
    btn.textContent = '发送中...';

    try {
      // 调用 API 发送验证码
      const result = await apiClient.sendVerificationCode(email);

      if (result.success) {
        showRegNotification('success', '验证码已发送', '验证码已发送至您的邮箱，请查收');

        // 开始倒计时
        let secondsLeft = 60;
        btn.textContent = secondsLeft + '秒后重发';

        const countdownInterval = setInterval(() => {
          secondsLeft--;
          btn.textContent = secondsLeft + '秒后重发';

          if (secondsLeft <= 0) {
            clearInterval(countdownInterval);
            btn.disabled = false;
            btn.textContent = '发送验证码';
          }
        }, 1000);
      } else {
        let errorMessage = '发送验证码失败';
        if (result.data && result.data.code) {
          switch (result.data.code) {
            case '400': errorMessage = result.data.message || '参数错误'; break;
            case '500': errorMessage = '服务器错误，请稍后再试'; break;
            default: errorMessage = result.data.message || errorMessage;
          }
        }
        showRegNotification('warning', '发送失败', errorMessage);
        btn.disabled = false;
        btn.textContent = '发送验证码';
      }
    } catch (error) {
      console.error('Send code error:', error);
      showRegNotification('warning', '发送失败', '网络错误或服务器不可用');
      btn.disabled = false;
      btn.textContent = '发送验证码';
    }
  };

  // 通知函数
  function showRegNotification(type, title, message) {
    if (typeof UITools !== 'undefined') {
      UITools.showNotification(type, title, message);
    } else if (typeof window.showNotification !== 'undefined') {
      window.showNotification(type, title, message);
    } else {
      alert(`${title}: ${message}`);
    }
  }

  // 注册表单提交事件监听器
  if (registerForm) {
    registerForm.addEventListener('submit', async function(e) {
      e.preventDefault();

      // 获取表单数据
      const email = emailInput.value.trim();
      const password = passwordInput.value;
      const confirmPassword = confirmPasswordInput.value;
      const verificationCode = verificationCodeInput ? verificationCodeInput.value.trim() : '';
      const username = usernameInput ? usernameInput.value.trim() : '';

      // 表单验证
      if (!email || !password || !confirmPassword || !verificationCode || !username) {
        showRegNotification('warning', '验证失败', '请填写所有必填项');
        return;
      }

      if (!termsCheckbox || !termsCheckbox.checked) {
        showRegNotification('warning', '验证失败', '请同意服务条款和隐私政策');
        return;
      }

      // 邮箱格式验证
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(email)) {
        showRegNotification('warning', '验证失败', '邮箱格式不正确');
        return;
      }

      // 密码长度验证
      if (password.length < 6) {
        showRegNotification('warning', '验证失败', '密码长度至少为 6 位');
        return;
      }

      // 密码一致性验证
      if (password !== confirmPassword) {
        showRegNotification('warning', '验证失败', '两次输入的密码不一致');
        return;
      }

      // 显示加载状态
      const submitButton = registerForm.querySelector('button[type="submit"]');
      const originalButtonText = submitButton.innerHTML;
      submitButton.disabled = true;
      submitButton.innerHTML = '<span class="loading-spinner"></span> 注册中...';

      try {
        // 调用 API 进行注册
        const result = await apiClient.register({
          email: email,
          password: password,
          code: verificationCode,
          username: username
        });

        if (result.success) {
          showRegNotification('success', '注册成功', '您的账户已成功创建！正在自动登录...');

          // 注册成功后自动登录
          setTimeout(async () => {
            try {
              const loginResult = await apiClient.login({
                email: email,
                password: password
              });

              if (loginResult.success) {
                showRegNotification('success', '登录成功', '欢迎加入！');

                // 关闭注册模态框
                const registerModal = document.getElementById('registerModal');
                if (registerModal) {
                  registerModal.classList.remove('active');
                }
                document.body.style.overflow = '';

                // 刷新页面或更新 UI 状态
                setTimeout(() => {
                  window.location.reload();
                }, 1500);
              } else {
                // 自动登录失败，提示用户手动登录
                showRegNotification('info', '提示', '注册成功，请关闭弹窗后手动登录');
              }
            } catch (loginError) {
              console.error('Auto login error:', loginError);
              showRegNotification('info', '提示', '注册成功，请关闭弹窗后手动登录');
            }
          }, 1000);
        } else {
          let errorMessage = result.error || '注册失败';

          // 根据错误码提供更具体的错误信息
          if (result.data && result.data.code) {
            switch (result.data.code) {
              case 'U003': errorMessage = '验证码错误或已过期，请重新获取'; break;
              case 'U004': errorMessage = '该邮箱已被注册，请直接登录或使用其他邮箱'; break;
              case 'U005': errorMessage = '邮箱已被注册'; break;
              case 'U006': errorMessage = '邮箱格式不正确'; break;
              case 'U007': errorMessage = '密码长度不够，至少 6 位'; break;
              case 'U008': errorMessage = '验证码错误或已过期'; break;
              case '400': errorMessage = '参数错误：' + (result.data.message || '请检查输入信息'); break;
              case '500': errorMessage = '服务器错误，请稍后再试'; break;
              default: errorMessage = result.data.message || errorMessage;
            }
          }

          showRegNotification('warning', '注册失败', errorMessage);
        }
      } catch (error) {
        console.error('Registration error:', error);
        showRegNotification('warning', '注册失败', '网络错误或服务器不可用');
      } finally {
        // 恢复按钮状态
        submitButton.disabled = false;
        submitButton.innerHTML = originalButtonText;
      }
    });
  }
});
