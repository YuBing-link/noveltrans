// 此脚本通过 <script src=""> 注入到页面上下文，用于读取 localStorage 中的 token
// 不受 CSP inline-script 限制，因为是通过 src 属性加载的外部脚本
(function() {
    try {
        var token = localStorage.getItem('authToken');
        var userInfoStr = localStorage.getItem('userInfo');
        var userInfo = null;
        if (userInfoStr) {
            try { userInfo = JSON.parse(userInfoStr); } catch(e) {}
        }
        window.postMessage({
            source: 'noveltrans-auth-sync',
            token: token || null,
            userInfo: userInfo
        }, '*');
    } catch(e) {
        window.postMessage({
            source: 'noveltrans-auth-sync',
            token: null,
            userInfo: null,
            error: e.message
        }, '*');
    }
})();
