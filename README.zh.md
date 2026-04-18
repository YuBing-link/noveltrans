# NovelTranslator - 双语小说翻译系统

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.11-blue.svg)](https://www.python.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![Redis Stack](https://img.shields.io/badge/Redis%20Stack-7-red.svg)](https://redis.io/docs/latest/develop/data-structures/search/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://docs.docker.com/compose/)

一套完整的双语小说翻译系统，包含 Chrome 浏览器扩展、Spring Boot 后端、LLM 翻译微服务和 Nginx 网关。支持三大翻译模式、四级缓存体系、RAG 语义检索翻译记忆、实体一致性翻译、协作翻译工作流和 SSE 流式响应。

[English README](README.md)

## 系统架构

```
┌──────────────┐     ┌──────────────┐     ┌────────────────┐     ┌───────────────────┐     ┌──────────────────┐
│ Chrome 扩展   │────▶│  Nginx 网关   │────▶│ Spring Boot    │────▶│ Python / MTran    │────▶│ OpenAI 兼容 API   │
│ Manifest V3  │     │  端口 7341    │     │ 端口 8080       │     │ 引擎 8000 / 8989  │     │ / Claude / Ollama│
└──────────────┘     └──────────────┘     └───────┬────────┘     └───────────────────┘     └──────────────────┘
                                  │
                    ┌─────────────┼──────────────┐
                    ▼             ▼              ▼
                MySQL 8.0    Redis Stack 7    Caffeine
                (持久存储)   (HNSW向量检索)   (进程内缓存)
```

## 核心特性

### RAG 翻译记忆检索
- **向量语义检索**：基于 Redis Stack HNSW 向量索引，将翻译原文编码为 1536 维 Embedding 向量，通过 KNN 搜索历史翻译记忆
- **四级缓存链路**：Caffeine (L1) → Redis (L2) → MySQL (L3) → RAG 语义检索 (L4) → 翻译引擎
- **质量筛选**：入库前自动过滤空译文、长度异常、广告关键词、特殊字符过多等低质量翻译
- **双路降级策略**：Redis KNN 不可用时自动降级为 MySQL 余弦相似度计算
- **用户隔离**：KNN 查询按 `user_id` + `target_lang` 过滤，避免数据串扰

### 翻译引擎架构
- **OpenAI 兼容 API**：支持 OpenAI GPT、Claude（兼容层）、本地 Ollama、DeepSeek 等任意兼容端点
- **专业小说翻译 Prompt**：6 条翻译原则，确保文学翻译质量
- **双引擎容错**：LLM 翻译引擎 + MTranServer 轻量引擎双向降级，健康检查 + 冷却隔离
- **概率轮询路由**：基于历史成功率 + 响应时间的智能引擎选择

### 四级缓存体系
| 层级 | 组件 | 过期时间 | 职责 |
|------|------|----------|------|
| L1 | Caffeine | 10 分钟 | 进程内热点缓存 |
| L2 | Redis | 30 分钟 | 分布式缓存 |
| L3 | MySQL | 24 小时 | 持久化缓存 |
| L4 | RAG 向量检索 | 永久 | 语义相似度匹配 |

- **缓存穿透防护**：空值占位 + 短暂过期策略
- **缓存击穿防护**：`ConcurrentHashMap` 同 key 并发加锁
- **缓存雪崩防护**：过期时间随机抖动

### 后端工程
- **SSE 流式翻译**：浏览器端渐进式渲染，大段文本翻译体验流畅
- **虚拟线程并发**：Java 21 Virtual Threads + Semaphore 用户级限流
- **实体一致性翻译**：长文本自动提取命名实体 → 术语库合并 → 占位符替换 → 翻译 → 实体恢复，确保专有名词一致性
- **异步文档翻译**：大文件异步任务 + 进度追踪
- **Undertow 服务器**：高性能非阻塞 Web 服务器

### 浏览器扩展
- **网页翻译**：DOM 遍历分析 → 文本注册表 → SSE 流式回写 → 原位替换，保持页面布局不变
- **阅读模式**：集成 Mozilla Readability 提取正文，生成干净阅读视图
- **划词翻译**：悬浮提示框即时翻译选中文本
- **客户端缓存**：IndexedDB + 内存双级缓存 + 请求去重

### 协作翻译
- **项目状态机**：DRAFT → ACTIVE → COMPLETED → ARCHIVED，强制状态流转校验
- **章节工作流**：创建章节 → 分配译者 → 提交翻译 → 审核通过/驳回
- **角色权限**：OWNER / TRANSLATOR / REVIEWER，自定义 `@RequireProjectAccess` 注解
- **评论系统**：支持层级回复 + 标记解决
- **邀请机制**：UUID 邀请码加入项目

### 安全体系
- **JWT + API Key 双重认证**：支持 Spring Security + JWT Token 和 `nt_sk_xxxx` 格式 API Key 两种认证方式，共享同一翻译管线
- **API Key 管理**：通过 `/user/api-keys` 接口生成、查看、重置、删除 API Key，前缀 `nt_sk_` + 32 位随机字符，列表展示掩码脱敏
- **BCrypt 密码加密**：用户密码哈希存储
- **邮箱验证**：注册/密码重置双重验证
- **分级限流**：匿名用户 (3) / 免费用户 (5) / Pro 用户 (20) 差异化并发限制

### 字符配额系统
- **三档用户**：Free (每月 1 万字), Pro (每月 5 万字), Max (每月 20 万字)
- **模式系数**：快速模式 ×0.5（节省配额）, 专家模式 ×1.0, 团队模式 ×2.0
- **按请求扣减**：`消耗 = ceil(译文字符 × 模式系数)`，翻译开始前预检查配额
- **按天追踪**：`quota_usage` 表按天记录消耗，按月汇总
- **每月自动重置**：定时任务每月 1 号 0 点清理过期记录
- **文档上传预估**：根据文件大小预检查配额

| 档位 | 月字符包 | 快速 (×0.5) | 专家 (×1.0) | 团队 (×2.0) |
|------|----------|-------------|-------------|-------------|
| **Free** | 10,000 | 等效 2 万字原文 | 等效 1 万字原文 | 等效 5 千字原文 |
| **Pro** | 50,000 | 等效 10 万字原文 | 等效 5 万字原文 | 等效 2.5 万字原文 |
| **Max** | 200,000 | 等效 40 万字原文 | 等效 20 万字原文 | 等效 10 万字原文 |

## 快速启动

### 方式一：Docker Compose（推荐，一键启动）

```bash
# 克隆仓库
git clone https://github.com/your-org/novelTranslator.git
cd novelTranslator

# 一键启动所有服务（MySQL、Redis、MTranServer、LLM 引擎、后端、Nginx）
docker compose up -d
```

启动后访问：
- **Web 管理端**：http://localhost:7341
- **后端 API**：http://localhost:7341/v1
- **健康检查**：http://localhost:7341/health

### 方式二：手动启动

#### 前置条件
- Java 21（Temurin / OpenJDK）
- Maven 3.9+
- MySQL 8.0
- Redis 7（推荐 Redis Stack）
- Python 3.11+（翻译微服务）

#### 1. 启动 MySQL 和 Redis

```bash
docker run -d --name mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=novel_translator \
  mysql:8.0

docker run -d --name redis-stack -p 6379:6379 redis/redis-stack-server:latest
```

#### 2. 启动后端

```bash
export JWT_SECRET="your-secret-key-here"
export MYSQL_HOST=localhost
export REDIS_HOST=localhost

mvn clean package -DskipTests
java -jar target/novelTranslator-0.0.1-SNAPSHOT.jar
# 后端运行在 http://localhost:8080
```

#### 3. 启动翻译引擎

```bash
pip install fastapi uvicorn openai

export OPENAI_API_KEY="sk-xxx"
export OPENAI_BASE_URL="https://api.deepseek.com/v1"
export OPENAI_MODEL="deepseek-chat"

python services/translate-engine/translate_server.py
# 引擎运行在 http://localhost:8000
```

#### 4. 安装浏览器扩展

1. 打开 Chrome，进入 `chrome://extensions/`
2. 开启"开发者模式"
3. 点击"加载已解压的扩展程序"，选择 `extension/` 目录

## 项目结构

```
novelTranslator/
├── extension/                    # Chrome 扩展（Manifest V3）
│   ├── manifest.json
│   └── src/
│       ├── background/           #   后台服务 Worker（消息路由、API 调用）
│       ├── content/              #   内容脚本（网页翻译、阅读模式、划词翻译）
│       ├── popup/                #   弹出窗口 UI
│       ├── options/              #   设置页面
│       └── lib/                  #   第三方库（Readability、DOMPurify）
├── frontend/                     # Web 管理端（原生 HTML/CSS/JS）
│   ├── pages/                    #   页面（首页、翻译、用户中心、协作等）
│   ├── js/                       #   业务逻辑（API 客户端、认证、翻译）
│   ├── styles/                   #   样式（响应式、暗色模式、动画）
│   ├── utils/                    #   工具函数
│   └── config.js                 #   API 端点配置
├── src/main/java/                # Spring Boot 后端（Java 21）
│   └── com/yumu/noveltranslator/
│       ├── controller/           #   REST API 控制器
│       ├── service/              #   业务逻辑层
│       ├── mapper/               #   MyBatis-Plus 数据访问
│       ├── entity/               #   数据实体
│       ├── dto/                  #   数据传输对象
│       ├── config/               #   配置类（Redis 向量索引、安全、线程池）
│       ├── security/             #   Spring Security + JWT
│       ├── enums/                #   枚举（错误码、引擎、状态）
│       └── util/                 #   工具类
├── services/translate-engine/    # Python 翻译微服务
│   └── translate_server.py       #   FastAPI + OpenAI SDK + 引擎降级链
├── nginx/                        # Nginx 网关配置
│   └── nginx.conf
├── docker-compose.yml            # Docker 一键部署编排
└── pom.xml                       # Maven 构建配置
```

## API 文档

- [API_ENDPOINTS.md](API_ENDPOINTS.md) - 三种翻译模式的请求/响应示例
- [API_DOCUMENTATION.md](API_DOCUMENTATION.md) - 完整后端 API 参考

### API 速查

| 接口 | 方法 | 说明 | 认证 |
|------|------|------|------|
| `/v1/translate/webpage` | POST | 网页批量翻译（SSE 流式） | 否 |
| `/v1/translate/reader` | POST | 阅读模式文章翻译 | 否 |
| `/v1/translate/selection` | POST | 划词翻译 | 否 |
| `/v1/translate/text` | POST | 纯文本翻译 | 否 |
| `/v1/translate/document` | POST | 异步文档翻译 | 是 |
| `/v1/translate/rag` | POST | RAG 语义检索翻译记忆 | 是 |
| `/user/register` | POST | 用户注册 | 否 |
| `/user/login` | POST | 用户登录 | 否 |
| `/user/profile` | GET/PUT | 获取/更新用户信息 | 是 |
| `/user/quota` | GET | 查看字符配额使用情况 | 是 |
| `/user/api-keys` | GET/POST | 列出/创建 API Key | 是 |
| `/user/api-keys/{id}` | DELETE | 删除 API Key | 是 |
| `/user/api-keys/{id}/reset` | POST | 重置（重新生成）API Key | 是 |
| `/user/glossaries` | GET/POST | 术语库增删查 | 是 |
| `/user/preferences` | GET/PUT | 获取/更新用户偏好设置 | 是 |
| `/v1/collab/projects` | GET/POST | 协作项目管理 | 是 |

## 环境变量

| 变量 | 说明 | 默认值 | 必填 |
|------|------|--------|------|
| `JWT_SECRET` | JWT 签名密钥 | 无 | 是 |
| `MYSQL_HOST` | MySQL 主机 | localhost | 否 |
| `REDIS_HOST` | Redis 主机 | localhost | 否 |
| `TRANSLATION_OPENAI_API_KEY` | 后端翻译 API 密钥 | 无 | 是 |
| `OPENAI_API_KEY` | 微服务 API 密钥 | 无 | 是 |
| `OPENAI_BASE_URL` | API 基础 URL | https://api.openai.com | 否 |
| `OPENAI_MODEL` | 翻译模型 | gpt-4o-mini | 否 |
| `EMBEDDING_PROVIDER` | Embedding 提供商 | openai | 否 |
| `EMBEDDING_OPENAI_API_KEY` | Embedding API 密钥 | 无 | RAG 需要 |
| `MTRAN_HOST` | MTranServer 主机 | localhost | 否 |
| `MTRAN_PORT` | MTranServer 端口 | 8989 | 否 |

## 技术栈

| 层级 | 技术 |
|------|------|
| **后端** | Java 21, Spring Boot 3.2.0, Undertow, Spring Security, WebFlux |
| **数据库** | MySQL 8.0, MyBatis-Plus 3.5.5 |
| **缓存** | Caffeine (L1), Redis Stack 7 / Lettuce (L2), MySQL (L3) |
| **向量检索** | RediSearch HNSW, OpenAI text-embedding-3-small (1536 维) |
| **微服务** | Python 3.11, FastAPI, OpenAI SDK, MTranServer |
| **前端** | Chrome Extension (Manifest V3), 原生 JS, CSS3, Thymeleaf |
| **网关** | Nginx 1.28 |
| **构建部署** | Maven, Docker Compose |

## License

本项目采用 [MIT License](LICENSE) 开源协议。
