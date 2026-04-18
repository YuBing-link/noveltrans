/**
 * NovaTrans 通用 UI 工具库
 * 包含通用的 UI 功能、表单处理和验证等
 */

// UI 工具类定义
class UITools {
  /**
   * 显示通知
   * @param {string} type - 通知类型 ('success', 'warning', 'error', 'info')
   * @param {string} title - 通知标题
   * @param {string} message - 通知消息
   * @param {number} duration - 显示持续时间（毫秒），0表示永久显示
   */
  static showNotification(type, title, message, duration = 3000) {
    // 尝试使用现有的通知系统
    if (typeof NovaTransUtils !== 'undefined') {
      NovaTransUtils.showNotification(type, title, message, duration);
      return;
    }

    // 检查是否为深色模式
    const isDarkMode = document.documentElement.getAttribute('data-theme') === 'dark';

    // 创建或获取通知容器
    let container = document.getElementById('notification-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'notification-container';
      container.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        z-index: 10000;
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 10px;
        max-width: 400px;
      `;
      document.body.appendChild(container);
    }

    // 创建通知元素
    const notification = document.createElement('div');
    notification.className = `notification notification-${type}`;
    notification.style.cssText = `
      background: ${isDarkMode ? '#1e1e1e' : 'white'};
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      border-left: 4px solid #3b82f6;
      padding: 16px;
      min-width: 300px;
      display: flex;
      align-items: flex-start;
      gap: 12px;
      opacity: 0;
      transform: translateX(100%);
      animation: slideIn 0.3s forwards;
    `;

    // 根据类型设置边框颜色
    if (type === 'success') {
      notification.style.borderLeftColor = '#10b981';
    } else if (type === 'warning') {
      notification.style.borderLeftColor = '#f59e0b';
    } else if (type === 'error') {
      notification.style.borderLeftColor = '#ef4444';
    } else if (type === 'info') {
      notification.style.borderLeftColor = '#3b82f6';
    }

    // 创建图标
    const icon = document.createElement('i');
    icon.className = 'notif-icon';
    icon.style.fontSize = '20px';

    if (type === 'success') {
      icon.className += ' ri-checkbox-circle-fill';
      icon.style.color = '#10b981';
    } else if (type === 'warning') {
      icon.className += ' ri-alert-line';
      icon.style.color = '#f59e0b';
    } else if (type === 'error') {
      icon.className += ' ri-error-warning-fill';
      icon.style.color = '#ef4444';
    } else if (type === 'info') {
      icon.className += ' ri-information-line';
      icon.style.color = '#3b82f6';
    }

    // 创建内容区域（使用安全的文本节点）
    const content = document.createElement('div');
    content.className = 'notif-content';

    const titleEl = document.createElement('strong');
    titleEl.style.cssText = `display: block; font-size: 14px; margin-bottom: 2px; color: ${isDarkMode ? '#e5e5e5' : '#1f2937'};`;
    titleEl.textContent = title;

    const messageEl = document.createElement('p');
    messageEl.style.cssText = `margin: 0; font-size: 12px; color: ${isDarkMode ? '#a3a3a3' : '#64748b'};`;
    messageEl.textContent = message;

    content.appendChild(titleEl);
    content.appendChild(messageEl);

    notification.appendChild(icon);
    notification.appendChild(content);

    // 添加到容器
    container.appendChild(notification);

    // 添加滑入动画
    const style = document.createElement('style');
    style.textContent = `
      @keyframes slideIn {
        from { opacity: 0; transform: translateX(100%); }
        to { opacity: 1; transform: translateX(0); }
      }
      @keyframes slideOut {
        from { opacity: 1; transform: translateX(0); }
        to { opacity: 0; transform: translateX(100%); }
      }
    `;

    // 如果页面还没有动画样式，添加它
    if (!document.querySelector('style[data-notif-style]')) {
      style.setAttribute('data-notif-style', 'true');
      document.head.appendChild(style);
    }

    // 自动移除通知
    if (duration > 0) {
      setTimeout(() => {
        notification.style.animation = 'slideOut 0.3s forwards';
        setTimeout(() => {
          if (notification.parentNode) {
            notification.parentNode.removeChild(notification);
          }
        }, 300);
      }, duration);
    }

    // 点击关闭
    notification.addEventListener('click', () => {
      notification.style.animation = 'slideOut 0.3s forwards';
      setTimeout(() => {
        if (notification.parentNode) {
          notification.parentNode.removeChild(notification);
        }
      }, 300);
    });
  }

  /**
   * 显示加载状态
   * @param {HTMLElement} element - 要显示加载状态的元素
   */
  static showLoading(element) {
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
   * 显示确认对话框
   * @param {string} title - 对话框标题
   * @param {string} message - 对话框消息
   * @returns {Promise<boolean>} 用户选择的结果
   */
  static confirmDialog(title, message) {
    return new Promise((resolve) => {
      // 尝试使用现有的对话框功能
      if (typeof NovaTransUtils !== 'undefined') {
        NovaTransUtils.confirmDialog(title, message).then(resolve);
        return;
      }

      // 检查是否为深色模式
      const isDarkMode = document.documentElement.getAttribute('data-theme') === 'dark';

      // 创建模态框
      const modal = document.createElement('div');
      modal.className = 'confirm-modal';
      modal.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 10001;
        opacity: 0;
        transition: opacity 0.3s ease;
      `;

      modal.innerHTML = `
        <div style="
          background: ${isDarkMode ? '#1e1e1e' : 'white'};
          border-radius: 14px;
          padding: 28px;
          min-width: 380px;
          max-width: 450px;
          box-shadow: 0 25px 50px -12px rgba(124, 58, 237, 0.25);
          text-align: center;
        ">
          <div style="
            width: 64px;
            height: 64px;
            margin: 0 auto 16px;
            background: ${isDarkMode ? 'rgba(124, 58, 237, 0.2)' : 'linear-gradient(135deg, #f5f3ff, #ede9fe)'};
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
          ">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#7c3aed" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
              <polyline points="16 17 21 12 16 7"></polyline>
              <line x1="21" y1="12" x2="9" y2="12"></line>
            </svg>
          </div>
          <h3 style="margin: 0 0 10px 0; color: ${isDarkMode ? '#e5e5e5' : '#1f2937'}; font-size: 20px; font-weight: 600;">${title}</h3>
          <p style="margin: 0 0 24px 0; color: ${isDarkMode ? '#a3a3a3' : '#6b7280'}; line-height: 1.6; font-size: 14px;">${message}</p>
          <div style="display: flex; justify-content: center; gap: 12px;">
            <button id="cancel-btn" style="
              padding: 10px 28px;
              border: 1px solid ${isDarkMode ? '#404040' : '#e5e7eb'};
              border-radius: 14px;
              background: ${isDarkMode ? '#2d2d2d' : 'white'};
              color: ${isDarkMode ? '#e5e5e5' : '#6b7280'};
              cursor: pointer;
              font-weight: 500;
              font-size: 14px;
              transition: all 0.2s;
            ">取消</button>
            <button id="confirm-btn" style="
              padding: 10px 28px;
              border: none;
              border-radius: 14px;
              background: linear-gradient(135deg, #7c3aed, #8b5cf6);
              color: white;
              cursor: pointer;
              font-weight: 500;
              font-size: 14px;
              box-shadow: 0 4px 12px rgba(124, 58, 237, 0.3);
              transition: all 0.2s;
            ">确定</button>
          </div>
        </div>
      `;

      document.body.appendChild(modal);

      // 触发布局重绘后添加淡入效果
      requestAnimationFrame(() => {
        modal.style.opacity = '1';
      });

      // 绑定事件
      const confirmBtn = modal.querySelector('#confirm-btn');
      const cancelBtn = modal.querySelector('#cancel-btn');

      // 添加按钮悬停效果
      confirmBtn.addEventListener('mouseenter', () => {
        confirmBtn.style.transform = 'translateY(-2px)';
        confirmBtn.style.boxShadow = '0 6px 16px rgba(124, 58, 237, 0.4)';
      });
      confirmBtn.addEventListener('mouseleave', () => {
        confirmBtn.style.transform = 'translateY(0)';
        confirmBtn.style.boxShadow = '0 4px 12px rgba(124, 58, 237, 0.3)';
      });

      cancelBtn.addEventListener('mouseenter', () => {
        cancelBtn.style.background = '#f9fafb';
        cancelBtn.style.borderColor = '#d1d5db';
      });
      cancelBtn.addEventListener('mouseleave', () => {
        cancelBtn.style.background = 'white';
        cancelBtn.style.borderColor = '#e5e7eb';
      });

      const closeModal = (result) => {
        modal.style.opacity = '0';
        setTimeout(() => {
          if (modal.parentNode) {
            modal.parentNode.removeChild(modal);
          }
          resolve(result);
        }, 300);
      };

      confirmBtn.addEventListener('click', () => closeModal(true));
      cancelBtn.addEventListener('click', () => closeModal(false));

      // 点击背景关闭
      modal.addEventListener('click', (e) => {
        if (e.target === modal) {
          closeModal(false);
        }
      });
    });
  }

  /**
   * 显示警告/消息对话框
   * @param {string} title - 对话框标题
   * @param {string} message - 对话框消息
   * @param {string} type - 对话框类型 ('info', 'warning', 'error', 'success')
   * @returns {Promise<void>} 用户关闭对话框时 resolve
   */
  static alertDialog(title, message, type = 'info') {
    return new Promise((resolve) => {
      // 检查是否为深色模式
      const isDarkMode = document.documentElement.getAttribute('data-theme') === 'dark';

      // 创建模态框
      const modal = document.createElement('div');
      modal.className = 'alert-modal';
      modal.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 10001;
        opacity: 0;
        transition: opacity 0.3s ease;
      `;

      // 根据类型设置图标和颜色
      const iconConfig = {
        info: { icon: 'info', color: '#7c3aed', bg: isDarkMode ? 'rgba(124, 58, 237, 0.2)' : '#f5f3ff' },
        warning: { icon: 'warning', color: '#f59e0b', bg: isDarkMode ? 'rgba(245, 158, 11, 0.2)' : '#fef3c7' },
        error: { icon: 'error', color: '#ef4444', bg: isDarkMode ? 'rgba(239, 68, 68, 0.2)' : '#fee2e2' },
        success: { icon: 'success', color: '#10b981', bg: isDarkMode ? 'rgba(16, 185, 129, 0.2)' : '#d1fae5' }
      };

      const config = iconConfig[type] || iconConfig.info;

      // SVG 图标定义
      const icons = {
        info: `<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="${config.color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>`,
        warning: `<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="${config.color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>`,
        error: `<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="${config.color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg>`,
        success: `<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="${config.color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>`
      };

      modal.innerHTML = `
        <div style="
          background: ${isDarkMode ? '#1e1e1e' : 'white'};
          border-radius: 14px;
          padding: 28px;
          min-width: 380px;
          max-width: 450px;
          box-shadow: 0 25px 50px -12px rgba(124, 58, 237, 0.25);
          text-align: center;
        ">
          <div style="
            width: 64px;
            height: 64px;
            margin: 0 auto 16px;
            background: ${config.bg};
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
          ">
            ${icons[config.icon]}
          </div>
          <h3 style="margin: 0 0 10px 0; color: ${isDarkMode ? '#e5e5e5' : '#1f2937'}; font-size: 20px; font-weight: 600;">${title}</h3>
          <p style="margin: 0 0 24px 0; color: ${isDarkMode ? '#a3a3a3' : '#6b7280'}; line-height: 1.6; font-size: 14px;">${message}</p>
          <button id="ok-btn" style="
            padding: 10px 32px;
            border: none;
            border-radius: 14px;
            background: linear-gradient(135deg, ${config.color}, ${config.color}dd);
            color: white;
            cursor: pointer;
            font-weight: 500;
            font-size: 14px;
            box-shadow: 0 4px 12px ${config.color}40;
            transition: all 0.2s;
          ">确定</button>
        </div>
      `;

      document.body.appendChild(modal);

      // 触发布局重绘后添加淡入效果
      requestAnimationFrame(() => {
        modal.style.opacity = '1';
      });

      // 绑定事件
      const okBtn = modal.querySelector('#ok-btn');

      // 添加按钮悬停效果
      okBtn.addEventListener('mouseenter', () => {
        okBtn.style.transform = 'translateY(-2px)';
        okBtn.style.boxShadow = `0 6px 16px ${config.color}50`;
      });
      okBtn.addEventListener('mouseleave', () => {
        okBtn.style.transform = 'translateY(0)';
        okBtn.style.boxShadow = `0 4px 12px ${config.color}40`;
      });

      const closeModal = () => {
        modal.style.opacity = '0';
        setTimeout(() => {
          if (modal.parentNode) {
            modal.parentNode.removeChild(modal);
          }
          resolve();
        }, 300);
      };

      okBtn.addEventListener('click', closeModal);

      // 点击背景关闭
      modal.addEventListener('click', (e) => {
        if (e.target === modal) {
          closeModal();
        }
      });

      // 按 ESC 关闭
      const escHandler = (e) => {
        if (e.key === 'Escape') {
          closeModal();
          document.removeEventListener('keydown', escHandler);
        }
      };
      document.addEventListener('keydown', escHandler);
    });
  }

  /**
   * 显示输入对话框
   * @param {Object} options - 对话框配置选项
   * @param {string} options.title - 对话框标题
   * @param {string} options.message - 对话框消息
   * @param {Array<Object>} options.inputs - 输入字段配置 [{id, label, type, placeholder, required, value}]
   * @returns {Promise<Object|null>} 用户输入的数据对象，取消时返回 null
   */
  static promptDialog(options = {}) {
    return new Promise((resolve) => {
      // 检查是否为深色模式
      const isDarkMode = document.documentElement.getAttribute('data-theme') === 'dark';

      const {
        title = '输入',
        message = '',
        inputs = [{ id: 'value', label: '', type: 'text', placeholder: '', required: true, value: '' }]
      } = options;

      // 创建模态框
      const modal = document.createElement('div');
      modal.className = 'prompt-modal';
      modal.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 10001;
        opacity: 0;
        transition: opacity 0.3s ease;
      `;

      // 生成输入字段 HTML
      const inputsHtml = inputs.map(input => `
        <div style="margin-bottom: 16px; text-align: left;">
          ${input.label ? `<label style="display: block; margin-bottom: 6px; font-size: 14px; font-weight: 500; color: ${isDarkMode ? '#e5e5e5' : '#374151'};">${input.label}</label>` : ''}
          ${input.type === 'textarea' ? `
            <textarea id="input-${input.id}"
              placeholder="${input.placeholder || ''}"
              ${input.required ? 'required' : ''}
              style="width: 100%; padding: 12px 14px; border: 2px solid ${isDarkMode ? '#404040' : '#e5e7eb'}; border-radius: 14px; font-size: 14px; resize: vertical; min-height: 80px; font-family: inherit; background: ${isDarkMode ? '#2d2d2d' : 'white'}; color: ${isDarkMode ? '#e5e5e5' : '#1f2937'}; transition: border-color 0.2s;"
              onfocus="this.style.borderColor='#7c3aed'"
              onblur="this.style.borderColor='${isDarkMode ? '#404040' : '#e5e7eb'}'"
            >${input.value || ''}</textarea>
          ` : `
            <input type="${input.type || 'text'}" id="input-${input.id}"
              placeholder="${input.placeholder || ''}"
              value="${input.value || ''}"
              ${input.required ? 'required' : ''}
              style="width: 100%; padding: 12px 14px; border: 2px solid ${isDarkMode ? '#404040' : '#e5e7eb'}; border-radius: 14px; font-size: 14px; background: ${isDarkMode ? '#2d2d2d' : 'white'}; color: ${isDarkMode ? '#e5e5e5' : '#1f2937'}; transition: border-color 0.2s;"
              onfocus="this.style.borderColor='#7c3aed'"
              onblur="this.style.borderColor='${isDarkMode ? '#404040' : '#e5e7eb'}'"
            >
          `}
        </div>
      `).join('');

      modal.innerHTML = `
        <div style="
          background: ${isDarkMode ? '#1e1e1e' : 'white'};
          border-radius: 14px;
          padding: 28px;
          min-width: 420px;
          max-width: 550px;
          box-shadow: 0 25px 50px -12px rgba(124, 58, 237, 0.25);
        ">
          <h3 style="margin: 0 0 8px 0; color: ${isDarkMode ? '#e5e5e5' : '#1f2937'}; font-size: 20px; font-weight: 600;">${title}</h3>
          ${message ? `<p style="margin: 0 0 20px 0; color: ${isDarkMode ? '#a3a3a3' : '#6b7280'}; font-size: 14px; line-height: 1.5;">${message}</p>` : ''}
          <form id="prompt-form">
            ${inputsHtml}
          </form>
          <div style="display: flex; justify-content: flex-end; gap: 12px; margin-top: 20px;">
            <button id="cancel-btn" style="
              padding: 10px 28px;
              border: 1px solid ${isDarkMode ? '#404040' : '#e5e7eb'};
              border-radius: 14px;
              background: ${isDarkMode ? '#2d2d2d' : 'white'};
              color: ${isDarkMode ? '#e5e5e5' : '#6b7280'};
              cursor: pointer;
              font-weight: 500;
              font-size: 14px;
              transition: all 0.2s;
            ">取消</button>
            <button id="ok-btn" style="
              padding: 10px 28px;
              border: none;
              border-radius: 14px;
              background: linear-gradient(135deg, #7c3aed, #8b5cf6);
              color: white;
              cursor: pointer;
              font-weight: 500;
              font-size: 14px;
              box-shadow: 0 4px 12px rgba(124, 58, 237, 0.3);
              transition: all 0.2s;
            ">确定</button>
          </div>
        </div>
      `;

      document.body.appendChild(modal);

      // 触发布局重绘后添加淡入效果
      requestAnimationFrame(() => {
        modal.style.opacity = '1';
      });

      // 绑定事件
      const okBtn = modal.querySelector('#ok-btn');
      const cancelBtn = modal.querySelector('#cancel-btn');
      const form = modal.querySelector('#prompt-form');

      // 添加按钮悬停效果
      okBtn.addEventListener('mouseenter', () => {
        okBtn.style.transform = 'translateY(-2px)';
        okBtn.style.boxShadow = '0 6px 16px rgba(124, 58, 237, 0.4)';
      });
      okBtn.addEventListener('mouseleave', () => {
        okBtn.style.transform = 'translateY(0)';
        okBtn.style.boxShadow = '0 4px 12px rgba(124, 58, 237, 0.3)';
      });

      cancelBtn.addEventListener('mouseenter', () => {
        cancelBtn.style.background = '#f9fafb';
        cancelBtn.style.borderColor = '#d1d5db';
      });
      cancelBtn.addEventListener('mouseleave', () => {
        cancelBtn.style.background = 'white';
        cancelBtn.style.borderColor = '#e5e7eb';
      });

      const closeModal = (result) => {
        modal.style.opacity = '0';
        setTimeout(() => {
          if (modal.parentNode) {
            modal.parentNode.removeChild(modal);
          }
          resolve(result);
        }, 300);
      };

      okBtn.addEventListener('click', (e) => {
        e.preventDefault();

        // 检查表单验证
        const formData = {};
        let isValid = true;

        inputs.forEach(input => {
          const inputEl = modal.querySelector(`#input-${input.id}`);
          const value = inputEl.value.trim();

          if (input.required && !value) {
            isValid = false;
            inputEl.style.borderColor = '#ef4444';
          } else {
            inputEl.style.borderColor = '#e5e7eb';
            formData[input.id] = value;
          }
        });

        if (!isValid) {
          UITools.showNotification('error', '验证失败', '请填写所有必填字段');
          return;
        }

        closeModal(formData);
      });

      cancelBtn.addEventListener('click', () => closeModal(null));

      // 表单提交
      form.addEventListener('submit', (e) => {
        e.preventDefault();
        okBtn.click();
      });

      // 点击背景关闭
      modal.addEventListener('click', (e) => {
        if (e.target === modal) {
          closeModal(null);
        }
      });

      // 按 ESC 关闭
      const escHandler = (e) => {
        if (e.key === 'Escape') {
          closeModal(null);
          document.removeEventListener('keydown', escHandler);
        }
      };
      document.addEventListener('keydown', escHandler);

      // 自动聚焦第一个输入框
      const firstInput = modal.querySelector('input, textarea');
      if (firstInput) {
        setTimeout(() => firstInput.focus(), 100);
      }
    });
  }

  /**
   * 格式化文件大小
   * @param {number} bytes - 文件大小（字节）
   * @returns {string} 格式化后的文件大小
   */
  static formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';

    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));

    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  /**
   * 验证邮箱格式
   * @param {string} email - 邮箱地址
   * @returns {boolean} 邮箱格式是否正确
   */
  static isValidEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  }

  /**
   * 验证密码强度
   * @param {string} password - 密码
   * @returns {boolean} 密码强度是否足够
   */
  static isStrongPassword(password) {
    // 至少8个字符，包含大小写字母、数字和特殊字符
    const strongPasswordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
    return strongPasswordRegex.test(password);
  }

  /**
   * 防抖函数
   * @param {Function} func - 要防抖的函数
   * @param {number} wait - 延迟时间（毫秒）
   * @returns {Function} 防抖后的函数
   */
  static debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
      const later = () => {
        clearTimeout(timeout);
        func(...args);
      };
      clearTimeout(timeout);
      timeout = setTimeout(later, wait);
    };
  }

  /**
   * 节流函数
   * @param {Function} func - 要节流的函数
   * @param {number} limit - 限制时间（毫秒）
   * @returns {Function} 节流后的函数
   */
  static throttle(func, limit) {
    let inThrottle;
    return function() {
      const args = arguments;
      const context = this;
      if (!inThrottle) {
        func.apply(context, args);
        inThrottle = true;
        setTimeout(() => inThrottle = false, limit);
      }
    };
  }
}

