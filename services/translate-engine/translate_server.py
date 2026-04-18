"""
小说翻译微服务 - OpenAI 兼容 API 翻译引擎
基于 FastAPI 框架，通过 OpenAI SDK 调用兼容 API（OpenAI / Claude / Ollama 等）
"""
import asyncio
import logging
import os
import time
from datetime import datetime, timedelta
from typing import Optional, Dict

import uvicorn
from fastapi import FastAPI, Body, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from openai import AsyncOpenAI

# =============================================================================
# 日志配置
# =============================================================================
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("translate-server")

# =============================================================================
# 环境变量配置
# =============================================================================
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
if not OPENAI_API_KEY:
    logger.warning("OPENAI_API_KEY 环境变量未设置，翻译功能将不可用")

OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")

logger.info(f"配置加载: OPENAI_BASE_URL={OPENAI_BASE_URL}")
logger.info(f"配置加载: OPENAI_MODEL={OPENAI_MODEL}")
logger.info(f"配置加载: OPENAI_API_KEY={'已设置' if OPENAI_API_KEY else '未设置'}")

# 初始化 OpenAI 异步客户端
openai_client = AsyncOpenAI(
    api_key=OPENAI_API_KEY or "sk-placeholder",
    base_url=OPENAI_BASE_URL,
)

# =============================================================================
# FastAPI 应用
# =============================================================================
app = FastAPI(title="Novel Translation Microservice", version="5.0")

# =============================================================================
# 1. 引擎注册表（保留扩展机制）
# =============================================================================
# 格式: 引擎名 -> (翻译函数, 是否需要 Key, 优先级)
ENGINE_REGISTRY: Dict[str, tuple] = {
    "openai": ("translate_openai", True, 1),
    # 别名映射：将前端传来的引擎名映射到实际引擎
    "google": ("translate_openai", True, 1),
    "deepseek": ("translate_openai", True, 1),
    "mtran": ("translate_openai", True, 1),
    # 未来可扩展其他引擎，例如:
    # "anthropic": ("translate_anthropic", True, 2),
    # "ollama": ("translate_ollama", False, 3),
}

# 引擎别名映射（前端引擎名 -> 实际注册引擎名）
ENGINE_ALIASES = {
    "google": "openai",
    "deepseek": "openai",
    "mtran": "openai",
    "libre": "openai",
    "baidu": "openai",
    "youdao": "openai",
}

# 自动生成候选引擎列表（按优先级排序）
ENGINE_CANDIDATES = sorted(
    ENGINE_REGISTRY.keys(),
    key=lambda x: ENGINE_REGISTRY[x][2],
)

logger.info(f"已注册引擎: {ENGINE_CANDIDATES}")
logger.info(f"引擎别名: {list(ENGINE_ALIASES.keys())}")

# =============================================================================
# 2. 引擎健康统计
# =============================================================================
class EngineStats:
    """单个翻译引擎的运行统计"""
    def __init__(self):
        self.request_count: int = 0
        self.success_count: int = 0
        self.fail_count: int = 0
        self.total_response_time: float = 0.0
        self.last_error: str = ""
        self.last_success_time: Optional[datetime] = None
        self.consecutive_failures: int = 0

# 统计注册表
engine_stats: Dict[str, EngineStats] = {}
for eng_name in ENGINE_REGISTRY:
    engine_stats[eng_name] = EngineStats()

# 健康检查配置
HEALTH_CHECK_THRESHOLD = 5          # 连续失败 5 次标记为不健康
UNHEALTHY_COOLDOWN_SECONDS = 60     # 不健康引擎冷却时间
unhealthy_engines: Dict[str, datetime] = {}  # 不健康引擎及其冷却过期时间

def is_engine_healthy(eng_name: str) -> bool:
    """检查引擎是否健康（不在冷却期且连续失败次数未超阈值）"""
    if eng_name in unhealthy_engines:
        if datetime.now() < unhealthy_engines[eng_name]:
            return False
        else:
            del unhealthy_engines[eng_name]
            logger.info(f"引擎 {eng_name} 冷却期结束，恢复可用")

    stats = engine_stats.get(eng_name)
    if stats and stats.consecutive_failures >= HEALTH_CHECK_THRESHOLD:
        return False
    return True

def record_engine_success(eng_name: str, response_time: float):
    """记录引擎成功"""
    stats = engine_stats.get(eng_name)
    if stats:
        stats.request_count += 1
        stats.success_count += 1
        stats.total_response_time += response_time
        stats.last_success_time = datetime.now()
        stats.consecutive_failures = 0
        if eng_name in unhealthy_engines:
            del unhealthy_engines[eng_name]
            logger.info(f"引擎 {eng_name} 从错误中恢复")

