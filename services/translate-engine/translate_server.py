"""
小说翻译微服务 - OpenAI 兼容 API 翻译引擎
基于 FastAPI 框架，通过 OpenAI SDK 调用兼容 API（OpenAI / Claude / Ollama 等）
"""
import asyncio
import logging
import os
import time
from concurrent.futures import ThreadPoolExecutor
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

# 实体提取 prompt
ENTITY_EXTRACTION_PROMPT = """你是一位专业的文本分析专家。请从以下文本中提取所有人名（包括全名、简称、昵称）和地名（城市、国家、地区、建筑等）。

规则：
1. 只提取专有名词，不提取普通名词
2. 如果同一个实体出现多次，只保留一次
3. 如果存在嵌套实体（如"北京天安门"包含"北京"），保留最长匹配
4. 以 JSON 数组格式返回，不要添加任何解释

示例输入：张三和李四在北京见面，后来一起去了清华大学。
示例输出：["张三", "李四", "北京", "清华大学"]

请直接返回 JSON 数组。"""

# 实体翻译 prompt
ENTITY_TRANSLATION_PROMPT = """你是一位专业的翻译专家。请将以下专有名词翻译为{target_lang}。

规则：
1. 人名翻译遵循目标语言的命名惯例（如中文人名翻译为英文时使用拼音格式）
2. 地名翻译遵循目标语言的地理命名惯例
3. 如果实体在目标语言中有通用译名，使用该译名
4. 以 JSON 对象格式返回，key 为原文，value 为译文

示例（翻译为英文）：{{"张三": "Zhang San", "李四": "Li Si", "北京": "Beijing"}}
示例（翻译为日文）：{{"张三": "張三", "李四": "李四", "北京": "北京"}}

请直接返回 JSON 对象，不要添加任何解释。"""

async def translate_with_system_prompt(text: str, target_lang: str, system_prompt: str) -> str:
    """
    使用自定义 system prompt 调用 OpenAI 兼容 API 进行翻译。

    预处理：将单换行转为双换行，防止 LLM 吞掉对话行之间的换行。
    后处理：将双换行还原回单换行，保持原文格式。
    """
    if not OPENAI_API_KEY:
        raise RuntimeError("OPENAI_API_KEY 未配置，无法调用翻译 API")

    # 预处理：将单换行转为双换行，让 LLM 识别每行对话为独立段落
    prepared_text = text.replace("\n", "\n\n")

    user_prompt = f"请将以下文本翻译为{target_lang}：\n\n{prepared_text}"

    response = await openai_client.chat.completions.create(
        model=OPENAI_MODEL,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        temperature=0.3,
        max_tokens=4096,
    )

    translated = response.choices[0].message.content
    # DeepSeek thinking models return content blocks, extract text only
    if isinstance(translated, list):
        parts = []
        for block in translated:
            if isinstance(block, dict):
                if block.get("type") == "text":
                    parts.append(block.get("text", ""))
                elif "content" in block:
                    parts.append(block["content"])
            elif isinstance(block, str):
                parts.append(block)
        translated = "\n".join(parts)
    if not translated:
        raise RuntimeError("API 返回空翻译结果")

    # 后处理：将双换行还原回单换行，恢复原文的段落结构
    translated = translated.replace("\n\n", "\n").strip()

    return translated

async def translate_openai(text: str, target_lang: str) -> str:
    """
    调用 OpenAI 兼容 API 进行翻译。
    支持 OpenAI、Claude（通过兼容层）、Ollama 等。
    """
    return await translate_with_system_prompt(text, target_lang, SYSTEM_PROMPT)

import re

def clean_json_response(text: str) -> str:
    """从 LLM 响应中提取 JSON，处理 markdown 代码块、前后多余文本等。"""
    cleaned = text.strip()
    if not cleaned:
        raise ValueError("LLM 返回空响应")
    # 1. 尝试提取 markdown 代码块中的 JSON
    fence_match = re.search(r'```(?:json)?\s*([\s\S]*?)\s*```', cleaned)
    if fence_match:
        cleaned = fence_match.group(1).strip()
    # 2. 尝试从文本中提取 JSON 对象或数组
    json_match = re.search(r'(\{[\s\S]*\}|\[[\s\S]*\])', cleaned)
    if json_match:
        cleaned = json_match.group(1).strip()
    return cleaned

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

