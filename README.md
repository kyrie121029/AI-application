# AI-application

基于 Spring Boot 3 的 AI 应用开发平台：任务管理、JWT 认证、大模型同步/流式调用、文件上传与异步解析、图片多模态分析、RAG 检索闭环、受控 Function Calling Agent，附带 Vue 3 演示前端。

## 技术栈

| 技术 | 说明 |
|------|------|
| Java 17 | record、文本块等现代语法 |
| Spring Boot 3.4.3 | 核心框架（MVC + SseEmitter，非 WebFlux） |
| Spring Security + JWT | 无状态认证鉴权，资源归属隔离 |
| Spring Data JPA | ORM 持久化 |
| MySQL 8 / H2 | 生产 / 开发数据库 |
| Flyway | 数据库版本迁移（V1–V14） |
| SpringDoc OpenAPI | Swagger UI 接口文档 |
| Spring Boot Actuator | 健康检查（Docker healthcheck） |
| Apache PDFBox / POI | PDF / DOCX 文本解析 |
| MinIO | 对象存储（可切本地存储） |
| Qdrant | 向量存储（RAG 索引，派生索引） |
| Vue 3 + Vite + TS | 演示前端（对话 / 文件） |
| Docker / Docker Compose | 镜像构建与一键部署 |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+

### 启动（H2 开发模式）

```bash
git clone https://github.com/kyrie121029/AI-application.git
cd AI-application

# Windows
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

默认 `dev` profile：H2 内存库 + Mock AI，无需安装任何外部依赖。

### 切换 MySQL（prod profile）

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

连接信息在 `application-prod.yml`；敏感配置走环境变量。

### Docker 运行

```bash
docker build -t ai-application .
docker run -d --name ai-app -p 8080:8080 \
  -e JAVA_OPTS="-Xmx512m" \
  -v ai_uploads:/app/data/uploads \
  ai-application
```

### Docker Compose 一键部署（backend + MySQL + Qdrant + MinIO）

```bash
# 1. 准备配置（敏感值，不入 Git）
cp .env.example .env

# 2. 构建并启动
docker compose up --build -d

# 3. 验证
curl http://localhost:8080/actuator/health     # {"status":"UP"}
docker compose ps

# 4. 停止（加 -v 会清空数据卷）
docker compose down
```

- backend 通过 service name 连接 `mysql:3306` / `qdrant:6333` / `minio:9000`。
- 默认 `AI_PROVIDER=mock`（无需 API Key）；改 `.env` 为 `openai` + `DASHSCOPE_API_KEY` 即接真实模型。
- 数据持久化：MySQL / Qdrant / MinIO 与后端上传目录均挂命名卷。

**访问入口**

| 服务 | 地址 |
|------|------|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Actuator 健康检查 | http://localhost:8080/actuator/health |
| H2 控制台 | http://localhost:8080/h2-console（JDBC `jdbc:h2:mem:taskdb`，sa / 空密码） |
| MinIO Console | http://localhost:9001 |
| Qdrant Dashboard | http://localhost:6333/dashboard |
| 前端（dev） | http://localhost:5173（`cd frontend && npm install && npm run dev`） |

## API 概览

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| **认证** | POST | `/api/auth/register` | 用户注册 |
| | POST | `/api/auth/login` | 用户登录，返回 JWT |
| **任务** | POST | `/api/tasks` | 创建任务 |
| | GET | `/api/tasks` | 分页查询（状态/类型/关键词过滤） |
| | GET | `/api/tasks/{id}` | 查询单个任务 |
| | DELETE | `/api/tasks/{id}` | 删除任务 |
| | POST | `/api/tasks/{id}/generate` | AI 分析任务 |
| **对话** | POST | `/api/chat` | 新建对话（SSE 流式） |
| | POST | `/api/chat/{id}/messages` | 继续对话（SSE 流式） |
| | GET | `/api/chat` | 会话列表 |
| | GET | `/api/chat/{id}/messages` | 会话历史 |
| | DELETE | `/api/chat/{id}` | 删除会话 |
| **文件** | POST | `/api/files` | 上传文件（multipart，pdf/docx/txt/jpg/png/webp） |
| | GET | `/api/files` | 文件列表 |
| | GET | `/api/files/{id}` | 文件元数据 |
| | GET | `/api/files/{id}/download` | 下载（JWT 保护，不暴露绝对路径） |
| | DELETE | `/api/files/{id}` | 删除文件（含派生向量清理） |
| | POST | `/api/files/{id}/parse` | 提交/重试文档解析 |
| | GET | `/api/files/{id}/parse-status` | 解析状态 |
| | POST | `/api/files/{id}/image-analysis` | 提交/重试图片分析 |
| | GET | `/api/files/{id}/image-analysis/status` | 图片分析状态 |
| | GET | `/api/files/{id}/image-analysis` | 图片分析结果 |
| | POST | `/api/files/{id}/chunks` | 重建 RAG Chunk（幂等） |

> RAG 检索/生成（`RetrievalService`、`RagService`）、Function Calling Agent（`FunctionCallingService`）、离线检索评估（`RetrievalEvaluationService`）目前是**服务层能力，未暴露 HTTP 接口**。

### 认证说明

除注册、登录、Actuator 健康检查、Swagger、H2 控制台外，所有接口需携带 JWT：

```
Authorization: Bearer <token>
```

用户只能访问自己的任务、会话与文件（服务层归属校验，不信任客户端传入的 userId）。

### 调用示例

**注册 / 登录**

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}' | jq -r '.data.token')
```

