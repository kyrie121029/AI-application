
# AI-application

基于 Spring Boot 3 的 AI 应用开发平台，支持任务管理、用户认证、大模型调用、流式对话等能力。

## 技术栈

| 技术 | 说明 |
|------|------|
| Java 17 | 支持 record、文本块等现代语法 |
| Spring Boot 3.4.3 | 核心框架 |
| Spring Security + JWT | 无状态认证鉴权 |
| Spring Data JPA | ORM 持久化 |
| MySQL / H2 | 生产 / 开发数据库 |
| Flyway | 数据库版本迁移 |
| SpringDoc OpenAPI | Swagger UI 接口文档 |
| Spring MVC + SseEmitter | SSE 流式响应 |
| Maven | 构建工具 |

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

默认使用 `dev` profile，启动 H2 内存数据库，无需安装 MySQL。

### 切换 MySQL

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

通过 `application-prod.yml` 配置 MySQL 连接信息。

### Docker 运行（可选）

```bash
# 1. 构建镜像（multi-stage：Maven 打包 → JRE 运行）
docker build -t ai-application .

# 2. 运行容器（默认 dev profile：H2 内存库，无需 MySQL）
docker run -d --name ai-app -p 8080:8080 \
  -e JAVA_OPTS="-Xmx512m" \
  ai-application

# 3. 访问
#    API / Swagger: http://localhost:8080/swagger-ui.html
#    H2 控制台:     http://localhost:8080/h2-console

# 覆盖存储类型（如切 MinIO / MySQL）通过环境变量注入：
#   -e SPRING_PROFILES_ACTIVE=prod \
#   -e DASHSCOPE_API_KEY=... \
#   -e MINIO_ACCESS_KEY=... -e MINIO_SECRET_KEY=...
```

> 本地文件上传写入容器内 `/app/data/uploads`；持久化需挂载卷：`-v ai_uploads:/app/data/uploads`。

### Docker Compose 一键部署（backend + MySQL + Qdrant + MinIO）

```bash
# 1. 准备配置（敏感值，不入 Git）
cp .env.example .env

# 2. 构建并启动（backend 用 Dockerfile 构建；MySQL/Qdrant/MinIO 用官方镜像）
docker compose up --build -d

# 3. 验证
curl http://localhost:8080/actuator/health     # {"status":"UP"}
docker compose ps

# 访问
#   Swagger: http://localhost:8080/swagger-ui.html
#   MinIO Console: http://localhost:9001
#   Qdrant Dashboard: http://localhost:6333/dashboard

# 4. 停止（加 -v 会清空数据卷）
docker compose down
```

- backend 通过 service name 连接 `mysql:3306` / `qdrant:6333` / `minio:9000`。
- 默认 `AI_PROVIDER=mock`（无需 API Key）；改 `.env` 为 `openai` + `DASHSCOPE_API_KEY` 即接真实模型。
- 数据持久化：MySQL/Qdrant/MinIO 与后端上传目录均挂命名卷。

### H2 控制台

`http://localhost:8080/h2-console`

- **JDBC URL**: `jdbc:h2:mem:taskdb`
- **用户名**: `sa`
- **密码**: （留空）

## API 概览

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| **认证** | POST | `/api/auth/register` | 用户注册 |
| | POST | `/api/auth/login` | 用户登录，返回 JWT |
| **任务** | POST | `/api/tasks` | 创建任务 |
| | GET | `/api/tasks` | 分页查询任务（支持状态/类型/关键词过滤） |
| | GET | `/api/tasks/{id}` | 查询单个任务 |
| | DELETE | `/api/tasks/{id}` | 删除任务 |
| | POST | `/api/tasks/{id}/generate` | AI 分析任务 |
| **对话** | POST | `/api/chat` | 新建对话（SSE 流式） |
| | POST | `/api/chat/{id}/messages` | 继续对话（SSE 流式） |
| | GET | `/api/chat` | 查询会话列表 |
| | GET | `/api/chat/{id}/messages` | 查询会话历史 |
| | DELETE | `/api/chat/{id}` | 删除会话 |
| **文档** | — | `/swagger-ui.html` | Swagger UI 交互文档 |