class EntityExtractionRequest(BaseModel):
    text: str = Field(..., min_length=1, description="待提取实体的原文")
    source_lang: str = Field(default="zh", description="源语言代码")
    target_lang: str = Field(default="en", description="目标语言代码（用于后续翻译）")

class EntityExtractionResponse(BaseModel):
    code: int
    entities: list[str]

class EntityTranslationRequestModel(BaseModel):
    entities: list[str] = Field(..., min_length=1, description="待翻译的实体列表")
    source_lang: str = Field(default="zh", description="源语言代码")
    target_lang: str = Field(default="en", description="目标语言代码")

class EntityTranslationResponse(BaseModel):
    code: int
    translations: dict[str, str]

class PlaceholderTranslateRequest(BaseModel):
    text: str = Field(..., min_length=1, description="包含占位符的待翻译文本")
    target_lang: str = Field(default="zh", description="目标语言代码")
    engine: str = Field(default="openai", description="指定翻译引擎")
    fallback: bool = Field(default=True, description="是否启用引擎降级")

class PlaceholderTranslateResponse(BaseModel):
    code: int
    data: str
    engine: str
    cost_ms: float

# =============================================================================
# 6. API 端点
# =============================================================================
@app.post("/extract-entities")
async def extract_entities_api(req: EntityExtractionRequest):
    """
    从原文中提取人名、地名等专有名词。
    返回 JSON 数组格式的实体列表。
    """
    logger.info(f"收到实体提取请求: source_lang={req.source_lang}, text_length={len(req.text)}")
    start_time = time.perf_counter()

    try:
        logger.info(f"[实体提取] 开始调用 LLM, text_length={len(req.text)}")
        result = await translate_with_system_prompt(
            req.text, req.source_lang, ENTITY_EXTRACTION_PROMPT
        )
        logger.info(f"[实体提取] LLM 返回, result_length={len(result) if result else 0}")
        # 解析 LLM 返回的 JSON 数组
        import json
        cleaned = clean_json_response(result)
        logger.info(f"[实体提取] clean_json_response 返回, cleaned_length={len(cleaned) if cleaned else 0}")

        entities = json.loads(cleaned)
        logger.info(f"[实体提取] json.loads 成功, entities_count={len(entities) if isinstance(entities, list) else 'not-list'}")
        if not isinstance(entities, list):
            raise ValueError(f"LLM 返回的不是列表格式，原始响应: {result[:200]}")

        # 去重
        seen = set()
        unique_entities = []
        for e in entities:
            e_lower = e.lower()
            if e_lower not in seen:
                seen.add(e_lower)
                unique_entities.append(e)

        cost_ms = (time.perf_counter() - start_time) * 1000
        logger.info(f"实体提取成功: 提取 {len(unique_entities)} 个实体, cost_ms={cost_ms:.1f}")
        return EntityExtractionResponse(code=200, entities=unique_entities)

    except Exception as e:
        logger.error(f"实体提取失败: {e}")
        raise HTTPException(status_code=500, detail=f"实体提取失败: {e}")

