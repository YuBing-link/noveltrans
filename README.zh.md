# NovelTranslator - 双语小说翻译系统

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.x-blue.svg)](https://www.python.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)

一个双语小说翻译系统，包含浏览器插件、Web 管理端、Spring Boot 后端、大模型翻译微服务和 Nginx 网关。支持 OpenAI 兼容 API、三级缓存、SSE 流式返回、术语库管理和用户认证体系。

[English README](README.md)

## 系统架构

```
┌──────────────┐    ┌──────────────┐    ┌───────────────┐    ┌──────────────────┐    ┌─────────────────┐
│ 浏览器插件   │───▶│  Nginx 网关  │───▶│ Spring Boot   │───▶│ Python 翻译引擎  │───▶│ OpenAI 兼容 API │
│ (Chrome Ext) │    │  (端口 7341) │    │ (端口 8080)   │    │ (端口 8000)      │    │ / Claude / Ollama│
└──────────────┘    └──────────────┘    └───────────────┘    └──────────────────┘    └─────────────────┘
                           │                     │                        │
                    静态资源/CORS          MySQL 8.0 数据库         引擎注册/降级/限流
                    反向代理               Redis 缓存 (L2)          专业小说翻译 Prompt
                                           Caffeine 缓存 (L1)
```

## 项目结构

```
novelTranslator/
├── extension/                   # 浏览器插件（Chrome Extension）
│   ├── manifest.json            #   插件配置
│   └── src/
│       ├── background/          #   后台服务
│       ├── content/             #   内容脚本（网页翻译、阅读模式、划词翻译）
│       ├── popup/               #   弹出窗口
│       ├── options/             #   设置页面
│       └── lib/                 #   第三方库（Readability、DOMPurify 等）
├── frontend/                    # Web 管理端（原生 HTML/CSS/JS）
│   ├── pages/                   #   页面（首页、翻译、用户中心、术语库等）
│   ├── js/                      #   业务逻辑（API 客户端、认证、翻译等）
│   ├── styles/                  #   样式（响应式、动画、共享样式）
│   ├── utils/                   #   工具函数（UI 组件）
│   └── config.js                #   前端配置
├── src/main/java/               # Spring Boot 后端（Java 21）
│   └── com/yumu/noveltranslator/
│       ├── controller/          #   REST API 控制器
│       ├── service/             #   业务逻辑层
│       ├── mapper/              #   MyBatis-Plus 数据访问
│       ├── entity/              #   数据实体
│       ├── dto/                 #   数据传输对象
│       ├── config/              #   配置类（Redis、Security 等）
│       ├── security/            #   Spring Security + JWT 认证
│       └── util/                #   工具类
├── services/translate-engine/   # Python 翻译微服务
│   └── translate_server.py      #   FastAPI + OpenAI SDK + 引擎降级
├── nginx/                       # Nginx 网关配置
│   └── nginx.conf               #   反向代理、CORS、静态资源
├── docker-compose.yml           # Docker 一键启动
└── pom.xml                      # Maven 构建配置
```

## 技术亮点

### 缓存架构
- **三级缓存体系**：Caffeine (L1, 10 分钟) → Redis (L2, 30 分钟) → MySQL (L3, 24 小时)
- **缓存穿透防护**：空值占位 + 短暂过期策略
- **缓存击穿防护**：基于 ConcurrentHashMap 的同 key 并发加锁
- **缓存雪崩防护**：过期时间随机抖动

### 翻译引擎
- **OpenAI 兼容 API**：支持 OpenAI GPT、Claude（兼容层）、本地 Ollama 等任意兼容端点
- **专业小说翻译 Prompt**：6 条翻译原则，确保文学翻译质量
- **智能调度与降级**：引擎健康检查、冷却隔离、多级降级兜底
- **请求限流**：滑动窗口算法，每秒最多 10 个请求

### 后端架构
- **SSE 流式翻译**：浏览器端渐进式渲染，大段文本翻译体验流畅
- **虚拟线程并发**：Java 21 Virtual Threads + Semaphore 用户级别限流
- **双引擎概率轮询**：基于历史成功率的智能路由

### 前端
- **浏览器插件**：完整实现网页翻译、阅读模式、划词翻译三大模式
- **Web 管理端**：用户中心、术语库管理、翻译历史、偏好设置
- **纯原生实现**：无框架依赖，轻量快速
- **响应式设计**：适配桌面与移动端

### 安全
- **JWT 认证**：Spring Security + auth0-jwt，密钥强制环境变量注入
- **BCrypt 密码加密**：用户密码哈希存储
- **邮箱验证**：注册/密码重置双重验证
- **分级限流**：匿名用户 / 免费用户 / Pro 用户差异化并发限制

## 快速启动

### 方式一：Docker Compose（推荐，一键启动）

```bash
# 配置环境变量
export JWT_SECRET="your-secret-key-here"
export OPENAI_API_KEY="sk-xxx"
export OPENAI_BASE_URL="https://api.openai.com/v1"  # 或你的兼容端点
export MYSQL_ROOT_PASSWORD="root"

# 一键启动所有服务
docker compose up -d
```

服务启动后：
- Web 管理端：http://localhost:7341
- 后端 API：http://localhost:7341/v1
- 健康检查：http://localhost:7341/health

### 方式二：手动启动

#### 1. 启动 MySQL 和 Redis

```bash
docker run -d --name mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=novel_translator \
  mysql:8.0

docker run -d --name redis -p 6379:6379 redis:7
```

#### 2. 启动后端

```bash
export JWT_SECRET="your-secret-key-here"
export MYSQL_HOST=localhost
export REDIS_HOST=localhost

mvn clean package -DskipTests
mvn spring-boot:run
# 后端启动在 http://localhost:8080
```

#### 3. 启动翻译引擎

```bash
pip install fastapi uvicorn openai

export OPENAI_API_KEY="sk-xxx"
export OPENAI_BASE_URL="https://api.openai.com/v1"
export OPENAI_MODEL="gpt-4o-mini"

python services/translate-engine/translate_server.py
# 引擎启动在 http://localhost:8000
```

#### 4. 安装浏览器插件

1. 打开 Chrome，进入 `chrome://extensions/`
2. 开启"开发者模式"
3. 点击"加载已解压的扩展程序"，选择 `extension/` 目录

## 环境变量

| 变量 | 说明 | 默认值 | 必填 |
|------|------|--------|------|
| `JWT_SECRET` | JWT 签名密钥 | 无 | 是 |
| `JWT_EXPIRATION` | Token 有效期（毫秒） | 2592000000 (30 天) | 否 |
| `MYSQL_HOST` | MySQL 主机地址 | localhost | 否 |
| `MYSQL_PORT` | MySQL 端口 | 3306 | 否 |
| `MYSQL_DB` | MySQL 数据库名 | novel_translator | 否 |
| `MYSQL_USER` | MySQL 用户名 | root | 否 |
| `MYSQL_PASSWORD` | MySQL 密码 | 无 | 否 |
| `REDIS_HOST` | Redis 主机地址 | localhost | 否 |
| `REDIS_PORT` | Redis 端口 | 6379 | 否 |
| `REDIS_PASSWORD` | Redis 密码 | 无 | 否 |
| `OPENAI_API_KEY` | OpenAI API 密钥 | 无 | 是 |
| `OPENAI_BASE_URL` | API 基础 URL | https://api.openai.com/v1 | 否 |
| `OPENAI_MODEL` | 翻译模型名称 | gpt-4o-mini | 否 |
| `MAIL_USERNAME` | 邮箱地址（用于邮件验证） | 无 | 邮件功能需要 |
| `MAIL_PASSWORD` | 邮箱 SMTP 授权码 | 无 | 邮件功能需要 |

## API 文档

- [API_ENDPOINTS.md](API_ENDPOINTS.md) - 三种翻译模式的请求/响应示例
- [API_DOCUMENTATION.md](API_DOCUMENTATION.md) - 完整后端 API 文档

## License

本项目采用 [MIT License](LICENSE) 开源协议。
