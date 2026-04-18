/**
 * 文档翻译页面功能实现
 * 实现文档翻译核心功能与 API 交互逻辑
 */

// 页面配置（使用 config.js 中的 PAGE 配置）
const PAGE_CONFIG = {
  LOGIN_PAGE: (typeof PAGE !== 'undefined' && PAGE.LOGIN) || 'index.html?modal=login'
};

// HTML 转义工具函数（防 XSS）
function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

// 存储当前上传的文件
let currentFile = null;
let currentTaskId = null;

document.addEventListener('DOMContentLoaded', function() {
  // 获取导航栏登录按钮并绑定事件
  const loginBtn = document.querySelector('.nav-actions .btn-secondary') ||
                   document.querySelector('.user-menu .btn-outline');
  if (loginBtn) {
    updateLoginButton(loginBtn);
  }

  // 文件上传功能
  const fileInput = document.getElementById('fileInput');
  const uploadArea = document.getElementById('uploadArea') || document.querySelector('.upload-area');
  const selectFileBtn = document.getElementById('selectFileBtn');

  if (fileInput && uploadArea) {
    // 上传区域点击事件
    if (selectFileBtn) {
      selectFileBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        fileInput.click();
      });
    }

    uploadArea.addEventListener('click', () => {
      fileInput.click();
    });

    // 文件选择事件
    fileInput.addEventListener('change', function(e) {
      if (e.target.files.length > 0) {
        const file = e.target.files[0];
        validateAndUploadFile(file);
      }
    });

    // 拖拽上传事件
    ['dragenter', 'dragover'].forEach(eventName => {
      uploadArea.addEventListener(eventName, (e) => {
        e.preventDefault();
        uploadArea.classList.add('drag-over');
      }, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
      uploadArea.addEventListener(eventName, (e) => {
        e.preventDefault();
        uploadArea.classList.remove('drag-over');
      }, false);
    });

    // 处理文件拖放
    uploadArea.addEventListener('drop', (e) => {
      e.preventDefault();
      if (e.dataTransfer.files.length > 0) {
        const file = e.dataTransfer.files[0];
        validateAndUploadFile(file);
      }
    });
  }

  // 验证并上传文件
  async function validateAndUploadFile(file) {
    // 检查文件类型
    const allowedTypes = ['.txt', '.epub', '.docx', '.pdf'];
    const fileName = file.name.toLowerCase();
    const fileTypeValid = allowedTypes.some(type => fileName.endsWith(type));

    if (!fileTypeValid) {
      showNotification('仅支持 TXT, EPUB, DOCX, PDF 格式', 'warning');
      return;
    }

    // 检查文件大小 (50MB 限制)
    const maxSize = 50 * 1024 * 1024;
    if (file.size > maxSize) {
      showNotification('文件大小不能超过 50MB', 'warning');
      return;
    }

    // 检查用户是否登录
    if (!AuthManager.isAuthenticated()) {
      showNotification('需要登录才能上传文件', 'warning');
      setTimeout(() => {
        window.location.href = PAGE_CONFIG.LOGIN_PAGE;
      }, 1500);
      return;
    }

    // 上传文件到后端
    await uploadFileToServer(file);
  }

  // 上传文件到服务器
  async function uploadFileToServer(file) {
    showNotification(`正在上传 ${file.name}...`, 'info');

    // 使用 apiClient 统一处理
    const result = await apiClient.uploadDocument(file, {
      targetLang: document.getElementById('targetLang')?.value || 'zh'
    });

    if (result.success) {
      currentTaskId = result.data?.taskId || result.data?.id;
      currentFile = file;

      showNotification(`${file.name} 已上传，开始翻译...`, 'success');

      // 添加文件到列表
      addFileToList(file);

      // 开始翻译
      if (currentTaskId) {
        startTranslation(file);
      }
    } else {
      const errorMessage = result.data?.message || result.error || '上传失败';
      showNotification(errorMessage, 'error');
    }
  }

  // 开始翻译
  async function startTranslation(file) {
    showNotification('正在翻译文档，请稍候...', 'info');

    // 更新文件状态
    updateFileStatus(file, 'translating');

    // 轮询任务状态
    pollTaskStatus(currentTaskId, file);
  }

  // 轮询任务状态
  async function pollTaskStatus(taskId, file) {
    const maxRetries = 30;
    let retryCount = 0;

    const checkStatus = async () => {
      try {
        const result = await apiClient.getTaskStatus(taskId);

        if (result.success) {
          const status = result.data?.status;

          if (status === 'completed') {
            updateFileStatus(file, 'completed');
            showNotification('文档翻译已完成', 'success');

            // 获取翻译结果
            await getTranslationResult(taskId);
          } else if (status === 'failed') {
            updateFileStatus(file, 'failed');
            showNotification(result.data?.errorMessage || '翻译过程中发生错误', 'error');
          } else {
            // 继续轮询
            setTimeout(checkStatus, 2000);
          }
        } else {
          retryCount++;
          if (retryCount >= maxRetries) {
            updateFileStatus(file, 'failed');
            showNotification('任务状态查询超时', 'error');
            return;
          }
          console.warn(`Status check failed (retry ${retryCount}/${maxRetries}):`, result);
          setTimeout(checkStatus, 2000);
        }
      } catch (error) {
        console.error('轮询任务状态异常:', error);
        retryCount++;
        if (retryCount >= maxRetries) {
          updateFileStatus(file, 'failed');
          showNotification('网络连接异常，请稍后重试', 'error');
          return;
        }
        setTimeout(checkStatus, 2000);
      }
    };

    checkStatus();
  }

  // 获取翻译结果
  async function getTranslationResult(taskId) {
    const result = await apiClient.getTaskResult(taskId);

    if (result.success) {
      const content = result.data?.translatedText;

      // 显示翻译结果
      showResult(currentFile, null, content);
    }
  }

  // 添加文件到列表
  function addFileToList(file) {
    const uploadedFiles = document.getElementById('uploadedFiles');
    if (!uploadedFiles) return;

    const fileSize = formatFileSize(file.size);
    const fileItem = document.createElement('div');
    fileItem.className = 'file-item';
    fileItem.innerHTML = `
      <div class="file-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
        </svg>
      </div>
      <div class="file-info">
        <div class="file-name">${escapeHtml(file.name)}</div>
        <div class="file-meta">
          <span class="file-size">${fileSize}</span>
          <span class="file-status status-ready">待翻译</span>
        </div>
      </div>
      <div class="file-actions">
        <button class="file-btn btn-translate" title="翻译">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M5 8l6 6"/>
            <path d="M4 14l6-6 2-3"/>
            <path d="M2 5h12"/>
            <path d="M7 2h1"/>
          </svg>
        </button>
        <button class="file-btn btn-delete" title="删除">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/>
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
          </svg>
        </button>
      </div>
    `;

    // 绑定按钮事件
    const translateBtn = fileItem.querySelector('.btn-translate');
    const deleteBtn = fileItem.querySelector('.btn-delete');

    translateBtn.addEventListener('click', () => {
      if (currentTaskId) {
        startTranslation(file);
      } else {
        showNotification('请先上传文件', 'warning');
      }
    });

    deleteBtn.addEventListener('click', () => {
      fileItem.remove();
      currentFile = null;
      currentTaskId = null;
      showNotification('文件已删除', 'info');
    });

    // 添加到列表开头
    uploadedFiles.insertBefore(fileItem, uploadedFiles.firstChild);
  }

  // 更新文件状态
  function updateFileStatus(file, status) {
    const uploadedFiles = document.getElementById('uploadedFiles');
    if (!uploadedFiles) return;

    const fileItems = uploadedFiles.querySelectorAll('.file-item');
    for (const item of fileItems) {
      const fileName = item.querySelector('.file-name')?.textContent;
      if (fileName === file.name) {
        const statusSpan = item.querySelector('.file-status');
        if (statusSpan) {
          statusSpan.className = `file-status status-${status}`;
          statusSpan.textContent = status === 'translating' ? '翻译中' :
                                   status === 'completed' ? '已完成' :
                                   status === 'failed' ? '失败' : '待翻译';
        }
        break;
      }
    }
  }

  // 格式化文件大小
  function formatFileSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  // 显示翻译结果
  function showResult(file, sourceContent, targetContent) {
    const resultContainer = document.getElementById('resultContainer');
    const resultFileName = document.getElementById('resultFileName');
    const resultSource = document.getElementById('resultSource');
    const resultTarget = document.getElementById('resultTarget');

    if (resultFileName) resultFileName.textContent = file.name;
    if (resultSource) resultSource.textContent = sourceContent || '原文内容加载中...';
    if (resultTarget) resultTarget.textContent = targetContent || '翻译结果加载中...';

    if (resultContainer) {
      resultContainer.style.display = 'flex';
      const uploadArea = document.getElementById('uploadArea') || document.querySelector('.upload-area');
      if (uploadArea) uploadArea.style.display = 'none';
    }

    // 绑定复制按钮
    const copyBtn = document.getElementById('copyResultBtn');
    if (copyBtn) {
      copyBtn.onclick = () => {
        if (resultTarget?.textContent) {
          navigator.clipboard.writeText(resultTarget.textContent);
          showNotification('已复制到剪贴板', 'success');
        }
      };
    }

    // 绑定下载按钮
    const downloadBtn = document.getElementById('downloadResultBtn');
    if (downloadBtn) {
      downloadBtn.onclick = async () => {
        if (currentTaskId) {
          await downloadTranslation(currentTaskId);
        }
      };
    }
  }

  // 下载翻译结果
  async function downloadTranslation(taskId) {
    const result = await apiClient.downloadTaskResult(taskId);

    if (result.success) {
      const blob = result.blob;
      const downloadUrl = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = downloadUrl;
      a.download = `translation_${currentFile?.name || 'result.txt'}`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(downloadUrl);
      showNotification('下载成功', 'success');
    } else {
      showNotification(result.error || '网络错误或服务器不可用', 'error');
    }
  }

  // 更新登录按钮状态
  function updateLoginButton(btn) {
    const isAuthenticated = AuthManager.isAuthenticated();
    btn.textContent = isAuthenticated ? '个人中心' : '登录';

    // 克隆按钮以移除旧监听器，防止累积
    const newBtn = btn.cloneNode(true);
    btn.parentNode.replaceChild(newBtn, btn);

    newBtn.addEventListener('click', function() {
      if (isAuthenticated) {
        showNotification('您已登录，可访问个人中心', 'success');
      } else {
        window.location.href = PAGE_CONFIG.LOGIN_PAGE;
      }
    });
  }

  // 通知功能 (message, type)
  function showNotification(message, type) {
    type = type || 'info';
    const titleMap = { success: '成功', error: '错误', warning: '警告', info: '提示' };
    const title = titleMap[type] || '提示';

    if (typeof UITools !== 'undefined') {
      UITools.showNotification(message, type);
      return;
    }

    let notification = document.getElementById('notification');
    if (!notification) {
      notification = document.createElement('div');
      notification.id = 'notification';
      notification.className = 'notification';
      notification.innerHTML = `
        <div class="notif-content">
          <strong id="notif-title"></strong>
          <p id="notif-msg"></p>
        </div>
      `;

      const style = document.createElement('style');
      style.textContent = `
        .notification {
          position: fixed;
          top: 80px;
          right: 24px;
          background: white;
          padding: 16px 20px;
          border-radius: 12px;
          box-shadow: 0 4px 20px rgba(0,0,0,0.15);
          border-left: 4px solid #3b82f6;
          display: flex;
          align-items: center;
          z-index: 10000;
          min-width: 300px;
          transform: translateX(150%);
          transition: transform 0.3s ease-out;
        }
        .notification.show {
          transform: translateX(0);
        }
        .notification.success { border-left-color: #10b981; }
        .notification.warning { border-left-color: #f59e0b; }
        .notification.error { border-left-color: #ef4444; }
        .notification.info { border-left-color: #3b82f6; }
        .notif-content strong {
          display: block;
          font-size: 14px;
          margin-bottom: 2px;
          color: #1f2937;
        }
        .notif-content p {
          margin: 0;
          font-size: 13px;
          color: #64748b;
        }
      `;
      document.head.appendChild(style);
      document.body.appendChild(notification);
    }

    const titleEl = notification.querySelector('#notif-title');
    const msgEl = notification.querySelector('#notif-msg');

    if (titleEl) titleEl.textContent = escapeHtml(title);
    if (msgEl) msgEl.textContent = escapeHtml(message);

    notification.className = `notification show ${type}`;

    setTimeout(() => {
      notification.classList.remove('show');
    }, 3000);
  }
});