// 表单验证工具类
class FormValidator {
  /**
   * 验证表单字段
   * @param {HTMLInputElement|HTMLTextAreaElement|HTMLSelectElement} field - 表单字段
   * @param {Object} rules - 验证规则
   * @returns {Array<string>} 错误信息列表
   */
  static validateField(field, rules = {}) {
    const errors = [];
    const value = field.value.trim();

    // 必填验证
    if (rules.required && !value) {
      errors.push(rules.requiredMessage || '此字段为必填项');
    }

    // 邮箱验证
    if (rules.email && value && !UITools.isValidEmail(value)) {
      errors.push(rules.emailMessage || '请输入有效的邮箱地址');
    }

    // 最小长度验证
    if (rules.minLength && value.length < rules.minLength) {
      errors.push(rules.minLengthMessage || `最少需要 ${rules.minLength} 个字符`);
    }

    // 最大长度验证
    if (rules.maxLength && value.length > rules.maxLength) {
      errors.push(rules.maxLengthMessage || `最多允许 ${rules.maxLength} 个字符`);
    }

    // 密码强度验证
    if (rules.passwordStrength && value && !UITools.isStrongPassword(value)) {
      errors.push(rules.passwordStrengthMessage || '密码强度不足，请至少包含8个字符，包括大小写字母、数字和特殊字符');
    }

    return errors;
  }

