# CLAUDE.md — AI 应用开发学习项目

## 项目定位

以 Spring Boot 3 为后端底座，逐步构建一个 **AI 应用开发平台**，最终形态是一个能对接大模型、具备 RAG、智能体、流式对话等能力的生产级项目。每一步都是「可运行 + 可面试展示」的增量。

**目标岗位**：AI 应用开发实习（Java / Spring Boot 方向）

---

## 当前状态（Phase 0 — 已完成）

```
Spring Boot 3.4.3 + JDK 17 + Maven
├── H2 内存数据库 + JPA
├── Task CRUD（创建/查询/删除）
├── 模拟 AI 分析结果（mock-result 端点）
├── 统一响应体 ApiResponse<T>
├── 全局异常处理 GlobalExceptionHandler
├── 参数校验 @Valid
└── 单元测试 TaskServiceTest
```

核心知识点已覆盖：IoC/DI、RESTful API、JPA Repository、@Entity、DTO 分层、异常处理、Validation。

---

## 学习路线图

每个 Phase 代表一个可独立完成的里程碑，顺序推进，后面会依赖前面的基础。

### Phase 1：数据库升级 & 事务管理

> 目标：把 H2 换成 MySQL，学会多环境配置和事务

- [ ] 引入 MySQL 驱动，`application.yml` 多环境配置（dev/prod）
- [ ] 用 Flyway 做数据库版本迁移（替代 `ddl-auto: update`）
- [ ] `@Transactional` 事务边界、传播机制、回滚策略
- [ ] 任务分页查询（`Pageable` + `@Query` 自定义查询）
- [ ] 增加搜索过滤：按状态、类型、时间范围筛选

### Phase 2：安全认证 & 多用户

> 目标：接入 Spring Security + JWT，支持用户注册登录

- [ ] `User` 实体 + `UserRepository`，User 与 Task 建立 `@ManyToOne` 关联
- [ ] Spring Security 配置：`SecurityFilterChain` + `PasswordEncoder`
- [ ] JWT 工具类（生成 / 校验 / 解析），无状态会话
- [ ] 注册 + 登录接口（`/api/auth/register`、`/api/auth/login`）
- [ ] `@CurrentUser` 自定义注解，从 Token 注入当前用户
- [ ] 用户只能操作自己的任务（资源归属校验）

### Phase 3：API 文档 & 接口规范

> 目标：用 OpenAPI 生成可交互的接口文档

- [ ] 引入 SpringDoc OpenAPI（Swagger UI 替代品）
- [ ] 为 Controller 和 DTO 补 `@Operation` / `@Schema` 注解
- [ ] 配置 `application.yml` 中的 Swagger 路径和分组
- [ ] 统一分页响应格式 `PageResponse<T>`

### Phase 4：接入大模型 API

> 目标：把 mock-result 升级为真正调用大模型

- [ ] 引入 Spring AI 或自封装 HTTP Client 调用 OpenAI 兼容 API
- [ ] 抽象 `AIService` 接口，实现 `OpenAIAIService` / `MockAIService`
- [ ] 用 `@ConditionalOnProperty` 在 mock 和真实调用之间切换
- [ ] Prompt 模板引擎：支持用户自定义 prompt，`{inputText}` 占位替换
- [ ] 记录每次 AI 调用的 token 消耗和耗时（`AIUsageLog` 实体）

### Phase 5：流式对话 & SSE

> 目标：实现 ChatGPT 式的逐字输出

- [ ] Spring Boot SSE（Server-Sent Events）+ `WebClient` 流式请求
- [ ] 前端用 EventSource / fetch readable stream 接收
- [ ] 对话历史存储：`Conversation` + `Message` 实体（`@OneToMany`）
- [ ] 支持多轮对话上下文

### Phase 6：文件处理 & 对象存储

> 目标：支持上传文档/图片，交给 AI 处理

