# 学习路线图 — AI 应用开发

> 以 Spring Boot 3 为后端底座，逐步构建一个 AI 应用开发平台。
> 每个 Phase 是一个可独立完成的里程碑，顺序推进。

## Phase 0 — 基础搭建 ✅

Spring Boot 3.4.3 + JDK 17 + Maven + H2 + JPA

- Task CRUD（创建/查询/删除）
- 模拟 AI 分析结果（mock-result 端点）
- 统一响应体 `ApiResponse<T>`
- 全局异常处理 `GlobalExceptionHandler`
- 参数校验 `@Valid`
- 单元测试

## Phase 1 — 数据库升级 & 事务管理

- MySQL 驱动，`application.yml` 多环境配置（dev/prod）
- Flyway 数据库版本迁移
- `@Transactional` 事务边界、传播机制、回滚策略
- 分页查询 + 自定义 `@Query` 搜索过滤

## Phase 2 — 安全认证 & 多用户

- `User` 实体，User 与 Task 的 `@ManyToOne` 关联
- Spring Security `SecurityFilterChain` + `PasswordEncoder`
- JWT 工具类（生成 / 校验 / 解析）
- 注册 + 登录接口
- `@CurrentUser` 自定义注解，资源归属校验

## Phase 3 — API 文档 & 接口规范

- SpringDoc OpenAPI（Swagger UI）
- `@Operation` / `@Schema` 注解补全
- 统一分页响应格式 `PageResponse<T>`

## Phase 4 — 接入大模型 API

- 抽象 `AIService` 接口（`OpenAIAIService` / `MockAIService`）
- 用 `@ConditionalOnProperty` 在 mock 和真实调用之间切换
- Prompt 模板引擎，`{inputText}` 占位替换
- `AIUsageLog` 记录 token 消耗和耗时

## Phase 5 — 流式对话 & SSE

- SSE（Server-Sent Events）+ `WebClient` 流式请求
- 对话历史存储：`Conversation` + `Message`（`@OneToMany`）
- 多轮对话上下文

## Phase 6 — 文件处理 & 对象存储

- `MultipartFile` 上传，类型校验、大小限制
- MinIO / 本地存储
- 多模态接口（图片理解）
- `@Async` + 线程池异步解析文档（PDF/Word/TXT）

## Phase 7 — RAG（检索增强生成）

- 文档分块策略
- Embedding 生成
- 向量数据库（Milvus / Qdrant / pgvector）
- 检索 + 生成流程编排，引文追溯

## Phase 8 — 异步任务 & 消息队列

- RabbitMQ / Kafka
- 提交任务 → 返回 taskId → 异步处理 → 通知完成
- `@EventListener` 事件驱动
- 重试 + 死信队列

## Phase 9 — 智能体（Agent）& 工具调用

- Agent 基础架构：LLM + Tools + Memory + Planner
- Function Calling
- 实现 3+ 工具：搜索网页、查询数据库、调用外部 API
- Agent 循环：思考 → 行动 → 观察 → 继续

## Phase 10 — 可观测性 & 生产就绪

- Actuator + Micrometer + Prometheus
- 自定义 Metrics：AI 调用 QPS、P99 延迟、错误率
- AOP 切面操作日志（`@OperationLog`）
- 全局限流（Bucket4j / Sentinel）
- Docker + docker-compose
- GitHub Actions CI

---

## 使用方式

每个 Phase 开始时告诉我「开始 Phase N」，我会：
1. 讲解该阶段涉及的知识点
2. 一起设计 API 和数据模型
3. 分步实现，每步验证
4. Phase 完成后总结学到的东西