def record_engine_failure(eng_name: str, error: str):
    """记录引擎失败"""
    stats = engine_stats.get(eng_name)
    if stats:
        stats.request_count += 1
        stats.fail_count += 1
        stats.last_error = error
        stats.consecutive_failures += 1

        if stats.consecutive_failures >= HEALTH_CHECK_THRESHOLD:
            cooldown_until = datetime.now() + timedelta(seconds=UNHEALTHY_COOLDOWN_SECONDS)
            unhealthy_engines[eng_name] = cooldown_until
            logger.warning(
                f"引擎 {eng_name} 连续失败 {stats.consecutive_failures} 次，"
                f"标记为不健康，冷却至 {cooldown_until.isoformat()}"
            )

# =============================================================================
# 3. 请求限流（滑动窗口：每秒最多 10 个请求）
# =============================================================================
RATE_LIMIT_MAX = 10        # 每秒最大请求数
RATE_LIMIT_WINDOW = 1.0    # 窗口大小（秒）
_request_timestamps: list[float] = []

async def check_rate_limit() -> bool:
    """
    检查是否超过速率限制。
    使用滑动窗口算法：统计当前时间前 1 秒内的请求数。
    返回 True 表示允许通过，False 表示被限流。
    """
    now = time.monotonic()
    # 清理过期时间戳
    cutoff = now - RATE_LIMIT_WINDOW
    _request_timestamps[:] = [ts for ts in _request_timestamps if ts > cutoff]

    if len(_request_timestamps) >= RATE_LIMIT_MAX:
        return False

    _request_timestamps.append(now)
    return True

@app.middleware("http")
async def rate_limit_middleware(request: Request, call_next):
    """全局请求限流中间件（仅对 /translate 端点生效）"""
    if request.url.path == "/translate":
        allowed = await check_rate_limit()
        if not allowed:
            logger.warning("请求频率超出限制，返回 429")
            return JSONResponse(
                status_code=429,
                content={"code": 429, "error": "请求频率超出限制，请稍后重试"},
            )
    return await call_next(request)

# =============================================================================
# 4. 翻译引擎实现
# =============================================================================
# 系统 prompt：专业小说翻译角色
SYSTEM_PROMPT = """你是一位专业的小说翻译家，精通多国语言，拥有丰富的文学翻译经验。

翻译原则：
1. 忠实原文：准确传达原文的含义，不增不减
2. 文学风格：保留原文的文学风格、修辞手法和情感色彩
3. 语言流畅：译文自然流畅，符合目标语言的表达习惯
4. 文化适应：对文化特定元素进行恰当的本地化处理
5. 保持格式：保留原文中的段落结构、标点符号和特殊格式
6. 人名地名：专有名词的翻译保持一致性

请直接输出翻译结果，不要添加任何解释、注释或额外内容。"""

async def translate_openai(text: str, target_lang: str) -> str:
    """
    调用 OpenAI 兼容 API 进行翻译。
    支持 OpenAI、Claude（通过兼容层）、Ollama 等。
    """
    if not OPENAI_API_KEY:
        raise RuntimeError("OPENAI_API_KEY 未配置，无法调用翻译 API")

    # 构建翻译 prompt
    user_prompt = f"请将以下文本翻译为{target_lang}：\n\n{text}"

    logger.debug(f"调用 OpenAI API，模型: {OPENAI_MODEL}, 目标语言: {target_lang}, 文本长度: {len(text)}")

    response = await openai_client.chat.completions.create(
        model=OPENAI_MODEL,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt},
        ],
        temperature=0.3,     # 较低温度以保证翻译一致性
        max_tokens=4096,     # 支持较长文本
    )

    translated = response.choices[0].message.content
    if not translated:
        raise RuntimeError("API 返回空翻译结果")

    logger.debug(f"翻译完成，结果长度: {len(translated)}")
    return translated.strip()

# =============================================================================
# 5. 请求/响应模型
# =============================================================================
class TranslateRequest(BaseModel):
    text: str = Field(..., min_length=1, description="待翻译的原文")
    target_lang: str = Field(default="zh", description="目标语言代码，如 zh, en, ja 等")
    engine: str = Field(default="openai", description="指定翻译引擎，或 'auto' 自动选择")
    fallback: bool = Field(default=True, description="是否启用引擎降级")

