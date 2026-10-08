# RAGdemo · 基于 Spring AI 的 RAG 知识库问答系统

上传 PDF 文档 → 自动切片并向量化入库 → 用户提问时基于向量检索召回资料，由大模型**只依据资料回答并标注来源**，资料中不存在的内容明确回答"根据现有资料无法回答"（防幻觉）。

## 技术栈

| 组件 | 选型 | 说明 |
|---|---|---|
| 语言 | Java 17 | |
| 框架 | Spring Boot 4.0.8 + Spring AI 2.0.1 | spring-ai-starter-model-openai、spring-ai-vector-store-elasticsearch、spring-ai-pdf-document-reader |
| 向量库 | Elasticsearch 9.5.4 | Windows 免安装版，cosine 相似度，1024 维向量 |
| Embedding 模型 | 百炼 qwen3.7-text-embedding | dashscope compatible-mode 接口 |
| 对话模型 | DeepSeek deepseek-chat | OpenAI 兼容接口 |

> 为什么选 Elasticsearch 而不是 Redis？本项目运行在无 Docker 的 Windows 环境，且旧版 Windows 移植版 Redis 不支持向量检索；ES 9 提供免安装版与原生向量检索能力。

## 核心流程

### 入库（上传 PDF → 向量化）

```
PDF(MultipartFile) → ByteArrayResource → PagePdfDocumentReader（按页解析）
  → List<Document>（每页一个，带 file_name / page_number 元数据）
  → TokenTextSplitter（chunkSize 800 token，切块）
  → 每块转 1024 维向量 → Elasticsearch(rag-document-index)
```

### 问答（问题 → 检索 → 生成）

```
question → embedding 向量化 → similaritySearch(topK=5) 召回最相关 5 块
  → 拼装 context（每块标注来源：文件名+页码）
  → system prompt（限定身份 + 只准按资料回答 + 缺失时明确说不知道 + 标注来源）
  → user(question) → deepseek-chat → 答案 + 来源列表
```

防幻觉设计：system prompt 强制模型"只依据检索到的资料回答"，资料中没有的内容必须回答"根据现有资料无法回答"，禁止编造。

## 快速开始

1. **启动 Elasticsearch**：运行 `D:\GongJu\elasticsearch-9.5.4\bin\elasticsearch.bat`，确认 `http://localhost:9200` 可访问
2. **配置 API Key**：复制 `src/main/resources/application-local.yml.example` 为 `application-local.yml`，填入 DeepSeek 与百炼的 key（该文件已被 .gitignore 忽略，不会提交）
3. **启动应用**：IDEA 运行 `RaGdemoApplication`，浏览器打开 `http://localhost:8080`
4. **上传文档**：页面①上传 PDF；或 `curl.exe -F "file=@xx.pdf" http://localhost:8080/document/upload`
5. **提问**：页面②输入问题；或 `http://localhost:8080/rag/ask?question=宿舍几点关门`

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/chat?message=` | 纯对话（无资料，对照实验用） |
| POST | `/document/upload` | 上传 PDF 并向量化入库，返回 `{fileName, chunkCount}` |
| GET | `/rag/ask?question=` | RAG 问答，返回 `{question, answer, sources}` |

## 目录结构

```
src/main/java/org/example/ragdemo/
├── RaGdemoApplication.java
├── controller/
│   ├── ChatController.java      # GET /chat 纯对话
│   ├── DocumentController.java  # POST /document/upload 上传入库
│   └── RagController.java       # GET /rag/ask 问答
└── service/
    ├── DocumentService.java     # 解析 PDF → 切块 → 向量化入库
    └── RagService.java          # 检索 → 拼上下文 → 组装 prompt → 生成
src/main/resources/
├── application.yml              # 公共配置（不包含 key）
├── application-local.yml        # 本地 key 配置（git 忽略）
└── static/index.html            # 演示页面（上传 + 问答）
```

## 开发中踩过的坑（经验沉淀）

1. **Maven 依赖找不到**：IDEA 全局 settings.xml 的 `localRepository` 被指向了错误仓库，注释掉后恢复默认 `~/.m2/repository`
2. **Embedding 模型属性名**：Spring AI 2.0 中 embedding 模型配置是 `spring.ai.openai.embedding.options.model`，写成 `model` 不生效
3. **向量维度**：百炼 qwen3.7-text-embedding 输出 1024 维，`dimensions` 必须配置 1024（不是 1536）
4. **PowerShell curl 别名**：PowerShell 里 `curl` 是 `Invoke-WebRequest` 别名，不支持 `-F`，上传需用 `curl.exe`
5. **启动顺序**：必须先启动 Elasticsearch 再启动应用，否则应用连接向量库失败
