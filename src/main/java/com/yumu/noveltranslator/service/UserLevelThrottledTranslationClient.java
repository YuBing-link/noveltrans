package com.yumu.noveltranslator.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.yumu.noveltranslator.config.TranslationLimitProperties;
import com.yumu.noveltranslator.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于用户级别的限流翻译客户端
 * 为不同用户级别提供不同的并发限制
 * 支持基于优秀率概率的动态轮询翻译服务
 *
 * 轮询策略：
 * 1. 统计每个引擎的「优秀翻译次数」（成功 + 快速响应）
 * 2. 计算优秀率 = 优秀次数 / 总请求数
 * 3. 根据优秀率分配请求概率
 * 4. 计数器每分钟重置，保持时效性
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserLevelThrottledTranslationClient {

    // 超时配置（毫秒）
    private static final int CONNECT_TIMEOUT_MS = 5000;    // 连接超时 5 秒
    private static final int READ_TIMEOUT_MS = 30000;      // 读取超时 30 秒
    private static final long SEMAPHORE_TIMEOUT_SECONDS = 30; // 信号量等待超时 30 秒

    @Value("${translation.python.url:http://llm-engine:8000/translate}")
    private String pythonTranslateUrl;

    private static final boolean ENABLE_ROUND_ROBIN_TRANSLATION = true;

    // ========== 轮询配置 ==========

    // 优秀翻译判定标准：响应时间 <= 1000ms 视为优秀
    private static final long EXCELLENT_RESPONSE_TIME_MS = 1000;

    // 计数器重置间隔：60 秒
    private static final long STATS_RESET_INTERVAL_SECONDS = 60;

    // 最小请求数（冷启动保护，避免统计样本不足）
    private static final int MIN_REQUESTS_FOR_STATS = 5;

    // ========== 轮询计数器 ==========

    // Python 服务统计
    private final AtomicInteger pythonRequestCount = new AtomicInteger(0);
    private final AtomicInteger pythonExcellentCount = new AtomicInteger(0);
    private final AtomicLong pythonLastResetTime = new AtomicLong(System.currentTimeMillis());

    // MTran 服务统计
    private final AtomicInteger mTranRequestCount = new AtomicInteger(0);
    private final AtomicInteger mTranExcellentCount = new AtomicInteger(0);
    private final AtomicLong mTranLastResetTime = new AtomicLong(System.currentTimeMillis());

    private final ExternalTranslationService externalTranslationService;
    private final TranslationLimitProperties limitProperties;

    private Semaphore freeUserSemaphore;
    private Semaphore proUserSemaphore;
    private Semaphore anonymousUserSemaphore;

    /**
     * 禁用代理的 ProxySelector，确保内部 Docker 服务直连
     */
    private static final java.net.ProxySelector NO_PROXY_SELECTOR = new java.net.ProxySelector() {
        @Override
        public java.util.List<java.net.Proxy> select(java.net.URI uri) {
            System.out.println("[直连] ProxySelector.select(" + uri + ") -> NO_PROXY");
            return java.util.List.of(java.net.Proxy.NO_PROXY);
        }
        @Override
        public void connectFailed(java.net.URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
            System.out.println("[直连] connectFailed(" + uri + "): " + ioe.getMessage());
        }
    };

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .proxy(NO_PROXY_SELECTOR)
            .build();

    /**
     * 初始化用户级别信号量（从配置文件读取）
     */
    @PostConstruct
    public void init() {
        this.freeUserSemaphore = new Semaphore(limitProperties.getFreeConcurrencyLimit());
        this.proUserSemaphore = new Semaphore(limitProperties.getProConcurrencyLimit());
        this.anonymousUserSemaphore = new Semaphore(limitProperties.getAnonymousConcurrencyLimit());
    }


    /**
     * 翻译请求（支持指定是否使用 MTranServer 及 html 模式）
     * 支持引擎降级机制：远程引擎 (Python) 失败时自动降级到本地引擎 (MTranServer)
     *
     * @param text 待翻译文本
     * @param targetLang 目标语言
     * @param engine 翻译引擎
     * @param html 是否启用 HTML 翻译模式（仅对 MTranServer 有效）
     * @return 翻译结果 JSON
     */
    public String translate(String text, String targetLang, String engine, boolean html) {
        return translate(text, targetLang, engine, html, false);
    }

    /**
     * 强制走 Python 服务翻译（专家模式）
     *
     * @param text 待翻译文本
     * @param targetLang 目标语言
     * @param engine 翻译引擎（如 google/deepl 等）
     * @return 翻译结果 JSON
     */
    public String translateWithPython(String text, String targetLang, String engine) {
        Semaphore userSemaphore = getUserSemaphore();
        try {
            if (userSemaphore.tryAcquire(SEMAPHORE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                try {
                    return doTranslateRequest(text, targetLang, engine);
                } finally {
                    userSemaphore.release();
                }
            } else {
                String errorMsg = "并发请求过多，请稍后重试";
                log.warn("限流：{}", errorMsg);
                throw new RuntimeException(errorMsg);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("请求被中断", e);
        } catch (Exception e) {
            String errorMsg = "翻译失败：" + e.getMessage();
            log.error("Python 翻译失败：{}", errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * 翻译请求（支持快速模式）
     *
     * @param text 待翻译文本
     * @param targetLang 目标语言
     * @param engine 翻译引擎
     * @param html 是否启用 HTML 翻译模式
     * @param fastMode 快速模式：true=直接走 MTranServer，false=走轮询
     * @return 翻译结果 JSON
     */
    public String translate(String text, String targetLang, String engine, boolean html, boolean fastMode) {
        Semaphore userSemaphore = getUserSemaphore();

        try {
            // 尝试获取许可，设定超时时间
            if (userSemaphore.tryAcquire(SEMAPHORE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                try {
                    // 快速模式（网页翻译）：直接使用 MTranServer
                    if (fastMode) {
                        return doExternalTranslationRequest(text, targetLang, html);
                    }
                    // 阅读器模式：直接使用 MTranServer
                    if (html) {
                        return doExternalTranslationRequest(text, targetLang, html);
                    }
                    // 普通模式：走轮询逻辑（支持降级到 MTranServer）
                    if (ENABLE_ROUND_ROBIN_TRANSLATION) {
                        return translateWithRoundRobin(text, targetLang, engine);
                    } else {
                        return translateWithFallback(text, targetLang, engine);
                    }
                } finally {
                    userSemaphore.release();
                }
            } else {
                String errorMsg = "并发请求过多，请稍后重试";
                log.warn("限流：{}", errorMsg);
                throw new RuntimeException(errorMsg);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMsg = "请求被中断";
            log.warn("中断：{}", errorMsg);
            throw new RuntimeException(errorMsg, e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = "翻译请求失败：" + e.getMessage();
            log.error("错误：{}", errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * 基于优秀率概率的轮询翻译（支持双向降级：Python⇄MTranServer）
     *
     * 降级策略：
     * 1. 轮询到 Python 服务 → Python 所有引擎失败 → 降级到 MTranServer → MTranServer 失败 → 抛出异常
     * 2. 轮询到 MTranServer → MTranServer 失败 → 降级到 Python 服务 → Python 所有引擎失败 → 抛出异常
     *
     * 核心逻辑：
     * 1. 检查并重置过期的统计计数器
     * 2. 如果某个引擎样本不足，优先分配请求
     * 3. 计算各引擎的优秀率
     * 4. 根据优秀率计算被选中的概率
     * 5. 随机决定使用哪个引擎
     * 6. 首选引擎失败自动降级到另一引擎
     * 7. 所有引擎都失败时，抛出异常由调用方处理
     */
    private String translateWithRoundRobin(String text, String targetLang, String engine) {
        // 检查并重置过期统计
        resetStatsIfNeeded();

        // 计算当前应该使用哪个服务
        boolean usePythonService = shouldUsePythonService();

        if (usePythonService) {
            // 场景 1：轮询到 Python 服务
            log.debug("轮询：Python 服务（优秀率概率选择）");
            try {
                return doTranslateRequest(text, targetLang, engine);
            } catch (Exception e) {
                // Python 服务（所有引擎）失败，降级到 MTranServer（本地引擎）
                log.warn("Python 服务翻译失败，降级到 MTranServer: {}", e.getMessage());
                try {
                    return doExternalTranslationRequest(text, targetLang);
                } catch (Exception e2) {
                    // MTranServer 也失败，抛出异常
                    log.error("MTranServer 翻译也失败：{} (根本原因：{})", e2.getMessage(),
                             e2.getCause() != null ? e2.getCause().getMessage() : "未知", e2);
                    throw new RuntimeException("所有翻译引擎均失败 (Python → MTranServer): " + e.getMessage() + "; " + e2.getMessage(), e2);
                }
            }
        } else {
            // 场景 2：轮询到 MTranServer（本地引擎）
            log.debug("轮询：MTran 服务（优秀率概率选择）");
            try {
                return doExternalTranslationRequest(text, targetLang);
            } catch (Exception e) {
                // MTranServer 失败，降级到 Python 服务
                log.warn("MTranServer 翻译失败，降级到 Python 服务：{}", e.getMessage());
                try {
                    return doTranslateRequest(text, targetLang, engine);
                } catch (Exception e2) {
                    // Python 服务（所有引擎）也失败，抛出异常
                    log.error("Python 服务翻译也失败：{}", e2.getMessage());
                    throw new RuntimeException("所有翻译引擎均失败 (MTranServer → Python): " + e.getMessage() + "; " + e2.getMessage(), e2);
                }
            }
        }
    }

    /**
     * 带降级机制的翻译请求（非轮询模式）
     *
     * 降级策略：
     * 1. 首先尝试 Python 远程引擎
     * 2. Python 失败则降级到 MTranServer 本地引擎
     * 3. MTranServer 也失败则抛出异常
     *
     * @param text 待翻译文本
     * @param targetLang 目标语言
     * @param engine 翻译引擎
     * @return 翻译结果 JSON
     */
    private String translateWithFallback(String text, String targetLang, String engine) {
        try {
            log.debug("尝试 Python 服务翻译 [engine={}]", engine);
            return doTranslateRequest(text, targetLang, engine);
        } catch (Exception e) {
            // Python 服务失败，降级到 MTranServer
            log.warn("Python 服务翻译失败，降级到 MTranServer: {}", e.getMessage());
            try {
                return doExternalTranslationRequest(text, targetLang);
            } catch (Exception e2) {
                // MTranServer 也失败，抛出异常
                log.error("MTranServer 翻译也失败：{}", e2.getMessage());
                throw new RuntimeException("所有翻译引擎均失败: " + e.getMessage() + "; " + e2.getMessage(), e2);
            }
        }
    }

    /**
     * 判断是否应该使用 Python 服务
     * @return true 使用 Python 服务，false 使用 MTran 服务
     */
    private boolean shouldUsePythonService() {
        int pythonCount = pythonRequestCount.get();
        int mTranCount = mTranRequestCount.get();

        // 冷启动阶段：样本不足时，轮流使用
        if (pythonCount < MIN_REQUESTS_FOR_STATS && mTranCount < MIN_REQUESTS_FOR_STATS) {
            // 两者样本都不足，简单轮流
            return (pythonCount + mTranCount) % 2 == 0;
        }
        if (pythonCount < MIN_REQUESTS_FOR_STATS) {
            return true;  // Python 样本不足，优先用它
        }
        if (mTranCount < MIN_REQUESTS_FOR_STATS) {
            return false; // MTran 样本不足，优先用它
        }

        // 样本充足，基于优秀率计算概率
        int pythonExcellent = pythonExcellentCount.get();
        int mTranExcellent = mTranExcellentCount.get();

        // 计算优秀率
        double pythonExcellentRate = (double) pythonExcellent / pythonCount;
        double mTranExcellentRate = (double) mTranExcellent / mTranCount;

        // 计算 Python 被选中的概率 = Python 优秀率 / (Python 优秀率 + MTran 优秀率)
        double totalExcellent = pythonExcellentRate + mTranExcellentRate;
        if (totalExcellent == 0) {
            // 两者都没有优秀记录，平均分配
            return Math.random() < 0.5;
        }

        double pythonProbability = pythonExcellentRate / totalExcellent;
        double random = Math.random();

        log.debug("轮询统计 - Python: 请求={}, 优秀={}, 优秀率={:.2%} | MTran: 请求={}, 优秀={}, 优秀率={:.2%} | Python 概率={:.2%}",
                 pythonCount, pythonExcellent, pythonExcellentRate,
                 mTranCount, mTranExcellent, mTranExcellentRate,
                 pythonProbability);

        return random < pythonProbability;
    }

    /**
     * 检查并重置过期的统计计数器
     */
    private void resetStatsIfNeeded() {
        long now = System.currentTimeMillis();
        long resetIntervalMs = STATS_RESET_INTERVAL_SECONDS * 1000;

        // 使用 CAS 确保只有一个线程执行重置（避免竞态条件）
        long pythonLastReset = pythonLastResetTime.get();
        if (now - pythonLastReset > resetIntervalMs
                && pythonLastResetTime.compareAndSet(pythonLastReset, now)) {
            pythonRequestCount.set(0);
            pythonExcellentCount.set(0);
            log.info("Python 服务统计计数器已重置");
        }

        long mTranLastReset = mTranLastResetTime.get();
        if (now - mTranLastReset > resetIntervalMs
                && mTranLastResetTime.compareAndSet(mTranLastReset, now)) {
            mTranRequestCount.set(0);
            mTranExcellentCount.set(0);
            log.info("MTran 服务统计计数器已重置");
        }
    }

    /**
     * 记录翻译结果统计
     * @param isPython true=Python 服务，false=MTran 服务
     * @param success 是否成功
     * @param responseTime 响应时间（毫秒）
     */
    private void recordStats(boolean isPython, boolean success, long responseTime) {
        if (isPython) {
            pythonRequestCount.incrementAndGet();
            if (success && responseTime <= EXCELLENT_RESPONSE_TIME_MS) {
                pythonExcellentCount.incrementAndGet();
            }
        } else {
            mTranRequestCount.incrementAndGet();
            if (success && responseTime <= EXCELLENT_RESPONSE_TIME_MS) {
                mTranExcellentCount.incrementAndGet();
            }
        }
    }

    /**
     * 执行 HTTP 翻译请求（Python 服务）
     */
    private String doTranslateRequest(String text, String targetLang, String engine) throws Exception {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        try {
            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("text", text);
            bodyMap.put("target_lang", targetLang);
            bodyMap.put("engine", engine);
            bodyMap.put("fallback", true);
            String jsonBody = JSON.toJSONString(bodyMap);
            String result = doPythonServiceRequest(jsonBody, text);
            success = true;
            return result;
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            recordStats(true, success, costTime);
        }
    }

    /**
     * 调用 Python 翻译服务
     */
    private String doPythonServiceRequest(String jsonBody, String text) throws Exception {
        log.info("[Python请求] URL={}, body length={}", pythonTranslateUrl, jsonBody.length());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pythonTranslateUrl))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .timeout(Duration.ofMillis(READ_TIMEOUT_MS))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        log.info("[Python请求] 发送请求到 {}", request.uri());
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("[Python响应] status={}, body length={}", response.statusCode(), response.body().length());
        if (response.statusCode() != 200) {
            throw new Exception("HTTP 错误：" + response.statusCode());
        }
        return response.body();
    }

    /**
     * 调用 MTranService 翻译服务（默认 html=false）
     * MTranServer 返回格式：{"result": "翻译内容"}
     * 包装成标准格式返回：{"success": true, "engine": "mtran", "translatedContent": "翻译内容"}
     *
     * @param text 待翻译文本
     * @param targetLang 目标语言
     */
    private String doExternalTranslationRequest(String text, String targetLang) {
        return doExternalTranslationRequest(text, targetLang, false);
    }

    /**
     * 调用 MTranService 翻译服务
     * MTranServer 返回格式：{"result": "翻译内容"}
     * 包装成标准格式返回：{"success": true, "engine": "mtran", "translatedContent": "翻译内容"}
     *
     * @param text 待翻译文本
     * @param targetLang 目标语言
     * @param html 是否启用 HTML 翻译模式
     */
    private String doExternalTranslationRequest(String text, String targetLang, boolean html) {
        long startTime = System.currentTimeMillis();
        boolean success = false;

        try {
            JSONObject mtranResponse = externalTranslationService.translate("auto", targetLang, text, html);

            // 从 MTranServer 响应中提取 result 字段
            String translatedContent = mtranResponse.getString("result");
            if (translatedContent == null) {
                // 如果没有 result 字段，返回原始 JSON 作为兜底
                translatedContent = mtranResponse.toJSONString();
            }

            // 包装成标准格式
            JSONObject standardResponse = new JSONObject();
            standardResponse.put("success", true);
            standardResponse.put("engine", "mtran");
            standardResponse.put("translatedContent", translatedContent);

            success = true;
            return standardResponse.toJSONString();
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            recordStats(false, success, costTime);

            // 记录慢请求
            if (costTime > 5000) {
                log.info("[慢请求] MTran 翻译耗时：{}ms, 文本长度：{}, html: {}", costTime, text.length(), html);
            }
        }
    }


    /**
     * 根据用户身份获取对应的信号量
     */
    private Semaphore getUserSemaphore() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() &&
            authentication.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            String userLevel = userDetails.getUserLevel();

            if ("pro".equalsIgnoreCase(userLevel) || "premium".equalsIgnoreCase(userLevel)) {
                return proUserSemaphore;
            } else {
                // 默认为免费用户
                return freeUserSemaphore;
            }
        }

        // 未登录用户
        return anonymousUserSemaphore;
    }

    /**
     * 获取统计信息（用于监控和调试）
     */
    public Map<String, Object> getRoundRobinStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        int pythonCount = pythonRequestCount.get();
        int mTranCount = mTranRequestCount.get();

        stats.put("python_requests", pythonCount);
        stats.put("python_excellent", pythonExcellentCount.get());
        stats.put("python_excellent_rate", pythonCount > 0
            ? String.format("%.2f%%", (double) pythonExcellentCount.get() / pythonCount * 100)
            : "N/A");

        stats.put("mtran_requests", mTranCount);
        stats.put("mtran_excellent", mTranExcellentCount.get());
        stats.put("mtran_excellent_rate", mTranCount > 0
            ? String.format("%.2f%%", (double) mTranExcellentCount.get() / mTranCount * 100)
            : "N/A");

        stats.put("next_reset_seconds", STATS_RESET_INTERVAL_SECONDS -
            (System.currentTimeMillis() - Math.max(pythonLastResetTime.get(), mTranLastResetTime.get())) / 1000);

        return stats;
    }
}
