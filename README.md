# RAGdemo · 基于 Spring AI 的 RAG 知识库问答系统

上传 PDF 文档 → 自动切片并向量化入库 → 用户提问时基于向量检索召回资料，由大模型**只依据资料回答并标注来源**，资料中不存在的内容明确回答"根据现有资料无法回答"（防幻觉）。

## 功能特性

- **PDF 异步入库**：上传后立即返回 taskId，RabbitMQ 异步解析、切片、向量化（前端轮询状态）
- **防幻觉问答**：系统提示词强制"只依据检索资料回答"，检索结果为空或相似度过低时直接拦截，不调用大模型
- **SSE 流式输出**：`/rag/ask/stream` 逐字流式返回（前端打字机效果）
- **Redis 问答缓存**：相同问题命中缓存直接秒出（含来源）
- **多轮对话记忆**：ChatMemory 按 conversationId 保存上下文，Redis 持久化（scan 分页清理）
- **相似度阈值过滤**：检索结果相似度 < 0.5 视为资料外问题，不硬凑回答

## 技术栈

| 组件 | 选型 | 说明 |
|---|---|---|
| 语言 | Java 17 | |
| 框架 | Spring Boot 4.0.8 + Spring AI 2.0.1 | spring-ai-starter-model-openai、spring-ai-vector-store-elasticsearch、spring-ai-pdf-document-reader |
| 向量库 | Elasticsearch 9.5（本地 9.5.4 / Docker 9.5.5） | cosine 相似度，1024 维向量 |
| Embedding 模型 | 百炼 qwen3.7-text-embedding | dashscope compatible-mode 接口 |
| 对话模型 | DeepSeek deepseek-chat | OpenAI 兼容接口 |
| 消息队列 | RabbitMQ（management） | 上传 PDF 后异步入库解耦 |
| 缓存/记忆 | Redis | 问答缓存 + 多轮对话记忆 |
| 部署 | Docker + Docker Compose | 全家桶一键起（app + ES + Redis + RabbitMQ） |

## 核心流程

### 入库（上传 PDF → 异步向量化）

```
上传 PDF(MultipartFile) → 返回 taskId，立即响应
  → RabbitTemplate.convertAndSend 发消息到 ragExchange（routingKey=ragQueue）
  → @RabbitListener 消费 → PagePdfDocumentReader 按页解析
  → TokenTextSplitter（chunkSize 800）切块
  → 每块调 dashscope 转 1024 维向量 → 写入 Elasticsearch(rag-document-index)
  → 任务状态写入 Redis（PENDING → PROCESSING → SUCCESS/FAILED），前端轮询 /document/status
```

### 问答（问题 → 检索 → 生成）

```
question + conversationId → 查 Redis 缓存（命中直接返回，含来源）
  → 调 embedding 向量化 → similaritySearch(topK=5, 阈值 0.5) 召回相关块
  → 无结果/全低于阈值 → 直接返回"根据现有资料无法回答"（不调大模型）
  → 拼装 context（每块标注来源：文件名+页码）+ 历史记忆（MessageChatMemoryAdvisor）
  → system prompt（只准按资料回答 + 缺失时明确说不知道 + 标注来源）
  → user(question) → deepseek-chat → SSE 流式返回答案 + 来源列表（响应头）
  → 写入 Redis 缓存 + 追加对话记忆
```

防幻觉设计：system prompt 强制模型"只依据检索到的资料回答"；相似度阈值 + 空结果拦截保证资料外问题不调用大模型、不编造。

## 快速开始

### 方式 A：Docker 一键部署（推荐，交付形态）

1. 安装 **Docker Desktop**（Windows 需 WSL2）
2. 项目根目录创建 `.env`（不进 git，本地私有）：

```
DEEPSEEK_API_KEY=sk-你的deepseek密钥
DASHSCOPE_API_KEY=sk-你的百炼密钥
```

3. 一键起全家桶：

```powershell
docker compose up -d
```

4. 浏览器打开 `http://localhost:8080` → 上传 PDF → 提问

> 应用会自动等 ES/Redis/RabbitMQ 健康后再启动（`depends_on: service_healthy`），容器间用服务名通信（`elasticsearch`/`redis`/`rabbitmq`）。
> 中间件数据在容器内（未挂 volume），`docker compose down -v` 会清空索引与缓存。

### 方式 B：本地开发（调试用）

1. 启动 Elasticsearch：`D:\GongJu\elasticsearch-9.5.4\bin\elasticsearch.bat`，确认 `http://localhost:9200` 可访问
2. （可选）启动 Redis / RabbitMQ：`docker run -d --name redis -p 6379:6379 redis`、`docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:management`
3. 复制 `src/main/resources/application-local.yml.example` 为 `application-local.yml`，填入两个 key
4. IDEA 运行 `RaGdemoApplication`，浏览器打开 `http://localhost:8080`
5. 上传文档：页面①上传 PDF；或 `curl.exe -F "file=@xx.pdf" http://localhost:8080/document/upload`
6. 提问：页面②输入问题