**文件上传（自动触发解析/分析）**

```bash
# 上传 TXT/DOCX/PDF → 自动异步解析 → 自动分块
curl -X POST http://localhost:8080/api/files \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@doc.txt;type=text/plain"

# 上传图片 → 自动异步多模态分析
curl -X POST http://localhost:8080/api/files \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@img.png;type=image/png"

# 查询解析状态（PENDING / PROCESSING / SUCCESS / FAILED）
curl http://localhost:8080/api/files/1/parse-status -H "Authorization: Bearer $TOKEN"
```

**SSE 流式对话**

```bash
# 新建对话（-N 关闭缓冲）
curl -N -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"title":"演示","content":"用三句话介绍 Spring Boot"}'

# 继续对话；requestId 用于幂等（重复提交返回 409）
curl -N -X POST http://localhost:8080/api/chat/1/messages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"content":"再详细一点","requestId":"uuid-001"}'
```

SSE 事件格式（前端必须用 `fetch` + `ReadableStream`，原生 `EventSource` 不支持 POST + Header）：

- 普通文本：`data: <文本片段>`
- 正常结束：`event: done`
- 出错：`event: error` + `data: {"errorType":"rate_limit","message":"模型服务繁忙，请稍后重试","retryable":true}`

> 前端已实现完整 SSE 解析（`frontend/src/api/chat.ts`：buffer 按空行切分事件、兼容跨 chunk 与多事件、AbortController 取消），可直接参考。

## 项目结构

```
src/main/java/com/example/demo/
├── config/            # SecurityConfig / JwtUtil / AIProperties / PromptTemplate
│                      # ChatAsyncConfig / FileParseAsyncConfig / ImageAnalysisAsyncConfig
│                      # StorageProperties / MinioConfig / VectorStoreProperties
│                      # RagProperties / EmbeddingProperties / AgentProperties
├── common/            # ApiResponse / PageResponse / GlobalExceptionHandler / @CurrentUser
├── controller/        # AuthController / TaskController / ChatController / FileController
├── service/
│   ├── AI 调用        # AIService / OpenAIAIService / MockAIService
│   ├── 对话           # ChatService / ChatPersistenceService
│   ├── 文件           # FileService / FileContentValidator / StorageService
│   │                  # LocalStorageService / MinioStorageService
│   ├── 文档解析       # FileParseService / FileParseWorker / FileParsePersistenceService
│   │                  # DocumentParser + Pdf/Docx/Txt 实现 / DocumentParserRegistry
│   ├── 图片分析       # ImageAnalysisService / ImageAnalysisWorker / ImageAnalysisPersistenceService
│   │                  # MultimodalAIService + OpenAI/Mock 实现
│   ├── RAG            # ChunkingService / ChunkingStrategy / DefaultChunkingStrategy
│   │                  # EmbeddingService + OpenAI兼容/Mock 实现 / ChunkEmbeddingService
│   │                  # VectorStore / QdrantVectorStore / DocumentIndexService
│   │                  # RetrievalService / ContextAssembler / RagPromptBuilder / RagService
│   │                  # RetrievalEvaluationService（离线评估）
│   └── Token          # TokenEstimator / SimpleTokenEstimator
├── tool/              # Function Calling：Tool / ToolDefinition / ToolRegistry / ToolExecutor
│                      # ToolSchemaExporter / ToolArgs / ToolErrorCode / ToolResult
│                      # SearchKnowledgeBaseTool / QueryTaskTool / GetFileInfoTool（均只读）
├── repository/        # Task / User / AIUsageLog / Conversation / Message
│                      # File / DocumentSegment / DocumentChunk(+Source) / ImageAnalysis / DocumentIndex
├── model/             # 同名 JPA 实体
├── dto/               # 请求/响应 DTO（record 优先）
├── enums/             # TaskStatus / MessageRole / FileParseStatus / SegmentType
│                      # ImageAnalysisStatus / IndexStatus / AgentStatus
└── util/              # TextEncodingUtil（编码检测）/ ImageDimensionReader

src/main/resources/
├── application.yml / application-dev.yml / application-prod.yml
└── db/migration/{h2,mysql}/   # V1–V14

frontend/                        # Vue 3 + Vite + TS 演示前端
├── src/api/                     # http.ts（JWT 拦截器）/ chat.ts（SSE 解析）/ files.ts
├── src/views/                   # LoginView / ChatView / FilesView
└── src/components/              # ConversationSidebar / Composer / MessageItem

Dockerfile                       # multi-stage：Maven 构建 → JRE 运行
docker-compose.yml               # backend + MySQL + Qdrant + MinIO
```