### 认证说明

除注册和登录外，所有接口需携带 JWT Token：

```
Authorization: Bearer <token>
```

用户只能操作自己的任务和会话（资源归属校验）。

### 调用示例

**注册**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'
```

**创建任务 + AI 分析**
```bash
# 先登录获取 token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}' | jq -r '.data.token')

# 创建任务
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"title":"分析客户反馈","taskType":"文本分析","inputText":"产品质量不错，但发货速度太慢了"}'

# AI 分析
curl -X POST http://localhost:8080/api/tasks/1/generate \
  -H "Authorization: Bearer $TOKEN"
```

**SSE 流式对话（curl）**

```bash
# 新建对话：逐字接收流式回复
curl -N -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"title":"演示","content":"用三句话介绍 Spring Boot"}'

# 继续对话（POST 流式，不能使用浏览器原生 EventSource）
curl -N -X POST http://localhost:8080/api/chat/1/messages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"content":"再详细一点"}'

# 幂等：携带 requestId 防重复提交
curl -N -X POST http://localhost:8080/api/chat/1/messages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"content":"你好","requestId":"uuid-001"}'
```

**SSE 流式对话（浏览器 fetch + ReadableStream）**

由于流式接口是 POST，浏览器原生 `EventSource` 只支持 GET，必须用 `fetch` + `ReadableStream`：

```html
<!DOCTYPE html>
<html lang="zh">
<body>
  <pre id="out"></pre>
  <script>
    const token = "粘贴你的 JWT";
    const convId = 1; // 已有会话 ID

    async function chat() {
      const resp = await fetch("http://localhost:8080/api/chat/" + convId + "/messages", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": "Bearer " + token
        },
        body: JSON.stringify({ content: "你好" })
      });

      const reader = resp.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        // 逐条解析 SSE 事件（以空行分隔）
        const events = buffer.split("\n\n");
        buffer = events.pop();
        for (const evt of events) {
          const dataLine = evt.split("\n").find(l => l.startsWith("data: "));
          if (!dataLine) continue;
          const data = dataLine.slice(6);
          if (evt.includes("event: error")) {
            document.getElementById("out").textContent += "\n[错误] " + data + "\n";
          } else if (evt.includes("event: done")) {
            document.getElementById("out").textContent += "\n[完成]\n";
          } else {
            document.getElementById("out").textContent += data;
          }
        }
      }
    }
    chat();
  </script>
</body>
</html>
```

SSE 事件格式：

- 普通文本：`data: <文本片段>`
- 正常结束：`event: done`
- 出错：`event: error` + `data: {"errorType":"rate_limit","message":"模型服务繁忙，请稍后重试","retryable":true}`

## 项目结构

```
src/main/java/com/example/demo/
├── DemoApplication.java         # 启动类
├── config/                      # 配置类
│   ├── SecurityConfig.java      # Spring Security 配置
│   ├── JwtUtil.java             # JWT 工具类
│   ├── JwtAuthenticationFilter.java  # JWT 认证过滤器
│   ├── AIProperties.java        # AI 服务配置绑定
│   ├── PromptTemplate.java      # Prompt 模板引擎
│   ├── ChatAsyncConfig.java     # 聊天流式线程池（有界）
│   ├── OpenApiConfig.java       # Swagger 配置
│   └── WebConfig.java           # Web/CORS 配置
├── common/                      # 公共组件
│   ├── ApiResponse.java         # 统一响应体
│   ├── PageResponse.java        # 分页响应
│   ├── GlobalExceptionHandler.java  # 全局异常处理
│   ├── CurrentUser.java         # @CurrentUser 注解
│   └── CurrentUserResolver.java # 当前用户参数解析器
├── controller/                  # 控制器
│   ├── AuthController.java      # 注册/登录
│   ├── TaskController.java      # 任务 CRUD + AI 分析
│   └── ChatController.java      # SSE 流式对话
├── service/                     # 业务逻辑
│   ├── TaskService.java         # 任务管理
│   ├── AIService.java           # AI 服务接口（抽象层）
│   ├── OpenAIAIService.java     # OpenAI 兼容实现
│   ├── MockAIService.java       # Mock 实现（开发用）
│   ├── ChatService.java         # 会话与流式对话编排
│   ├── ChatPersistenceService.java  # 对话持久化（独立短事务）
│   ├── TokenEstimator.java      # Token 估算接口
│   └── SimpleTokenEstimator.java    # 近似 Token 估算实现
├── repository/                  # JPA 数据访问
│   ├── TaskRepository.java
│   ├── UserRepository.java
│   ├── AIUsageLogRepository.java
│   ├── ConversationRepository.java
│   └── MessageRepository.java
├── model/                       # JPA 实体
│   ├── Task.java                # 任务
│   ├── User.java                # 用户
│   ├── AIUsageLog.java          # AI 调用日志
│   ├── Conversation.java        # 对话会话
│   └── Message.java             # 对话消息
├── dto/                         # 请求/响应 DTO
│   ├── CreateTaskRequest.java / CreateTaskResponse.java
│   ├── RegisterRequest.java / LoginRequest.java / LoginResponse.java
│   ├── ChatRequest.java / MessageResponse.java / ConversationResponse.java
│   └── AIResult.java / MockResultResponse.java / TaskResponse.java
├── enums/
│   ├── TaskStatus.java          # PENDING / PROCESSING / RESULT_GENERATED
│   └── MessageRole.java         # USER / ASSISTANT / SYSTEM
└── exception/                   # 自定义异常
    ├── TaskNotFoundException.java
    ├── ConversationNotFoundException.java
    ├── DuplicateRequestException.java
    ├── AIServiceException.java
    ├── AIResponseParseException.java
    └── ForbiddenException.java