@app.post("/translate-entities")
async def translate_entities_api(req: EntityTranslationRequestModel):
    """
    批量翻译实体名，返回 {原文: 译文} 字典。
    """
    logger.info(f"收到实体翻译请求: entities={len(req.entities)}, target_lang={req.target_lang}")
    start_time = time.perf_counter()

    try:
        prompt = ENTITY_TRANSLATION_PROMPT.format(target_lang=req.target_lang)
        # 将实体列表展示给 LLM
        entities_str = ", ".join(req.entities)
        user_content = f"需要翻译的实体列表：{entities_str}\n\n请直接返回 JSON 对象。"

        response = await openai_client.chat.completions.create(
            model=OPENAI_MODEL,
            messages=[
                {"role": "system", "content": prompt},
                {"role": "user", "content": user_content},
            ],
            temperature=0.1,  # 极低温度保证翻译一致性
            max_tokens=1024,
        )

        # 提取文本（DeepSeek 等 thinking 模型可能返回 content blocks）
        def _extract_text(content) -> str:
            if isinstance(content, list):
                parts = []
                for block in content:
                    if isinstance(block, dict):
                        if block.get("type") == "text":
                            parts.append(block.get("text", ""))
                        elif "content" in block:
                            parts.append(block["content"])
                    elif isinstance(block, str):
                        parts.append(block)
                return "\n".join(parts)
            return str(content) if content else ""

        raw_content = response.choices[0].message.content
        result = _extract_text(raw_content)
        if not result:
            raise RuntimeError("API 返回空翻译结果")

        # 解析 JSON
        import json
        cleaned = clean_json_response(result)

        translations = json.loads(cleaned)
        if not isinstance(translations, dict):
            raise ValueError(f"LLM 返回的不是字典格式，原始响应: {result[:200]}")

        cost_ms = (time.perf_counter() - start_time) * 1000
        logger.info(f"实体翻译成功: 翻译 {len(translations)} 个实体, cost_ms={cost_ms:.1f}")
        return EntityTranslationResponse(code=200, translations=translations)

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"实体翻译失败: {e}")
        raise HTTPException(status_code=500, detail=f"实体翻译失败: {e}")

@app.post("/translate-with-placeholders")
async def translate_with_placeholders_api(req: PlaceholderTranslateRequest):
    """
    翻译包含占位符的文本，占位符不会被翻译。
    占位符格式：[{1}], [{2}], ...
    """
    logger.info(
        f"收到占位符翻译请求: target_lang={req.target_lang}, "
        f"engine={req.engine}, text_length={len(req.text)}"
    )
    start_time = time.perf_counter()

    # 提取文本中的占位符
    import re
    placeholders = re.findall(r'\[\{(\d+)\}\]', req.text)
    unique_placeholders = sorted(set(placeholders))
    placeholder_list = ", ".join(f"[{{{p}}}]" for p in unique_placeholders)

    # 构建带占位符保护指令的 system prompt
    system_prompt = f"""你是一位专业的小说翻译家，精通多国语言。

翻译原则：
1. 忠实原文：准确传达原文的含义，不增不减
2. 文学风格：保留原文的文学风格、修辞手法和情感色彩
3. 语言流畅：译文自然流畅，符合目标语言的表达习惯
4. 文化适应：对文化特定元素进行恰当的本地化处理
5. 保持格式：保留原文中的段落结构、标点符号和特殊格式

重要：文本中包含格式为 [{{N}}] 的占位符（如 {placeholder_list}）。
这些占位符**绝对不能被翻译、修改或删除**。请在翻译结果中原样保留它们。

请直接输出翻译结果，不要添加任何解释、注释或额外内容。"""

    try:
        # 复用现有的翻译逻辑，但使用自定义 system prompt
        candidates = []
        if req.engine != "auto":
            if req.engine in ENGINE_REGISTRY and is_engine_healthy(req.engine):
                candidates.append(req.engine)
            elif req.engine in ENGINE_REGISTRY:
                logger.warning(f"指定引擎 {req.engine} 不健康，将使用降级引擎")

        if req.engine == "auto" or req.fallback:
            for eng_name in ENGINE_CANDIDATES:
                if eng_name not in candidates and is_engine_healthy(eng_name):
                    candidates.append(eng_name)

        if not candidates:
            raise HTTPException(status_code=503, detail="所有翻译引擎均不可用")

        last_error = "无"
        for eng_name in candidates:
            try:
                func_name = ENGINE_REGISTRY[eng_name][0]
                translator_func = globals().get(func_name)
                if not translator_func:
                    continue

                result = await translate_with_system_prompt(req.text, req.target_lang, system_prompt)
                cost_ms = (time.perf_counter() - start_time) * 1000
                record_engine_success(eng_name, cost_ms)
                return PlaceholderTranslateResponse(
                    code=200, data=result, engine=eng_name, cost_ms=round(cost_ms, 2)
                )
            except Exception as e:
                error_msg = str(e)
                record_engine_failure(eng_name, error_msg)
                last_error = error_msg
                continue

        raise HTTPException(status_code=503, detail=f"所有翻译引擎均失败。最后错误: {last_error}")

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"占位符翻译失败: {e}")
        raise HTTPException(status_code=500, detail=f"占位符翻译失败: {e}")

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
# 6. AI 翻译团队端点 (Agentscope 多 Agent 协作)
# =============================================================================
# Separate thread pool for translation — agentscope uses asyncio.run() internally
# which requires a thread without a running event loop.
_translation_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="translate")
class TeamTranslateRequest(BaseModel):
    """AI 翻译团队请求体

    同时支持 Java 驼峰命名（sourceLang/targetLang/novelType）
    和 Python 下划线命名（source_lang/target_lang/novel_type）
    """
    model_config = {"populate_by_name": True}

    text: str = Field(..., description="章节原文")
    novel_type: str = Field(default="daily", alias="novelType", description="小说类型：battle/mystery/daily")
    source_lang: str = Field(default="Japanese", alias="sourceLang", description="源语言")
    target_lang: str = Field(default="Chinese", alias="targetLang", description="目标语言")
    glossary_terms: list[dict[str, str]] | None = Field(
        default=None,
        alias="glossaryTerms",
        description="术语表 [{source: '', target: '', note: ''}]",
    )
    placeholders: dict[str, str] | None = Field(
        default=None,
        description="占位符映射，如 {'[{1}]': '已翻译的专有名词'}",
    )


