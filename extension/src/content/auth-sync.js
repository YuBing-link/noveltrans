// 此脚本通过 <script src=""> 注入到页面上下文，用于读取 localStorage 中的 token
// 不受 CSP inline-script 限制，因为是通过 src 属性加载的外部脚本
// 使用 CustomEvent 而非 postMessage，防止 token 泄露到第三方 iframe
(function() {
    try {
        var token = localStorage.getItem('authToken');
        var userInfoStr = localStorage.getItem('userInfo');
        var userInfo = null;
        if (userInfoStr) {
            try { userInfo = JSON.parse(userInfoStr); } catch(e) {}
        }
        document.dispatchEvent(new CustomEvent('noveltrans-auth-sync', {
            detail: { token: token || null, userInfo: userInfo }
        }));
    } catch(e) {
        document.dispatchEvent(new CustomEvent('noveltrans-auth-sync', {
            detail: { token: null, userInfo: null, error: e.message }
        }));
    }
})();