src/main/resources/
├── application.yml              # 通用配置
├── application-dev.yml          # 开发环境（H2 + Mock AI）
├── application-prod.yml         # 生产环境（MySQL + 真实 AI）
└── db/migration/                # Flyway 迁移脚本
    ├── h2/                      # H2 专用
    └── mysql/                   # MySQL 专用
```

## 架构设计

### AI 服务抽象

```
Controller → AIService (接口)
                ├── OpenAIAIService  （真实模型调用）
                └── MockAIService    （开发/测试降级）
```

通过 `application.yml` 中的 `ai.provider` 切换：
- `mock`：返回模拟数据，无需 API Key
- `openai`：调用 OpenAI 兼容 API，需设置 `DASHSCOPE_API_KEY` 环境变量

### 流式对话流程

```
Client ──SSE──▶ ChatController ──▶ ChatService ──▶ AIService.stream()
                                         │
                                         ├── 保存 Conversation + Message
                                         ├── 记录 AIUsageLog（token/耗时）
                                         └── 逐块推送 SSE 事件
```

### JWT 认证流程

```
Client ──POST /api/auth/login──▶ 返回 JWT Token
  │
  └── 后续请求 Header: Authorization: Bearer <token>
        │
        └── JwtAuthenticationFilter ──▶ SecurityContext
                                          │
                                          └── @CurrentUser 注入当前用户
```

## 配置说明

| 配置项 | 说明 |
|--------|------|
| `ai.provider` | `mock` / `openai` 切换 |
| `ai.openai.api-key` | 从环境变量 `DASHSCOPE_API_KEY` 读取 |
| `ai.openai.base-url` | OpenAI 兼容 API 地址 |
| `ai.connect-timeout` | 连接超时（默认 10s） |
| `ai.read-timeout` | 读取超时（默认 60s） |
| `ai.max-retries` | 失败重试次数 |
| `ai.chat.prompt-version` | 聊天 System Prompt 版本号 |
| `ai.chat.max-input-tokens` | 上下文总 Token 预算（system + 当前用户 + 历史；默认 4000） |
| `ai.chat.max-output-tokens` | 模型输出 Token 预留（从总预算中扣除；默认 1000） |
| `chat.executor.core-pool-size` | 聊天流式线程池核心线程数 |
| `chat.executor.max-pool-size` | 聊天流式线程池最大线程数 |
| `chat.executor.queue-capacity` | 聊天流式线程池队列容量（有界） |

## License

MIT
