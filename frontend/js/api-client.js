/**
 * NovaTrans API 客户端
 * 基于 api-docs.md 文档实现的前端 API 接口
 */

class ApiClient {
  constructor() {
    // 从 config.js 获取 API 基础 URL
    this.baseURL = (typeof CONFIG !== 'undefined') ? CONFIG.API_BASE_URL : 'http://127.0.0.1:7341';
    this.userApiPath = (typeof CONFIG !== 'undefined') ? CONFIG.USER_API_PATH : '/user';
    this.translateApiPath = (typeof CONFIG !== 'undefined') ? CONFIG.TRANSLATE_API_PATH : '/v1/translate';
    this.token = localStorage.getItem('authToken') || null;
  }

  /**
   * 通用请求方法
   */
  async request(endpoint, options = {}, useUserApi = false, useTranslateApi = false) {
    let basePath = '';
    if (useUserApi) {
      basePath = this.userApiPath;
    } else if (useTranslateApi) {
      basePath = this.translateApiPath;
    }

    const normalizedEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
    const url = `${this.baseURL}${basePath}${normalizedEndpoint}`;
    const config = {
      headers: {
        'Content-Type': 'application/json',
        ...options.headers
      },
      ...options
    };

    // 每次请求动态读取 token
    const currentToken = localStorage.getItem('authToken') || this.token;
    if (currentToken) {
      config.headers['Authorization'] = `Bearer ${currentToken}`;
    }

    try {
      const response = await fetch(url, config);

      // 克隆响应以便可以多次读取（解决 body stream 已读取的问题）
      const responseClone = response.clone();

      // 检查响应状态
      if (!response.ok) {
        // 如果响应不成功，获取错误信息
        let errorData;
        try {
          // 尝试解析为 JSON
          errorData = await response.json();
          throw new Error(errorData.message || `HTTP Error: ${response.status}`);
        } catch (jsonError) {
          // 如果不能解析为 JSON，获取文本
          try {
            const errorText = await response.text();
            throw new Error(errorText || `HTTP Error: ${response.status}`);
          } catch (textError) {
            // 如果两者都失败，使用状态码作为错误信息
            throw new Error(`HTTP Error: ${response.status}`);
          }
        }
      }

      // 响应成功，尝试解析 JSON 数据
      let data;
      try {
        data = await response.json();
        return { success: true, data };
      } catch (e) {
        // 如果不能解析为 JSON，获取文本
        try {
          data = await response.text();
          return { success: true, data };
        } catch (textError) {
          // 如果无法解析响应，则返回错误
          throw new Error(`Failed to parse response: ${textError.message}`);
        }
      }
    } catch (error) {
      console.error('API Request Error:', error);
      return { success: false, error: error.message };
    }
  }

  /**
   * 通用请求方法（带 Blob 响应）
   */
  async requestBlob(endpoint, options = {}, useUserApi = false, useTranslateApi = false) {
    let basePath = '';
    if (useUserApi) {
      basePath = this.userApiPath;
    } else if (useTranslateApi) {
      basePath = this.translateApiPath;
    }

    const normalizedEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
    const url = `${this.baseURL}${basePath}${normalizedEndpoint}`;
    const config = {
      headers: {
        ...options.headers
      },
      ...options
    };

    // 每次请求动态读取 token
    const currentToken = localStorage.getItem('authToken') || this.token;
    if (currentToken) {
      config.headers['Authorization'] = `Bearer ${currentToken}`;
    }

    try {
      const response = await fetch(url, config);

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || `HTTP Error: ${response.status}`);
      }

