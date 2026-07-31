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
| Spring WebFlux | SSE 流式响应 |
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
| | POST | `/api/tasks/{id}/analyze` | AI 分析任务 |
| **对话** | POST | `/api/chat/stream` | SSE 流式对话 |
| | GET | `/api/chat/conversations` | 查询会话列表 |
| | GET | `/api/chat/conversations/{id}/messages` | 查询会话历史 |
| | DELETE | `/api/chat/conversations/{id}``` | 删除会话 |
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
curl -X POST http://localhost:8080/api/tasks/1/analyze \
  -H "Authorization: Bearer $TOKEN"
```

**SSE 流式对话**
```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"message":"用三句话介绍 Spring Boot"}'
```

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
│   └── ChatService.java         # 会话与流式对话
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

## License

MIT
