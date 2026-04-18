// welcome.js - 引导页面按钮处理
document.addEventListener('DOMContentLoaded', function() {
    var loginBtn = document.getElementById('btn-login');
    var closeBtn = document.getElementById('btn-close');

    if (loginBtn) {
        loginBtn.addEventListener('click', function() {
            // 获取基础 URL 并跳转到登录页
            chrome.storage.local.get(['apiBaseUrl'], function(result) {
                var baseUrl = result.apiBaseUrl || 'http://127.0.0.1:7341';
                chrome.tabs.create({ url: baseUrl + '/pages/index.html' });
            });
        });
    }

    if (closeBtn) {
        closeBtn.addEventListener('click', function() {
            window.close();
        });
    }
});