class TeamTranslateResponse(BaseModel):
    """AI 翻译团队响应体"""

    code: int = Field(200, description="状态码")
    data: str = Field("", description="翻译后的文本")
    cost_ms: float = Field(0, description="耗时（毫秒）")
    novel_type: str = Field("", description="使用的提示词风格")
    chunk_count: int = Field(0, description="分段数量")


def _sync_translate(
    text: str,
    novel_type: str,
    glossary_terms,
    source_lang: str,
    target_lang: str,
    placeholders,
) -> str:
    """Synchronous wrapper for translate_chapter, runs in a dedicated thread."""
    from agents.pipeline import translate_chapter

    return translate_chapter(
        text=text,
        novel_type=novel_type,
        glossary_terms=glossary_terms,
        source_lang=source_lang,
        target_lang=target_lang,
        placeholders=placeholders,
    )


@app.post("/translate-team")
async def translate_team(req: TeamTranslateRequest):
    """
    AI 翻译团队端点：使用 agentscope 多 Agent 协作翻译。

    Java 端传入章节原文、小说类型、术语表，Python 端通过 MsgHub 群聊协作
    完成翻译、审校、润色，返回最终译文。
    """
    start_time = time.perf_counter()

    try:
        from agents.pipeline import translate_chapter

        # Run sync translation in a separate thread to avoid event loop conflicts.
        # agentscope 1.x MsgHub is async but called from sync code; running in
        # a fresh thread gives it its own event loop where asyncio.run() works.
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(
            _translation_executor,
            _sync_translate,
            req.text,
            req.novel_type,
            req.glossary_terms,
            req.source_lang,
            req.target_lang,
            req.placeholders,
        )

        cost_ms = (time.perf_counter() - start_time) * 1000
        # 估算分段数量（pipeline 内部 chunk_size=4000）
        chunk_count = max(1, len(req.text) // 4000 + (1 if len(req.text) % 4000 else 0))

        return TeamTranslateResponse(
            code=200,
            data=result,
            cost_ms=round(cost_ms, 2),
            novel_type=req.novel_type,
            chunk_count=chunk_count,
        )

    except ImportError:
        logger.error("agentscope 模块未安装或 agents 目录不存在")
        raise HTTPException(status_code=500, detail="AI 翻译团队模块未就绪")
    except Exception as e:
        cost_ms = (time.perf_counter() - start_time) * 1000
        logger.error(f"AI 翻译团队翻译失败: {e}")
        raise HTTPException(
            status_code=503, detail=f"AI 翻译团队翻译失败: {str(e)}"
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