## 架构设计

### 分层与权限

```
Controller（只做路由 + DTO + ApiResponse）
  → Service（业务编排；归属校验：仅比对当前 JWT 用户）
      → Repository（JPA）
      → 外部能力（AI / Storage / VectorStore，均为接口抽象 + 可替换实现）
```

### AI 服务抽象

```
AIService（接口）
  ├── OpenAIAIService   # OpenAI 兼容：analyze / complete / chatCompletion(tools) / streamAnalyze
  └── MockAIService     # 开发测试降级，确定性输出

MultimodalAIService（独立于文本，图片理解）
  ├── OpenAIMultimodalAIService
  └── MockMultimodalAIService

EmbeddingService（独立于生成能力）
  ├── OpenAICompatibleEmbeddingService
  └── MockEmbeddingService
```

### 流式对话

```
Client ──SSE──▶ ChatController ──▶ ChatService ──▶ AIService.streamAnalyze()
                                        ├── 保存 Conversation + Message（短事务）
                                        ├── Token 预算选择上下文
                                        ├── 有界线程池执行阻塞式 HTTP
                                        └── 终态保护（AtomicBoolean）+ AIUsageLog
```

### 文件 → 解析 → 分块 → 索引

```
上传（校验扩展名 + MIME + 真实特征）→ SHA-256 → storageKey → StorageService.store
  → FileRecord 入库
  → 文档：FileParseWorker（解析成 DocumentSegment：PAGE/PARAGRAPH/LINE）
        → ChunkingService（自然边界优先分块 → DocumentChunk + 来源映射）
        → DocumentIndexService（Embedding → Qdrant upsert，幂等重建）
  → 图片：ImageAnalysisWorker（多模态分析 → ImageAnalysis）
```

### RAG 检索闭环

```
query → EmbeddingService.embed → VectorStore.search（Qdrant filter: userId + indexVersion + 可选 fileIds）
  → MySQL 二次归属校验取回 Chunk → ContextAssembler（rank + token 预算 → [S1]/[S2]）
  → RagPromptBuilder（防注入指令）→ AIService.complete → sourceIds 白名单校验 → RagAnswer + Citations
```

### Function Calling Agent（受控多轮）

```
User → LLM(tools) → ToolCall → ToolExecutor（参数 + 权限校验，结构化错误码）
     → ToolResult 回填 messages → LLM → ... → Final Answer
边界：maxRounds / 总超时 / 重复 Tool+参数检测 / 安全终止状态；Execution Trace 全程记录
当前仅 3 个只读工具（searchKnowledgeBase / queryTask / getFileInfo），无写操作
```

### JWT 认证

```
POST /api/auth/login → JWT
  → 后续请求 Header: Authorization: Bearer <token>
  → JwtAuthenticationFilter → SecurityContext → @CurrentUser 注入 User
```

## 配置说明

| 配置项 | 说明 |
|--------|------|
| `ai.provider` | `mock` / `openai` |
| `ai.openai.api-key` | 环境变量 `DASHSCOPE_API_KEY` |
| `ai.openai.base-url` / `model` | OpenAI 兼容地址与 chat 模型 |
| `ai.connect-timeout` / `read-timeout` / `max-retries` | 连接与重试 |
| `ai.chat.*` | 聊天 system prompt / 版本 / Token 预算 / 消息长度上限 |
| `chat.executor.*` | 聊天流式有界线程池 |
| `file-parse.*` | 文档解析线程池（core/max/queue） |
| `image-analysis.*` | 图片分析：enabled / model / max-tokens / 线程池 |
| `storage.type` | `local` / `minio` |
| `storage.local.root-path` | 本地存储根目录 |
| `storage.minio.endpoint/access-key/secret-key/bucket` | MinIO（密钥走环境变量） |
| `storage.max-file-size` | 单文件上限（与 `spring.servlet.multipart` 一致） |
| `embedding.provider/model/dimension/batch-size/max-retries` | Embedding（`dimension` 为期望响应维度，需与 Qdrant collection 一致） |
| `vector-store.type` / `qdrant.host/port/collection` | 向量存储（Qdrant） |
| `rag.chunk.max-chars` / `overlap-chars` | 分块参数（`overlap < max-chars`） |
| `rag.index.version` | 索引版本（写入 payload，重建/隔离用） |
| `rag.retrieval.top-k` / `score-threshold` | 检索参数 |
| `rag.context.max-context-tokens` | 送入模型的上下文 Token 预算 |
| `agent.max-rounds` / `timeout` | Agent 循环边界 |

## 测试

```bash
./mvnw test                      # 全量
./mvnw test -Dtest=FileServiceTest   # 单个测试类
```

当前 **258 个测试全部通过**，覆盖：任务/认证、SSE 流式与终态保护、文件上传校验与存储、文档解析与分块、Embedding 与向量检索、RAG 组装与 Citation 校验、Tool 执行与 Agent 边界、离线检索评估指标。

## License

MIT