  /**
   * 显示字段错误信息
   * @param {HTMLInputElement|HTMLTextAreaElement|HTMLSelectElement} field - 表单字段
   * @param {Array<string>} errors - 错误信息列表
   */
  static showFieldErrors(field, errors) {
    // 移除之前的错误信息
    this.clearFieldErrors(field);

    if (errors.length > 0) {
      field.style.borderColor = 'var(--danger)';

      // 创建错误信息元素
      const errorDiv = document.createElement('div');
      errorDiv.className = 'field-error';
      errorDiv.style.cssText = `
        color: var(--danger);
        font-size: 12px;
        margin-top: 4px;
        margin-left: 2px;
      `;
      errorDiv.textContent = errors[0]; // 只显示第一个错误

      // 找到字段的父容器并添加错误信息
      const parent = field.parentElement;
      parent.appendChild(errorDiv);
    }
  }

  /**
   * 清除字段错误信息
   * @param {HTMLInputElement|HTMLTextAreaElement|HTMLSelectElement} field - 表单字段
   */
  static clearFieldErrors(field) {
    field.style.borderColor = 'var(--border-light, #e5e7eb)';

    // 查找并移除错误信息元素
    const errorElement = field.parentElement.querySelector('.field-error');
    if (errorElement) {
      errorElement.remove();
    }
  }
}

// 导出工具类
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { UITools, FormValidator };
} else {
  window.UITools = UITools;
  window.FormValidator = FormValidator;
}