      const blob = await response.blob();
      return { success: true, blob, response };
    } catch (error) {
      console.error('API Request Error:', error);
      return { success: false, error: error.message };
    }
  }

  /**
   * 通用请求方法（FormData，用于文件上传）
   */
  async requestFormData(endpoint, formData, options = {}, useUserApi = false, useTranslateApi = false) {
    let basePath = '';
    if (useUserApi) {
      basePath = this.userApiPath;
    } else if (useTranslateApi) {
      basePath = this.translateApiPath;
    }

    const normalizedEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
    const url = `${this.baseURL}${basePath}${normalizedEndpoint}`;
    const config = {
      headers: {
        ...options.headers
      },
      body: formData,
      ...options
    };

    // 每次请求动态读取 token
    const currentToken = localStorage.getItem('authToken') || this.token;
    if (currentToken) {
      config.headers['Authorization'] = `Bearer ${currentToken}`;
    }

    // 注意：不要设置 Content-Type，让浏览器自动设置 multipart/form-data

    try {
      const response = await fetch(url, config);

      // 克隆响应以便可以多次读取
      const responseClone = response.clone();

      if (!response.ok) {
        let errorData;
        try {
          errorData = await response.json();
          throw new Error(errorData.message || `HTTP Error: ${response.status}`);
        } catch (jsonError) {
          try {
            const errorText = await response.text();
            throw new Error(errorText || `HTTP Error: ${response.status}`);
          } catch (textError) {
            throw new Error(`HTTP Error: ${response.status}`);
          }
        }
      }

      let data;
      try {
        data = await response.json();
        return { success: true, data };
      } catch (e) {
        try {
          data = await response.text();
          return { success: true, data };
        } catch (textError) {
          throw new Error(`Failed to parse response: ${textError.message}`);
        }
      }
    } catch (error) {
      console.error('API Request Error:', error);
      return { success: false, error: error.message };
    }
  }

  /* ==================== 翻译接口 ==================== */

  /**
   * 选中翻译
   * POST /v1/translate/selection
   */
  async translateSelection(params) {
    const { text, sourceLang = 'auto', targetLang = 'zh', engine, context } = params;

    return this.request('/selection', {
      method: 'POST',
      body: JSON.stringify({
        text,
        sourceLang,
        targetLang,
        engine,
        context
      })
    }, false, true);
  }

  /**
   * 阅读器翻译
   * POST /v1/translate/reader
   */
  async translateReader(params) {
    const { content, sourceLang = 'auto', targetLang, engine } = params;

    return this.request('/reader', {
      method: 'POST',
      body: JSON.stringify({
        content,
        sourceLang,
        targetLang,
        engine
      })
    }, false, true);
  }

  /**
   * 网页翻译（流式 SSE）
   * POST /v1/translate/webpage
   * 返回 EventSource 实例
   */
  translateWebpageStream(params, onProgress, onDone, onError) {
    const { targetLang, sourceLang = 'auto', engine, textRegistry } = params;

    const url = `${this.baseURL}${this.translateApiPath}/webpage`;

    // 使用 EventSource 接收 SSE 流
    const xhr = new XMLHttpRequest();
    xhr.open('POST', url, true);
    xhr.setRequestHeader('Content-Type', 'application/json');

    if (this.token) {
      xhr.setRequestHeader('Authorization', `Bearer ${this.token}`);
    }

    xhr.send(JSON.stringify({
      targetLang,
      sourceLang,
      engine,
      textRegistry
    }));

    xhr.onprogress = () => {
      const text = xhr.responseText;
      // 解析 SSE 格式的数据
      const lines = text.split('\n');
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = line.slice(6);
          if (data === '[DONE]') {
            onDone && onDone();
            return;
          }
          if (data.startsWith('ERROR: ')) {
            onError && onError(new Error(data.slice(6)));
            return;
          }
          try {
            const parsed = JSON.parse(data);
            onProgress && onProgress(parsed);
          } catch (e) {
            // 忽略解析错误
          }
        }
      }
    };

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        // 请求完成，onDone 已在 onprogress 中调用
      } else {
        onError && onError(new Error(`HTTP Error: ${xhr.status}`));
      }
    };

    xhr.onerror = () => {
      onError && onError(new Error('Network error'));
    };

    return xhr;
  }

  /**
   * 查询翻译任务状态
   * GET /v1/translate/task/{taskId}
   */
  async getTaskStatus(taskId) {
    return this.request(`/task/${taskId}`, {
      method: 'GET'
    }, false, true);
  }

  /**
   * 取消翻译任务（需要认证）
   * DELETE /v1/translate/task/{taskId}
   */
  async cancelTask(taskId) {
    return this.request(`/task/${taskId}`, {
      method: 'DELETE'
    }, false, true);
  }

  /**
   * 获取翻译结果
   * GET /v1/translate/task/{taskId}/result
   */
  async getTaskResult(taskId) {
    return this.request(`/task/${taskId}/result`, {
      method: 'GET'
    }, false, true);
  }

  /**
   * 下载翻译结果（需要认证）
   * GET /v1/translate/task/{taskId}/download
   */
  async downloadTaskResult(taskId) {
    return this.requestBlob(`/task/${taskId}/download`, {
      method: 'GET'
    }, false, true);
  }

  /**
   * 高级翻译 - 选中翻译（需要认证）
   * POST /v1/translate/premium-selection
   */
  async premiumTranslateSelection(params) {
    const { text, sourceLang = 'auto', targetLang = 'zh', engine, context } = params;

    return this.request('/premium-selection', {
      method: 'POST',
      body: JSON.stringify({
        text,
        sourceLang,
        targetLang,
        engine,
        context
      })
    }, false, true);
  }

  /**
   * 高级翻译 - 阅读器翻译（需要认证）
   * POST /v1/translate/premium-reader
   */
  async premiumTranslateReader(params) {
    const { content, sourceLang = 'auto', targetLang, engine } = params;

    return this.request('/premium-reader', {
      method: 'POST',
      body: JSON.stringify({
        content,
        sourceLang,
        targetLang,
        engine
      })
    }, false, true);
  }

  /* ==================== 用户接口 ==================== */

  /**
   * 发送验证码
   * POST /user/send-code
   */
  async sendVerificationCode(email) {
    return this.request('/send-code', {
      method: 'POST',
      body: JSON.stringify({ email })
    }, true, false);
  }

  /**
   * 用户登录
   * POST /user/login
   * 注意：此方法只负责调用 API，认证成功后由调用者使用 AuthManager.login() 存储认证数据
   */
  async login(credentials) {
    const { email, password, from = null } = credentials;

    return this.request('/login', {
      method: 'POST',
      body: JSON.stringify({
        email,
        password,
        from
      })
    }, true, false);
  }

  /**
   * 用户注册
   * POST /user/register
   * 注意：此方法只负责调用 API，认证成功后由调用者使用 AuthManager.login() 存储认证数据
   */
  async register(userData) {
    const { email, password, code, username, avatar } = userData;

    return this.request('/register', {
      method: 'POST',
      body: JSON.stringify({
        email,
        password,
        code,
        username,
        avatar
      })
    }, true, false);
  }

  /**
   * 获取当前用户信息
   * GET /user/profile
   */
  async getProfile() {
    return this.request('/profile', {
      method: 'GET'
    }, true, false);
  }

  /**
   * 更新用户信息
   * PUT /user/profile
   */
  async updateProfile(params) {
    const { username, avatar } = params;

    return this.request('/profile', {
      method: 'PUT',
      body: JSON.stringify({
        username,
        avatar
      })
    }, true, false);
  }

  /**
   * 修改密码
   * POST /user/change-password
   */
  async changePassword(params) {
    const { oldPassword, newPassword } = params;

    return this.request('/change-password', {
      method: 'POST',
      body: JSON.stringify({
        oldPassword,
        newPassword
      })
    }, true, false);
  }

  /**
   * 重置密码
   * POST /user/reset-password
   */
  async resetPassword(params) {
    const { email, code, newPassword } = params;

    return this.request('/reset-password', {
      method: 'POST',
      body: JSON.stringify({
        email,
        code,
        newPassword
      })
    }, true, false);
  }

  /**
   * 刷新令牌
   * POST /user/refresh-token
   */
  async refreshToken(refreshToken) {
    return this.request('/refresh-token', {
      method: 'POST',
      body: JSON.stringify({ refreshToken })
    }, true, false);
  }

  /**
   * 退出登录
   * POST /user/logout
   */
  async logout(refreshToken) {
    const result = await this.request('/logout', {
      method: 'POST',
      body: refreshToken ? JSON.stringify({ refreshToken }) : undefined
    }, true, false);

    // 清除本地存储
    this.logoutLocal();

    return result;
  }

  /**
   * 本地登出（清除存储）
   */
  logoutLocal() {
    localStorage.removeItem('authToken');
    localStorage.removeItem('userInfo');
    localStorage.removeItem('rememberMe');
    this.token = null;
  }

  /**
   * 获取用户统计
   * GET /user/statistics
   */
  async getStatistics() {
    return this.request('/statistics', {
      method: 'GET'
    }, true, false);
  }

  /**
   * 获取用户配额
   * GET /user/quota
   */
  async getQuota() {
    return this.request('/quota', {
      method: 'GET'
    }, true, false);
  }

  /**
   * 获取翻译历史
   * GET /user/translation-history
   */
  async getTranslationHistory(params = {}) {
    const { page = 1, pageSize = 20, type = 'all' } = params;
    const queryString = new URLSearchParams({ page, pageSize, type }).toString();
    return this.request(`/translation-history?${queryString}`, {
      method: 'GET'
    }, true, false);
  }

  /**
   * 注册设备 Token
   * POST /user/register-device
   */
  async registerDevice(deviceId) {
    return this.request('/register-device', {
      method: 'POST',
      body: JSON.stringify({ deviceId })
    }, true, false);
  }

  /**
   * 获取 Token（设备）
   * GET /user/get-token/{deviceId}
   */
  async getTokenByDevice(deviceId) {
    return this.request(`/get-token/${deviceId}`, {
      method: 'GET'
    }, true, false);
  }

  /* ==================== 文档管理接口 ==================== */

  /**
   * 获取文档列表
   * GET /user/documents
   */
  async getDocuments(params = {}) {
    const { page = 1, pageSize = 20, status = 'all' } = params;
    const queryString = new URLSearchParams({ page, pageSize, status }).toString();
    return this.request(`/documents?${queryString}`, {
      method: 'GET'
    }, true, false);
  }

  /**
   * 获取文档详情
   * GET /user/documents/{docId}
   */
  async getDocument(docId) {
    return this.request(`/documents/${docId}`, {
      method: 'GET'
    }, true, false);
  }

  /**
   * 删除文档
   * DELETE /user/documents/{docId}
   */
  async deleteDocument(docId) {
    return this.request(`/documents/${docId}`, {
      method: 'DELETE'
    }, true, false);
  }

  /**
   * 上传并翻译文档
   * POST /user/documents/upload
   */
  async uploadDocument(file, params = {}) {
    const { sourceLang = 'auto', targetLang, mode = 'novel' } = params;

    const formData = new FormData();
    formData.append('file', file);
    formData.append('sourceLang', sourceLang);
    formData.append('targetLang', targetLang);
    formData.append('mode', mode);

    return this.requestFormData('/documents/upload', formData, {
      method: 'POST'
    }, true, false);
  }

  /**
   * 下载文档
   * GET /user/documents/{docId}/download
   */
  async downloadDocument(docId) {
    return this.requestBlob(`/documents/${docId}/download`, {
      method: 'GET'
    }, true, false);
  }

  /* ==================== API Key 管理接口 ==================== */

  /**
   * 生成 API Key
   * POST /user/api-keys
   */
  async createApiKey(name = 'Default') {
    return this.request('/api-keys', {
      method: 'POST',
      body: JSON.stringify({ name })
    }, true, false);
  }

  /**
   * 获取 API Key 列表
   * GET /user/api-keys
   */
  async getApiKeys() {
    return this.request('/api-keys', {
      method: 'GET'
    }, true, false);
  }

  /**
   * 删除 API Key
   * DELETE /user/api-keys/{id}
   */
  async deleteApiKey(id) {
    return this.request(`/api-keys/${id}`, {
      method: 'DELETE'
    }, true, false);
  }

  /**
   * 重置 API Key
   * POST /user/api-keys/{id}/reset
   */
  async resetApiKey(id) {
    return this.request(`/api-keys/${id}/reset`, {
      method: 'POST'
    }, true, false);
  }

  /* ==================== 术语库接口 ==================== */

  /**
   * 获取术语列表（平铺术语项）
   * GET /user/glossaries
   */
  async getGlossaries() {
    return this.request('/glossaries', {
      method: 'GET'
    }, true, false);
  }

  /**
   * 获取术语库详情
   * GET /user/glossaries/{id}
   */
  async getGlossary(glossaryId) {
    return this.request(`/glossaries/${glossaryId}`, {
      method: 'GET'
    }, true, false);
  }

  /**
   * 获取术语列表（与 getGlossaries 相同，后端返回平铺术语）
   * GET /user/glossaries/{id}/terms
   */
  async getTerms(glossaryId) {
    return this.request(`/glossaries/${glossaryId}/terms`, {
      method: 'GET'
    }, true, false);
  }

  /**
   * 创建术语项
   * POST /user/glossaries
   */
  async createGlossary(data) {
    const { sourceWord, targetWord, description, remark } = data;
    return this.request('/glossaries', {
      method: 'POST',
      body: JSON.stringify({
        sourceWord,
        targetWord,
        remark: remark || description || ''
      })
    }, true, false);
  }

  /**
   * 更新术语项
   * PUT /user/glossaries/{id}
   */
  async updateGlossary(glossaryId, data) {
    const { sourceWord, targetWord, description, remark } = data;
    return this.request(`/glossaries/${glossaryId}`, {
      method: 'PUT',
      body: JSON.stringify({
        sourceWord,
        targetWord,
        remark: remark || description || ''
      })
    }, true, false);
  }

  /**
   * 删除术语库
   * DELETE /user/glossaries/{id}
   */
  async deleteGlossary(glossaryId) {
    return this.request(`/glossaries/${glossaryId}`, {
      method: 'DELETE'
    }, true, false);
  }

  /**
   * 添加术语
   * POST /user/glossaries/{id}/terms
   */
  async addTerm(glossaryId, data) {
    const { sourceText, targetText, description } = data;
    return this.request(`/glossaries/${glossaryId}/terms`, {
      method: 'POST',
      body: JSON.stringify({
        sourceText,
        targetText,
        description
      })
    }, true, false);
  }

  /**
   * 更新术语
   * PUT /user/glossaries/{id}/terms/{termId}
   */
  async updateTerm(glossaryId, termId, data) {
    const { sourceText, targetText, description } = data;
    return this.request(`/glossaries/${glossaryId}/terms/${termId}`, {
      method: 'PUT',
      body: JSON.stringify({
        sourceText,
        targetText,
        description
      })
    }, true, false);
  }

  /**
   * 删除术语
   * DELETE /user/glossaries/{id}/terms/{termId}
   */
  async deleteTerm(glossaryId, termId) {
    return this.request(`/glossaries/${glossaryId}/terms/${termId}`, {
      method: 'DELETE'
    }, true, false);
  }

  /* ==================== 用户偏好设置接口 ==================== */

  /**
   * 获取用户偏好设置
   * GET /user/preferences
   */
  async getPreferences() {
    return this.request('/preferences', {
      method: 'GET'
    }, true, false);
  }

  /**
   * 更新用户偏好设置
   * PUT /user/preferences
   */
  async updatePreferences(data) {
    const { defaultSourceLang, defaultTargetLang, translationEngine, notifications } = data;
    return this.request('/preferences', {
      method: 'PUT',
      body: JSON.stringify({
        defaultSourceLang,
        defaultTargetLang,
        translationEngine,
        notifications
      })
    }, true, false);
  }

  /* ==================== 协作翻译接口 ==================== */

  /**
   * 创建协作项目
   * POST /v1/collab/projects
   */
  async createCollabProject(data) {
    return this.request('/v1/collab/projects', {
      method: 'POST',
      body: JSON.stringify(data)
    });
  }

  /**
   * 获取我参与的项目列表
   * GET /v1/collab/projects
   */
  async listCollabProjects() {
    return this.request('/v1/collab/projects', {
      method: 'GET'
    });
  }

  /**
   * 获取项目详情
   * GET /v1/collab/projects/{projectId}
   */
  async getCollabProject(projectId) {
    return this.request(`/v1/collab/projects/${projectId}`, {
      method: 'GET'
    });
  }

  /**
   * 邀请成员
   * POST /v1/collab/projects/{projectId}/invite
   */
  async inviteMember(projectId, data) {
    return this.request(`/v1/collab/projects/${projectId}/invite`, {
      method: 'POST',
      body: JSON.stringify(data)
    });
  }

  /**
   * 通过邀请码加入
   * POST /v1/collab/join
   */
  async joinByInviteCode(code) {
    return this.request('/v1/collab/join', {
      method: 'POST',
      body: JSON.stringify({ inviteCode: code })
    });
  }

  /**
   * 获取项目成员列表
   * GET /v1/collab/projects/{projectId}/members
   */
  async listProjectMembers(projectId) {
    return this.request(`/v1/collab/projects/${projectId}/members`, {
      method: 'GET'
    });
  }

  /**
   * 分配章节
   * PUT /v1/collab/chapters/{chapterId}/assign
   */
  async assignChapter(chapterId, data) {
    return this.request(`/v1/collab/chapters/${chapterId}/assign`, {
      method: 'PUT',
      body: JSON.stringify(data)
    });
  }

  /**
   * 提交章节翻译
   * PUT /v1/collab/chapters/{chapterId}/submit
   */
  async submitChapter(chapterId, data) {
    return this.request(`/v1/collab/chapters/${chapterId}/submit`, {
      method: 'PUT',
      body: JSON.stringify(data)
    });
  }

  /**
   * 审核章节
   * PUT /v1/collab/chapters/{chapterId}/review
   */
  async reviewChapter(chapterId, data) {
    return this.request(`/v1/collab/chapters/${chapterId}/review`, {
      method: 'PUT',
      body: JSON.stringify(data)
    });
  }

  /**
   * 获取章节详情
   * GET /v1/collab/chapters/{chapterId}
   */
  async getChapter(chapterId) {
    return this.request(`/v1/collab/chapters/${chapterId}`, {
      method: 'GET'
    });
  }

  /**
   * 获取我的章节任务
   * GET /v1/collab/chapters/my
   */
  async listMyChapters() {
    return this.request('/v1/collab/chapters/my', {
      method: 'GET'
    });
  }

  /**
   * 创建评论
   * POST /v1/collab/chapters/{chapterId}/comments
   */
  async createComment(chapterId, data) {
    return this.request(`/v1/collab/chapters/${chapterId}/comments`, {
      method: 'POST',
      body: JSON.stringify(data)
    });
  }

  /**
   * 获取章节评论列表
   * GET /v1/collab/chapters/{chapterId}/comments
   */
  async listComments(chapterId) {
    return this.request(`/v1/collab/chapters/${chapterId}/comments`, {
      method: 'GET'
    });
  }

  /**
   * 标记评论已解决
   * PUT /v1/collab/comments/{commentId}/resolve
   */
  async resolveComment(commentId) {
    return this.request(`/v1/collab/comments/${commentId}/resolve`, {
      method: 'PUT'
    });
  }

  /**
   * 删除评论
   * DELETE /v1/collab/comments/{commentId}
   */
  async deleteComment(commentId) {
    return this.request(`/v1/collab/comments/${commentId}`, {
      method: 'DELETE'
    });
  }

  /**
   * RAG 翻译记忆查询
   * POST /v1/translate/rag
   */
  async queryRag(text, targetLang, engine) {
    return this.request('/rag', {
      method: 'POST',
      body: JSON.stringify({ text, targetLang, engine })
    }, false, true);
  }

  /* ==================== 平台统计接口 ==================== */

  /**
   * 获取平台统计数据
   * GET /platform/stats
   */
  async getPlatformStats() {
    return this.request('/platform/stats', {
      method: 'GET'
    }, false, false);
  }

  /* ==================== 工具方法 ==================== */

  /**
   * 获取当前登录用户信息（本地）
   * 推荐使用 AuthManager.getCurrentUser()
   */
  getCurrentUser() {
    const userStr = localStorage.getItem('userInfo');
    return userStr ? JSON.parse(userStr) : null;
  }

  /**
   * 检查用户是否已登录
   *  delegating to AuthManager.isAuthenticated() for unified auth checking
   */
  isAuthenticated() {
    // 使用 AuthManager 统一检查认证状态
    return typeof AuthManager !== 'undefined' ? AuthManager.isAuthenticated() : false;
  }

  /**
   * 获取 Token
   * 推荐使用 AuthManager.getAuthToken()
   */
  getToken() {
    return localStorage.getItem('authToken');
  }
}

// 创建全局 API 客户端实例
const apiClient = new ApiClient();

// 导出 ApiClient 类和全局实例
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { ApiClient, apiClient };
} else {
  window.ApiClient = ApiClient;
  window.apiClient = apiClient;
}