- [ ] Spring Boot 文件上传（`MultipartFile`）
- [ ] 文件类型校验、大小限制、安全过滤
- [ ] 引入 MinIO / 本地存储，返回可访问 URL
- [ ] 对接大模型的多模态接口（图片理解）
- [ ] 异步解析文件内容（PDF/Word/TXT），用 `@Async` + 线程池

### Phase 7：RAG（检索增强生成）

> 目标：让 AI 能基于私有文档回答问题

- [ ] 文档分块策略（按段落 / 固定长度 / 滑动窗口）
- [ ] Embedding 生成（调用 text-embedding API）
- [ ] 接入向量数据库（Milvus / Qdrant / pgvector）
- [ ] 检索 + 生成流程编排：query → embed → search → prompt assemble → LLM
- [ ] 引文追溯：返回结果时标注引用来源

### Phase 8：异步任务 & 消息队列

> 目标：长时间 AI 任务不阻塞 HTTP 请求

- [ ] 引入 RabbitMQ / Kafka
- [ ] 「提交任务 → 返回 taskId → 异步处理 → 轮询/WebSocket 通知完成」全链路
- [ ] `@EventListener` 事件驱动：任务状态变更时发送通知
- [ ] 任务重试 + 死信队列

### Phase 9：智能体（Agent）& 工具调用

> 目标：实现能自动调用工具的 AI Agent

- [ ] Agent 基础架构：LLM + Tools + Memory + Planner
- [ ] Function Calling：让 LLM 决定调用哪个后端接口
- [ ] 实现 3+ 工具：搜索网页、查询数据库、调用外部 API
- [ ] Agent 循环：思考 → 行动 → 观察 → 继续，直到完成

### Phase 10：可观测性 & 生产就绪

> 目标：面试能聊「你项目怎么运维的」

- [ ] Spring Boot Actuator + Micrometer + Prometheus 指标暴露
- [ ] 自定义 Metrics：AI 调用 QPS、延迟 P99、错误率
- [ ] AOP 切面实现操作日志（`@OperationLog` 注解）
- [ ] 全局限流（Bucket4j / Sentinel）
- [ ] Docker 容器化 + docker-compose 一键启动全家桶
- [ ] GitHub Actions CI：自动跑测试

---

## 项目分层规范

```
src/main/java/com/example/demo/
├── config/          # @Configuration 配置类（Security、CORS、线程池等）
├── common/          # 全局通用组件（ApiResponse、异常处理、AOP 切面）
├── controller/      # 控制器 —— 只做参数校验和路由，不写业务逻辑
├── service/         # 业务逻辑层 —— 编排 repository + 外部服务
├── repository/      # 数据访问 —— 只定义接口，不写实现
├── model/ 或 entity/  # JPA 实体 —— 纯数据载体 + 关联映射
├── dto/             # 请求/响应 DTO —— Controller 入参和出参
├── enums/           # 枚举
├── exception/       # 自定义异常类
└── util/            # 工具类
```

**铁律**：
- Controller 不写业务逻辑，只调 Service
- Service 不操作 HttpServletRequest/Response，只处理业务
- Repository 只定义接口，不加任何业务注解
- 数据库实体（Entity）不要直接暴露到 Controller 的返回值

---

## 编码约定

- Java 17：能用 `record` 替代简单 DTO 就用 record
- 构造器注入替代 `@Autowired` 字段注入
- `@Valid` / `@Validated` 做参数校验，不在 Controller 里手写 if
- 异常用全局 `@RestControllerAdvice` 统一处理，不在业务代码中 try-catch 返回
- 日志用 `LoggerFactory.getLogger`（Lombok 团队不推荐 `@Slf4j`，保持原生）
- 测试类命名 `XxxTest`，用 `@MockBean` mock 外部依赖

---

## 使用方式

每个 Phase 开始时：
1. 告诉我「开始 Phase N」，我会解释涉及的知识点
2. 一起设计该 Phase 的 API 和数据模型
3. 分步实现，每步写测试验证
4. Phase 完成后我会总结你学到了什么

> 这个 CLAUDE.md 会随项目演进持续更新。
