// welcome.js - 引导页面按钮处理
document.addEventListener('DOMContentLoaded', function() {
    // 初始化 i18n
    NovelTransI18n.init(function() {
        NovelTransI18n.apply(document);
    });

    var loginBtn = document.getElementById('btn-login');
    var closeBtn = document.getElementById('btn-close');

    if (loginBtn) {
        loginBtn.addEventListener('click', function() {
            chrome.storage.local.get(['apiBaseUrl'], function(result) {
                var baseUrl = result.apiBaseUrl || 'http://localhost:7341';
                chrome.tabs.create({ url: baseUrl + '/login' });
            });
        });
    }

    if (closeBtn) {
        closeBtn.addEventListener('click', function() {
            window.close();
        });
    }
});