class TranslateResponse(BaseModel):
    code: int
    data: str
    engine: str
    cost_ms: float
    is_fallback: bool

# =============================================================================
# 6. API 端点
# =============================================================================
@app.get("/health")
async def health():
    """微服务健康检查，供 Java 端探测"""
    engine_health = {}
    for eng_name, stats in engine_stats.items():
        is_healthy = is_engine_healthy(eng_name)
        success_rate = (
            round(stats.success_count / stats.request_count, 3)
            if stats.request_count > 0
            else 0
        )
        engine_health[eng_name] = {
            "healthy": is_healthy,
            "success_rate": success_rate,
            "request_count": stats.request_count,
            "consecutive_failures": stats.consecutive_failures,
        }

    # 检查 OpenAI API Key 是否可用
    api_ready = bool(OPENAI_API_KEY)

    return {
        "status": "UP" if api_ready else "DEGRADED",
        "api_key_configured": api_ready,
        "model": OPENAI_MODEL,
        "base_url": OPENAI_BASE_URL,
        "rate_limit": f"{RATE_LIMIT_MAX} req/s",
        "engine_health": engine_health,
        "engines": ENGINE_CANDIDATES,
    }

@app.post("/translate")
async def translate_api(req: TranslateRequest):
    """
    翻译端点：接收文本并返回翻译结果。
    支持引擎降级机制（当前仅注册 openai，未来可扩展）。
    """
    logger.info(
        f"收到翻译请求: engine={req.engine}, target_lang={req.target_lang}, "
        f"text_length={len(req.text)}"
    )
    start_time = time.perf_counter()

    # 构建候选引擎列表
    candidates = []
    if req.engine != "auto":
        if req.engine in ENGINE_REGISTRY and is_engine_healthy(req.engine):
            candidates.append(req.engine)
        elif req.engine in ENGINE_REGISTRY:
            logger.warning(f"指定引擎 {req.engine} 不健康，将使用降级引擎")
        else:
            raise HTTPException(
                status_code=400,
                detail=f"未知翻译引擎: {req.engine}。可用引擎: {ENGINE_CANDIDATES}",
            )

    # 按优先级添加健康引擎
    if req.engine == "auto" or req.fallback:
        for eng_name in ENGINE_CANDIDATES:
            if eng_name not in candidates and is_engine_healthy(eng_name):
                candidates.append(eng_name)

    if not candidates:
        raise HTTPException(
            status_code=503,
            detail="所有翻译引擎均不可用，请稍后重试",
        )

    last_error = "无"

    # 逐个尝试候选引擎
    for eng_name in candidates:
        try:
            # 获取翻译函数
            func_name = ENGINE_REGISTRY[eng_name][0]
            translator_func = globals().get(func_name)
            if not translator_func:
                logger.warning(f"引擎 {eng_name} 的翻译函数 {func_name} 未找到，跳过")
                continue

            # 执行翻译
            result = await translator_func(req.text, req.target_lang)

            # 记录成功
            cost_ms = (time.perf_counter() - start_time) * 1000
            record_engine_success(eng_name, cost_ms)

            logger.info(
                f"翻译成功: engine={eng_name}, cost_ms={cost_ms:.1f}, "
                f"result_length={len(result)}"
            )

            return TranslateResponse(
                code=200,
                data=result,
                engine=eng_name,
                cost_ms=round(cost_ms, 2),
                is_fallback=(req.engine != "auto" and eng_name != req.engine),
            )

        except Exception as e:
            error_msg = str(e)
            logger.error(f"引擎 {eng_name} 翻译失败: {error_msg}")
            record_engine_failure(eng_name, error_msg)
            last_error = error_msg
            continue

    # 所有引擎均失败
    logger.error(f"所有翻译引擎均失败，最后错误: {last_error}")
    raise HTTPException(
        status_code=503,
        detail=f"所有翻译引擎均失败。最后错误: {last_error}",
    )

# =============================================================================
# 7. 启动入口
# =============================================================================
if __name__ == "__main__":
    logger.info("=" * 60)
    logger.info("小说翻译微服务启动中...")
    logger.info(f"  模型: {OPENAI_MODEL}")
    logger.info(f"  基础 URL: {OPENAI_BASE_URL}")
    logger.info(f"  API Key: {'已配置' if OPENAI_API_KEY else '未配置'}")
    logger.info(f"  限流: {RATE_LIMIT_MAX} req/s")
    logger.info(f"  引擎: {ENGINE_CANDIDATES}")
    logger.info("=" * 60)

    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8000,
        log_level="info",
    )