> PowerShell 里 `curl` 是 `Invoke-WebRequest` 别名（不支持 `-F`），上传务必用 `curl.exe`。

## 配置说明（约定优于配置）

| 文件 | 作用 | 必须有吗 |
|---|---|---|
| `.env`（项目根） | compose 的 `${VAR}` 自动读取（shell 环境变量 > .env） | 被 `${VAR}` 引用时才需要，缺了变量为空 |
| `application.yml` | 公共配置（不包含 key） | 必须 |
| `application-{profile}.yml` | 激活 profile 后自动加载，同名键覆盖主文件 | 激活该 profile 时必须存在 |
| `application-local.yml` | 本地 key 配置，已被 .gitignore 忽略 | local profile 下必须 |
| `application-docker.yml` | docker profile 配置：中间件 host 用服务名，key 用 `${VAR}` 占位 | docker profile 下必须 |

docker profile 下 `spring.config.import: optional:classpath:application-local.yml`：`optional:` 前缀让被 import 的文件**不存在时静默跳过**（容器里没有 local 文件也能启动）。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/chat?message=` | 纯对话（无资料，对照实验用） |
| POST | `/document/upload` | 上传 PDF，异步入库，返回 `{fileName, taskId}` |
| GET | `/document/status?taskId=` | 查询异步入库任务状态（PENDING/PROCESSING/SUCCESS/FAILED） |
| GET | `/rag/ask?question=` | RAG 问答（非流式），返回 `{question, answer, sources}` |
| GET | `/rag/ask/stream?question=&conversationId=` | RAG 流式问答（SSE），sources 在响应头，新会话用 UUID |

## 目录结构

```
RAGdemo/
├── Dockerfile                 # 多阶段构建（maven 构建 → temurin 运行）
├── docker-compose.yml         # 全家桶编排（app + ES + Redis + RabbitMQ）
├── .env                       # API key（git 忽略，他人 clone 后自建）
└── src/main/java/org/example/ragdemo/
    ├── RaGdemoApplication.java
    ├── controller/
    │   ├── ChatController.java      # GET /chat 纯对话
    │   ├── DocumentController.java  # POST /document/upload + GET /document/status
    │   └── RagController.java       # GET /rag/ask + /rag/ask/stream
    ├── consumer/
    │   └── PdfUploadConsumer.java   # @RabbitListener 异步入库
    ├── config/
    │   ├── RabbitConfig.java        # 交换机/队列/绑定声明
    │   └── ChatMemoryConfig.java    # MessageWindowChatMemory + Redis 仓库
    ├── repository/
    │   └── RedisChatMemoryRepository.java  # ChatMemory Redis 读写（scan 分页）
    └── service/
        ├── DocumentService.java
        ├── RagService.java
        └── Impl/
            ├── DocumentServiceImpl.java
            └── RagServiceImpl.java
src/main/resources/
├── application.yml              # 公共配置（不包含 key）
├── application-local.yml        # 本地 key 配置（git 忽略）
├── application-docker.yml       # docker profile（服务名 + 环境变量占位）
└── static/index.html            # 演示页面（上传 + 流式问答）
```

## 开发中踩过的坑（经验沉淀）

1. **Maven 依赖找不到**：IDEA 全局 settings.xml 的 `localRepository` 指向错误仓库，注释掉后恢复默认 `~/.m2/repository`
2. **Embedding 模型属性名**：Spring AI 2.0 中 embedding 模型配置是 `spring.ai.openai.embedding.options.model`，写成 `model` 不生效
3. **向量维度**：百炼 qwen3.7-text-embedding 输出 1024 维，`dimensions` 必须配置 1024（不是 1536）
4. **PowerShell curl 别名**：`curl` 是 `Invoke-WebRequest`，上传需用 `curl.exe`；中文 URL 参数需 `[uri]::EscapeDataString()` 编码
5. **SSE 中文乱码**：响应头来源（sources）经 header 传输中文需 `ESCAPE_NON_ASCII`，否则 ISO-8859-1 解码乱码
6. **ES 镜像版本**：`elasticsearch:9.5.4` 官方未发布，9.5 系列最新 tag 是 `9.5.5`
7. **容器访问外网 000**：Docker Desktop 的 Containers proxy 跟随宿主代理，梯子关则容器外网全挂——dashscope/deepseek 是国内 API，改 **No proxy** 直连即可
8. **compose 占位符未替换**：`<从 application-local.yml 复制>` 这类占位文本被当成真实 key 注入，Authorization 头含中文直接 500——写配置后要回头检查占位符
9. **docker build 找不到 Dockerfile**：`.` 指当前目录，新开终端务必先 `cd` 到项目根
