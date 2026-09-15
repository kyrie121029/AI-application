可以。下面这版我按你原来的 `ROADMAP.md` 风格重新整理：保留“每个 Phase 有目标、任务列表、完成标准”的结构，不把内容压缩成简单提纲；同时按照你当前真实开发进度重新编号，后面从 Phase 8 开始连续推进，不再出现 10A、10B 这种跳号。原路线中的数据库、安全、模型接入、会话、文件、RAG、MQ、Agent、可观测性和部署主线都保留，只调整了后半程优先级。

你可以直接整体复制覆盖原来的 `ROADMAP.md`。

````markdown
# 学习路线图 — AI 应用开发

> 以 Spring Boot 3 为后端底座，逐步构建一个面向多用户的 AI 知识库与智能工具调用平台。
>
> 项目以 AI 应用开发实习为主要目标，因此后续开发不单纯追求功能数量，而是重点建立：
>
> **后端工程基础 → 大模型调用 → Context Engineering → 文件处理 → RAG → 部署 → Agent → 异步任务 → 可观测性与项目交付**
>
> 每个 Phase 是一个相对独立的里程碑，后续阶段建立在前面的基础之上。

---

## 当前项目定位

### 基于 Spring Boot 的多用户 AI 知识库与智能工具调用平台

项目核心链路：

```text
用户注册 / 登录
        ↓
Spring Security + JWT
        ↓
多用户资源权限隔离
        ↓
文件上传
        ↓
Local / MinIO 对象存储
        ↓
异步文档解析
        ↓
DocumentSegment
        ↓
DocumentChunk
        ↓
Embedding
        ↓
Qdrant Vector Index
        ↓
权限感知 Retrieval
        ↓
Context Assembly
        ↓
Grounded Prompt
        ↓
LLM Generation
        ↓
Citation
        ↓
Function Calling / Agent
````

项目重点不是简单调用一次大模型 API，而是完整学习 AI 应用开发中的：

* Spring Boot 后端工程
* 用户认证与数据权限
* 大模型 API 抽象
* Prompt Engineering
* Structured Output
* Streaming
* Context Engineering
* 文件处理
* Embedding
* Vector Database
* RAG
* Citation
* Function Calling
* Agent Control Flow
* Retry / Timeout / Error Handling
* Async Task
* Message Queue
* Observability
* Docker Deployment
* CI

---

# Phase 0：Spring Boot 基础 — 已完成

> 目标：建立基础 Spring Boot Web 项目，理解后端应用的基本分层和调用链。

* [x] Spring Boot 3.4.3 + JDK 17 + Maven
* [x] H2 / JPA 基础
* [x] Task CRUD
* [x] Repository 数据访问
* [x] Service 业务层
* [x] Controller 接口层
* [x] DTO 基础分层
* [x] 统一响应体 `ApiResponse<T>`
* [x] 全局异常处理 `GlobalExceptionHandler`
* [x] 参数校验 `@Valid`
* [x] 基础单元测试

核心知识点：

* IoC / DI
* RESTful API
* Controller / Service / Repository 分层
* JPA Repository
* `@Entity`
* DTO
* Validation
* Exception Handling
* Dependency Injection

### 完成标准

* 能独立创建 Spring Boot REST API
* 能完成基本 CRUD
* 能解释 Controller、Service、Repository 的职责
* 能使用统一异常处理和参数校验

---

# Phase 1：数据库升级与事务管理 — 已完成

> 目标：从开发阶段的简单数据库升级为 MySQL，并学习真实业务项目中的数据库版本管理和事务边界。

* [x] 引入 MySQL Driver
* [x] 使用 MySQL 替代 H2
* [x] 配置 `application.yml`
* [x] 区分不同环境配置
* [x] 使用 Flyway 管理数据库 Schema
* [x] 禁止依赖 `ddl-auto: update` 管理生产表结构
* [x] 学习 `@Transactional`
* [x] 明确事务边界
* [x] 理解事务回滚
* [x] 实现分页查询 `Pageable`
* [x] 使用 Repository 自定义查询
* [x] 支持基础状态和条件过滤

核心知识点：

* MySQL
* Flyway
* Transaction
* Transaction Boundary
* Rollback
* JPA
* Pagination
* Database Migration

### 完成标准

* 应用可以使用 MySQL 正常启动
* Flyway 可以自动完成数据库迁移
* 不依赖自动建表维护数据库结构
* 能解释为什么事务通常放在 Service 层
* 能完成分页和基础条件查询

---

# Phase 2：安全认证、权限控制与多用户 — 已完成

> 目标：接入 Spring Security + JWT，实现用户认证、资源隔离和多用户权限控制。

* [x] 建立 `User` 实体
* [x] `UserRepository`
* [x] User 与 Task 建立关系
* [x] 使用 `PasswordEncoder` 保存密码
* [x] 禁止明文密码
* [x] 配置 `SecurityFilterChain`
* [x] 区分公开接口和受保护接口
* [x] 实现 JWT 生成
* [x] JWT 校验
* [x] JWT 过期判断
* [x] JWT 用户信息解析
* [x] 实现 JWT Authentication Filter
* [x] 将认证信息写入 `SecurityContext`
* [x] 注册接口
* [x] 登录接口
* [x] 统一处理 401
* [x] 统一处理 403
* [x] 实现统一当前用户获取方式
* [x] 用户只能访问自己的资源
* [x] 禁止直接使用前端传入的 userId 判断权限
* [x] 增加数据库层资源归属校验

核心调用链：

```text
HTTP Request
↓
Security Filter Chain
↓
JWT Authentication Filter
↓
JWT Validation
↓
SecurityContext
↓
Controller
↓
Service
↓
Owner Validation
↓
Repository
```

核心知识点：

* Authentication
* Authorization
* JWT
* SecurityContext
* Filter
* Resource Ownership
* 401 / 403
* Defense in Depth

### 完成标准

* 用户可以注册和登录
* Token 可以正确认证
* Token 失效后返回 401
* 用户无法访问其他用户的数据
* 后端不依赖前端传入 userId 做权限判断

---

# Phase 3：API 文档与接口规范 — 已完成

> 目标：建立规范、可维护、可交互测试的 REST API。

* [x] 引入 SpringDoc OpenAPI
* [x] Swagger UI
* [x] Controller 使用 `@Operation`
* [x] DTO 使用 `@Schema`
* [x] 统一分页响应 `PageResponse<T>`
* [x] 统一成功响应
* [x] 统一错误响应
* [x] 设计错误码
* [x] API Version，例如 `/api/v1/...`
* [x] 明确 DTO / Entity / VO 职责
* [x] Controller 不直接返回 Entity

核心知识点：

* OpenAPI
* Swagger
* API Contract
* DTO
* Entity
* VO
* Error Code
* API Versioning

### 完成标准

* Swagger 可以完成主要接口调用
* 每个核心接口具有清晰的请求和响应说明
* Controller 不直接暴露数据库 Entity
* API 返回结构基本统一

---

# Phase 4：大模型 API、Prompt 与 Structured Output — 已完成核心能力

> 目标：将简单 Mock AI 升级为稳定、可测试、可替换的大模型调用模块。

## 4.1 模型接入与抽象

* [x] 抽象 `AIService`
* [x] 实现 `OpenAIAIService`
* [x] 实现 `MockAIService`
* [x] 使用 `@ConditionalOnProperty` 切换 Provider
* [x] 支持 OpenAI-compatible API
* [x] Base URL 配置化
* [x] API Key 配置化
* [x] Model Name 配置化
* [x] Timeout 配置化
* [x] API Key 通过环境变量注入
* [x] 禁止真实 API Key 提交 Git

调用关系：

```text
Business Service
↓
AIService
↓
OpenAIAIService / MockAIService
↓
Model Provider
```

---

## 4.2 Prompt Engineering

* [x] 区分 System Prompt
* [x] 区分 User Prompt
* [x] 区分 Business Context
* [x] Prompt 从 Controller 中抽离
* [x] Prompt 从业务 Service 中适当解耦
* [x] Prompt Template
* [x] Prompt Version
* [x] 支持动态业务变量注入
* [x] 理解 Few-shot 的使用场景
* [x] 理解简单任务不应滥用 CoT

核心思想：

```text
System Prompt
→ 行为和规则

Business Context
→ 动态业务数据

User Prompt
→ 当前用户请求
```

---

## 4.3 Structured Output

* [x] 定义结构化 DTO
* [x] 要求模型输出 JSON
* [x] 使用 Jackson 解析
* [x] 校验必填字段
* [x] 校验字段类型
* [x] 处理非法 JSON
* [x] 输出解析异常分类
* [x] 禁止通过正则表达式直接切割自然语言结果

当前主要实现：

```text
Prompt-enforced JSON
+
Application-side Strict Validation
```

后续可增强：

* [ ] Provider-native Structured Output
* [ ] JSON Schema
* [ ] `response_format`
* [ ] Constrained Decoding

---

## 4.4 稳定性与异常处理

* [x] Connect Timeout
* [x] Read Timeout
* [x] 网络异常分类
* [x] Rate Limit
* [x] Authentication Error
* [x] Bad Request
* [x] Upstream Server Error
* [x] Retryable Error
* [x] Non-retryable Error
* [x] 有限重试
* [x] 指数退避
* [x] 禁止所有异常无限重试
* [x] Mock Provider 支持测试和本地开发

---

## 4.5 AI Usage Log

* [x] 建立 `AIUsageLog`
* [x] Model Name
* [x] Provider
* [x] Request Time
* [x] Latency
* [x] Input Token
* [x] Output Token
* [x] Error Type
* [x] Prompt Version
* [x] 用户 / Task / Conversation 基础关联
* [x] 避免记录 API Key 等敏感信息

### 完成标准

* Mock 和真实模型可以通过配置切换
* AIService 不绑定单一模型厂商
* 非法返回能够被识别
* 网络错误和鉴权错误具有不同处理策略
* Retry 有明确边界
* 模型调用具备基础使用记录

---

# Phase 5：流式对话、会话管理与 Context Engineering — 已完成

> 目标：实现 ChatGPT 式流式响应，并正确管理多轮会话和模型上下文。

## 5.1 SSE Streaming

* [x] 使用 SSE 返回流式响应
* [x] 支持模型 Streaming
* [x] 定义流式事件
* [x] 定义结束事件
* [x] 定义异常事件
* [x] 处理客户端中途断开
* [x] 处理模型生成超时
* [x] 使用独立有界线程池
* [x] 区分同步结构化接口和流式自然语言接口

---

## 5.2 Conversation 与 Message

* [x] `Conversation`
* [x] `Message`
* [x] role
* [x] content
* [x] createdAt
* [x] tokenCount 基础支持
* [x] conversationId
* [x] 会话资源归属
* [x] 创建会话
* [x] 发送消息
* [x] 查询历史
* [x] 删除会话

---

## 5.3 Context Engineering

* [x] 区分数据库完整历史和实际模型上下文
* [x] 最近 N 条消息
* [x] 最近 N 轮消息
* [x] 为 System Prompt 预留 Token
* [x] 为 User Input 预留 Token
* [x] 为 Model Output 预留 Token
* [x] Token Budget
* [x] History Trimming
* [x] 早期消息摘要压缩思路
* [x] 无关历史过滤思路

核心思想：

```text
Conversation History
≠
Model Context
```

### 完成标准

* 支持多轮会话
* 会话之间互相隔离
* 不会简单把所有历史无限发送给模型
* 能解释 Context Window 和 Token Budget

---

# Phase 6：文件上传、对象存储与文档解析 — 已完成

> 目标：建立安全、可追踪的文件处理 Pipeline，为 RAG 和多模态能力提供数据基础。

## 6.1 文件上传

* [x] `MultipartFile`
* [x] 文件大小限制
* [x] 文件数量限制基础
* [x] Extension 校验
* [x] Content-Type 校验
* [x] 文件真实类型基础校验
* [x] 文件重命名
* [x] 防止路径穿越
* [x] File Hash
* [x] FileRecord

---

## 6.2 文件存储

* [x] StorageService 抽象
* [x] Local Storage
* [x] MinIO
* [x] 数据库仅保存对象位置和元数据
* [x] 用户文件权限校验
* [x] 理解公开 URL 和 Signed URL

核心关系：

```text
Database
→ File Metadata

Object Storage
→ File Binary
```

---

## 6.3 文档解析

* [x] PDF
* [x] DOCX
* [x] TXT
* [x] 文档异步处理
* [x] 独立线程池
* [x] PENDING
* [x] PROCESSING
* [x] SUCCESS
* [x] FAILED
* [x] 保存失败原因
* [x] Retry
* [x] `DocumentSegment`
* [x] Page / Paragraph 等来源元数据

---

## 6.4 多模态处理

* [x] 调用支持图片输入的多模态模型
* [x] MultimodalAIService 抽象
* [x] 图片分析 Worker
* [x] Image Analysis Status
* [x] 区分文本解析、OCR 和视觉理解
* [x] 图片尺寸和数量基础限制
* [x] 多模态 Token / Cost 基础认知

### 完成标准

* 用户可以上传文档
* 文件能够持久化存储
* 文件可以异步解析
* 可以查询解析状态
* 解析结果保留来源信息
* 文件资源具有用户权限隔离

---

# Phase 7：RAG — 核心闭环已完成

> 目标：构建具有权限控制、上下文预算、引用追溯和基础评估能力的知识库问答系统。

## 7.1 文档 Chunking

* [x] `DocumentChunk`
* [x] `DocumentChunkSource`
* [x] Segment → Chunk
* [x] 自然边界分块
* [x] Chunk Overlap
* [x] Oversized Segment Sliding
* [x] Chunk Size 配置化
* [x] Overlap 配置化
* [x] 保存 Chunk 来源关系

核心链：

```text
File
↓
DocumentSegment
↓
DocumentChunk
↓
DocumentChunkSource
```

---

## 7.2 Embedding

* [x] `EmbeddingService`
* [x] `OpenAIEmbeddingService`
* [x] `MockEmbeddingService`
* [x] 单条 Embedding
* [x] Batch Embedding
* [x] Embedding Provider
* [x] Model
* [x] Dimension
* [x] Response Validation
* [x] Provider Index Reordering
* [x] `ChunkEmbeddingService`
* [x] `ChunkEmbeddingDraft`

核心链：

```text
DocumentChunk
↓
EmbeddingService
↓
ChunkEmbeddingDraft
```

---

## 7.3 Vector Store 与索引

* [x] `VectorStore`
* [x] Qdrant
* [x] Collection
* [x] Point
* [x] Vector
* [x] Payload
* [x] `VectorRecord`
* [x] Stable Point ID
* [x] Upsert
* [x] Delete by fileId
* [x] indexVersion
* [x] `DocumentIndex`
* [x] PENDING
* [x] INDEXING
* [x] SUCCESS
* [x] FAILED
* [x] CAS Index Claim
* [x] Index Retry 基础
* [x] MySQL Source of Truth
* [x] Qdrant Derived Index

核心思想：

```text
MySQL
= Business Source of Truth

Qdrant
= Derived / Rebuildable Semantic Index
```

---

## 7.4 Retrieval

* [x] Query Embedding
* [x] Vector Search
* [x] TopK
* [x] Score Threshold
* [x] userId Filter
* [x] fileIds Filter
* [x] indexVersion Filter
* [x] `VectorSearchRequest`
* [x] `VectorSearchResult`
* [x] `RetrievalResult`
* [x] Qdrant Payload Parse
* [x] MySQL Chunk Hydration
* [x] Owner-aware 二次权限校验
* [x] Preserve Retrieval Ranking
* [x] Orphan Vector 容忍

核心链：

```text
Query
↓
EmbeddingService
↓
Query Vector
↓
Qdrant Search
↓
Permission Filter
↓
Top-K
↓
chunkId
↓
MySQL Hydration
↓
RetrievalResult
```

---

## 7.5 Context Assembly、Generation 与 Citation

### Context Assembly

* [x] `TokenEstimator`
* [x] `SimpleTokenEstimator`
* [x] `ContextAssembler`
* [x] `ContextAssemblyResult`
* [x] `RagContextSource`
* [x] Context Token Budget
* [x] Rank-preserving Greedy Packing
* [x] Oversized Chunk Skip
* [x] 完整 Chunk 优先
* [x] Source ID：S1 / S2 / ...

核心链：

```text
RetrievalResult[]
↓
TokenEstimator
↓
ContextAssembler
↓
ContextAssemblyResult
```

---

### Grounded Prompt

* [x] `RagPromptBuilder`
* [x] System Prompt
* [x] Context
* [x] User Query
* [x] 要求模型只基于 Context 回答
* [x] Context 不足时允许拒答
* [x] Retrieved Context 视为不可信输入
* [x] 基础 Indirect Prompt Injection 防护
* [x] 模型只能引用实际 Source ID

---

### RAG Generation

* [x] `AIService.complete()`
* [x] OpenAI Provider
* [x] Mock Provider
* [x] Reuse Timeout
* [x] Reuse Retry
* [x] Low Temperature
* [x] Empty Context Short Circuit

---

### Citation

* [x] 模型返回 `answer`
* [x] 模型返回 `sourceIds`
* [x] Strict JSON Parse
* [x] Source ID Whitelist
* [x] Invalid Citation Drop
* [x] Citation Deduplication
* [x] Citation Order Preserve
* [x] `RagCitation`
* [x] `RagAnswer`

完整 RAG：

```text
Document
↓
Parse
↓
Chunk
↓
Embedding
↓
Qdrant
↓
Query Embedding
↓
Retrieval
↓
Context Assembly
↓
Grounded Prompt
↓
LLM
↓
Citation
```

---

## 7.6 RAG Evaluation

> 目标：理解并实现基础 Retrieval Evaluation，不让大规模 Benchmark 成为当前项目推进的阻塞项。

### 已完成

* [x] Retrieval Evaluation 基础设计
* [x] Retrieval Evaluation Engine
* [x] Evaluation Case
* [x] Ground Truth
* [x] Recall@K
* [x] Precision@K
* [x] Hit@K
* [x] HitRate@K
* [x] Reciprocal Rank
* [x] MRR
* [x] Retrieval / Context / Generation 分层诊断
* [x] 理解 Evaluation Dataset 的作用
* [x] 理解 Ground Truth 需要独立于 Retrieval System

### 暂缓

* [ ] 大规模公开 Benchmark 导入
* [ ] 20～100+ 人工 Evaluation Cases
* [ ] Embedding Model 大规模对比
* [ ] Chunking 参数 Benchmark
* [ ] TopK / Threshold 完整参数实验
* [ ] Generation Evaluation
* [ ] LLM-as-a-Judge
* [ ] Faithfulness Evaluation
* [ ] Claim-level Citation Evaluation

后续仅要求：

* [ ] 使用少量 5～10 个 Query 做 Smoke Evaluation
* [ ] 验证 Evaluation Engine 可以真实运行
* [ ] README 简要说明 Evaluation Design

### 完成标准

* 能解释 Recall@K、Precision@K、HitRate@K 和 MRR
* 能区分 Retrieval Failure 和 Generation Failure
* Evaluation Engine 可以运行
* 大规模真实 Benchmark 不作为进入下一 Phase 的前置条件

---

# Phase 8：容器化与部署基础

> 目标：将当前只能在开发环境运行的项目升级为可复制、可一键启动的完整工程。

这一阶段暂时不要求立即购买云服务器。

优先实现：

```text
Local Development
↓
Docker Image
↓
Docker Compose
↓
Production-like Local Environment
```

后续再根据需要部署到云服务器。

---

## 8.1 Docker 基础

需要掌握：

* [ ] Dockerfile
* [ ] Image
* [ ] Container
* [ ] Registry
* [ ] Docker Build
* [ ] Docker Run
* [ ] Container Port
* [ ] Host Port
* [ ] Docker Network
* [ ] Volume
* [ ] Environment Variable

需要理解关系：

```text
Dockerfile
↓
docker build
↓
Image
↓
docker run
↓
Container
```

---

## 8.2 Spring Boot Dockerfile

* [ ] 编写项目 Dockerfile
* [ ] Maven Multi-stage Build
* [ ] Build Stage
* [ ] Runtime Stage
* [ ] Runtime 使用 JRE Image
* [ ] 复制最终 JAR
* [ ] 配置 `ENTRYPOINT`
* [ ] 配置容器端口
* [ ] 编写 `.dockerignore`
* [ ] 理解 Docker Layer Cache
* [ ] 理解 `ENTRYPOINT` 和 `CMD`
* [ ] 控制最终 Image 大小

目标：

```text
Source Code
↓
Maven Build
↓
Spring Boot JAR
↓
Docker Image
↓
Spring Boot Container
```

---

## 8.3 Docker Compose

统一管理：

```text
Docker Compose

├── app
├── mysql
├── qdrant
└── minio
```

* [ ] Spring Boot Service
* [ ] MySQL Service
* [ ] Qdrant Service
* [ ] MinIO Service
* [ ] Compose Network
* [ ] Service Name DNS
* [ ] Port Mapping
* [ ] `depends_on`
* [ ] Restart Policy
* [ ] Healthcheck

重点理解：

宿主机访问容器：

```text
localhost + mapped port
```

容器访问容器：

```text
service-name + container-port
```

例如：

```text
Spring Boot → MySQL
mysql:3306

Spring Boot → Qdrant
qdrant:6333

Spring Boot → MinIO
minio:9000
```

---

## 8.4 数据持久化

* [ ] MySQL Volume
* [ ] Qdrant Volume
* [ ] MinIO Volume
* [ ] 验证 Container 删除后数据不会丢失
* [ ] 理解 Container Lifecycle 和 Data Lifecycle 的区别

至少设计：

```text
mysql-data
qdrant-data
minio-data
```

核心思想：

```text
Container
= Disposable Runtime

Volume
= Persistent Data
```

---

## 8.5 Environment 与 Secret

* [ ] `.env`
* [ ] `.env.example`
* [ ] `.gitignore`
* [ ] DB URL
* [ ] DB User
* [ ] DB Password
* [ ] JWT Secret
* [ ] AI API Key
* [ ] AI Base URL
* [ ] AI Model
* [ ] Qdrant URL
* [ ] MinIO Endpoint
* [ ] MinIO Credentials

原则：

```text
.env
→ 真实配置
→ 不提交 Git

.env.example
→ 配置模板
→ 可以提交 Git
```

---

## 8.6 Health Check

* [ ] Spring Boot Actuator
* [ ] `/actuator/health`
* [ ] MySQL Healthcheck
* [ ] App Healthcheck
* [ ] 理解 Startup
* [ ] 理解 Liveness
* [ ] 理解 Readiness
* [ ] 理解 `depends_on` 不代表依赖服务已经真正 Ready

核心思想：

```text
Container Started
≠
Application Ready
```

---

## 8.7 README 一键启动

README 至少包括：

* [ ] 项目环境要求
* [ ] Docker Requirement
* [ ] `.env.example`
* [ ] AI Provider 配置
* [ ] Docker Compose 启动
* [ ] Swagger 地址
* [ ] Health 地址
* [ ] MySQL
* [ ] Qdrant
* [ ] MinIO
* [ ] 常见启动问题

理想启动流程：

```text
git clone
↓
copy .env.example .env
↓
填写必要 Secret
↓
docker compose up --build
↓
Spring Boot Ready
↓
Swagger 可访问
```

### 完成标准

* `docker compose up --build` 可以启动完整系统
* MySQL 正常运行
* Qdrant 正常运行
* MinIO 正常运行
* Spring Boot 正常连接全部依赖
* Flyway Migration 正常
* Volume 数据可持久化
* Swagger 可访问
* `/actuator/health` 正常
* API Key 等 Secret 不进入 Git
* 其他开发者可以根据 README 独立启动系统

---

# Phase 9：Function Calling 与受控 Agent

> 目标：让模型能够在后端控制范围内调用真实业务工具，建立完整的 LLM → Tool → Result → LLM Agent Loop。

本阶段以 AI 应用开发岗位为导向，不追求构建复杂通用 Agent Framework。

---

## 9.1 Function Calling

* [ ] 定义 Tool Name
* [ ] Tool Description
* [ ] Parameter Schema
* [ ] Tool Definition 与业务 Service 分离
* [ ] 模型只负责选择工具
* [ ] 模型只负责生成 Tool Arguments
* [ ] 后端负责参数 Validation
* [ ] 后端负责 Permission Check
* [ ] 后端负责真实 Tool Execution
* [ ] Tool Result 返回给 LLM
* [ ] LLM 根据 Tool Result 生成 Final Answer

核心链：

```text
User
↓
LLM
↓
Tool Call
↓
Backend Validation
↓
Business Service
↓
Tool Result
↓
LLM
↓
Final Answer
```

---

## 9.2 Tool Design

第一版优先使用已有业务能力，不重复造 Service。

至少实现 2～3 个只读工具：

### `searchKnowledgeBase`

* [ ] 调用现有 RetrievalService
* [ ] 支持用户权限
* [ ] 支持 fileIds
* [ ] 限制返回数量

### `queryTask`

* [ ] 查询当前用户 Task
* [ ] 禁止访问其他用户 Task

### `getFileInfo`

* [ ] 查询当前用户文件信息
* [ ] 返回有限字段
* [ ] 禁止返回任意文件系统路径

共同要求：

* [ ] 参数 Schema
* [ ] 参数校验
* [ ] Tool Timeout
* [ ] Tool Error Code
* [ ] Tool Result Size Limit
* [ ] Permission Check

第一版全部采用：

```text
Read-only Tool
```

暂时不急着开放：

* Create
* Update
* Delete
* External URL
* Arbitrary SQL
* Arbitrary File System Access

---

## 9.3 Agent Loop

* [ ] LLM → Tool Call
* [ ] Tool Call → Tool Result
* [ ] Tool Result → LLM
* [ ] 支持多轮 Tool Calling
* [ ] 最大 Tool Round
* [ ] 最大执行时间
* [ ] Token Budget
* [ ] 重复 Tool Call 检测
* [ ] 相同参数重复调用检测
* [ ] Invalid Tool
* [ ] Invalid Arguments
* [ ] Tool Failure
* [ ] Model Failure
* [ ] Safe Termination

防止：

```text
LLM
↓
Tool
↓
LLM
↓
Tool
↓
LLM
↓
Tool
↓
无限循环
```

---

## 9.4 Agent Memory 与 Context

* [ ] 区分 Conversation Memory
* [ ] Tool Execution State
* [ ] Business State
* [ ] 第一版复用短期 Conversation Context
* [ ] Tool Result 不无限加入 Context
* [ ] 对过大的 Tool Result 做裁剪
* [ ] 不强制实现 Planner

暂缓：

* [ ] Long-term Memory
* [ ] Complex Planner
* [ ] Reflection Loop
* [ ] Autonomous Agent Planning

---

## 9.5 Agent Execution Trace

每次 Agent 请求至少记录：

* [ ] Request ID
* [ ] Round
* [ ] Model
* [ ] Tool Name
* [ ] Tool Arguments
* [ ] Tool Latency
* [ ] Tool Result Summary
* [ ] Error Type
* [ ] Final Status

目标：

```text
一次 Agent 请求
↓
可以还原完整执行轨迹
```

### 完成标准

* 模型可以正确选择至少 2～3 个 Tool
* Tool Definition 和业务 Service 分离
* Tool 执行前具有参数校验
* Tool 执行前具有权限校验
* Agent 有最大调用轮数
* Agent 有最大执行时间
* 重复 Tool Call 不会无限循环
* Tool Failure 可以安全结束
* 能查看一次 Agent 的完整 Tool Trace

---

# Phase 10：异步任务、RabbitMQ 与任务状态管理 — 可选增强阶段

> 目标：将当前进程内异步任务进一步升级为消息队列驱动的可靠异步架构。
>
> 本阶段主要增强 Java 后端和分布式工程能力。如果求职时间有限，可以在完成 Phase 9 后先进入 Phase 11。

---

## 10.1 Async Task Model

* [ ] PENDING
* [ ] RUNNING
* [ ] SUCCESS
* [ ] FAILED
* [ ] CANCELLED
* [ ] taskId
* [ ] Task Status Query
* [ ] Failure Reason
* [ ] Retry Count

---

## 10.2 RabbitMQ

第一版选择 RabbitMQ，不同时引入 Kafka。

* [ ] RabbitMQ
* [ ] Exchange
* [ ] Queue
* [ ] Routing Key
* [ ] Producer
* [ ] Consumer
* [ ] Message Serialization

可以优先改造：

```text
File Upload
↓
Create Async Task
↓
RabbitMQ
↓
Document Parse Worker
↓
Chunk
↓
Embedding
↓
Vector Index
```

---

## 10.3 Idempotency

* [ ] Message ID
* [ ] Idempotency Key
* [ ] Duplicate Delivery
* [ ] Consumer Idempotency
* [ ] 避免重复创建 Chunk
* [ ] 避免重复 Index Side Effect

理解：

```text
At-least-once Delivery
↓
消息可能重复
↓
Consumer 必须考虑幂等
```

---

## 10.4 Retry 与 DLQ

* [ ] Retryable Error
* [ ] Non-retryable Error
* [ ] Max Retry
* [ ] Retry Delay
* [ ] Dead Letter Queue
* [ ] Final Failure Reason
* [ ] Manual Retry 基础

---

## 10.5 DB 与 MQ 一致性

重点理解：

```text
Database Commit
+
Message Send
```

不是一个天然原子操作。

需要掌握：

* [ ] DB Transaction
* [ ] MQ Publish
* [ ] Message Loss
* [ ] Duplicate Message
* [ ] Outbox Pattern
* [ ] Eventual Consistency

第一版不强制实现完整 Transactional Outbox，但需要能够解释它解决什么问题。

### 完成标准

* 长耗时任务可以脱离 HTTP Request Thread
* 用户可以通过 taskId 查询任务状态
* Consumer 能处理重复消息
* Retry 有明确上限
* 无法恢复的消息进入 DLQ
* 能解释数据库事务和 MQ 发送之间的一致性问题

---

# Phase 11：可观测性、CI 与生产就绪

> 目标：让系统从“能够运行”进一步升级为“能够观察、诊断、验证和维护”。

---

## 11.1 Spring Boot Actuator 与 Micrometer

* [ ] Spring Boot Actuator
* [ ] Micrometer
* [ ] HTTP Request Count
* [ ] HTTP Error Rate
* [ ] HTTP Latency
* [ ] P50
* [ ] P95
* [ ] P99

需要理解：

```text
Average Latency
≠
Tail Latency
```

重点掌握：

* P50
* P95
* P99
* Long-tail Latency

---

## 11.2 AI Observability

至少监控：

* [ ] AI Request Count
* [ ] Model
* [ ] Provider
* [ ] AI Latency
* [ ] Input Token
* [ ] Output Token
* [ ] Retry Count
* [ ] AI Error Count
* [ ] AI Error Rate

可设计：

```text
ai.request.count
ai.request.latency
ai.error.count
ai.retry.count
ai.input.tokens
ai.output.tokens
```

---

## 11.3 RAG Observability

至少监控：

* [ ] Query Embedding Latency
* [ ] Retrieval Latency
* [ ] Retrieved Count
* [ ] Empty Retrieval Count
* [ ] Empty Retrieval Rate
* [ ] Context Source Count
* [ ] Estimated Context Tokens
* [ ] Context Truncated Count
* [ ] Context Truncated Rate
* [ ] Generation Latency
* [ ] Structured Output Parse Failure
* [ ] Citation Count
* [ ] Total RAG Latency

核心诊断链：

```text
RAG Request Slow
↓
Embedding Slow?
↓
Vector Search Slow?
↓
MySQL Hydration Slow?
↓
Context Assembly Slow?
↓
LLM Generation Slow?
```

---

## 11.4 Agent Observability

* [ ] Agent Request Count
* [ ] Tool Call Count
* [ ] Tool Error Rate
* [ ] Tool Latency
* [ ] Average Tool Rounds
* [ ] Agent Failure Rate
* [ ] Max Round Termination Count

---

## 11.5 Logging 与 Trace ID

* [ ] Request ID
* [ ] Trace ID
* [ ] MDC
* [ ] userId
* [ ] taskId
* [ ] conversationId
* [ ] 分层记录 Retrieval / AI / Tool Latency
* [ ] 根据 Trace ID 还原一次请求

禁止日志记录：

* API Key
* Password
* JWT Secret
* 完整敏感文档
* 不必要的完整 Prompt
* 用户隐私信息

---

## 11.6 Prometheus

* [ ] Micrometer Prometheus Registry
* [ ] `/actuator/prometheus`
* [ ] Prometheus
* [ ] Prometheus Scrape
* [ ] 基础 PromQL
* [ ] P95 / P99 查询

可选：

* [ ] Grafana
* [ ] AI Dashboard
* [ ] RAG Dashboard

Grafana 只作为展示增强，不作为项目必须项。

---

## 11.7 Stability

* [ ] 所有外部模型调用设置 Timeout
* [ ] Qdrant Timeout
* [ ] MinIO Timeout
* [ ] 必要接口限流
* [ ] Retry Boundary
* [ ] Fail Fast
* [ ] 基础 Graceful Shutdown
* [ ] Readiness
* [ ] Liveness

可了解：

* Bucket4j
* Sentinel
* Circuit Breaker
* Resilience4j

第一版不要求同时引入所有组件。

---

## 11.8 GitHub Actions

建立：

```text
Push / Pull Request
↓
Checkout
↓
Setup JDK
↓
Maven Cache
↓
mvn test
↓
mvn package
```

* [ ] GitHub Actions Workflow
* [ ] Maven Cache
* [ ] Automated Test
* [ ] Automated Build
* [ ] PR 自动检查
* [ ] CI Failure 阻止合并（仓库条件允许时）

暂不要求复杂 CD。

---

## 11.9 公网 Demo — 可选

本地 Docker Compose 完成之后，根据时间决定是否部署。

简单部署架构：

```text
Internet
↓
Nginx
↓
Spring Boot Container
├── MySQL
├── Qdrant
└── MinIO

Spring Boot
↓
External LLM / Embedding API
```

可选学习：

* [ ] 云服务器
* [ ] Domain
* [ ] Nginx
* [ ] Reverse Proxy
* [ ] HTTPS
* [ ] TLS Certificate
* [ ] Firewall
* [ ] Production Environment Variables

公网部署属于加分项，不作为项目完成的硬性前置条件。

### 完成标准

* 可以查看 HTTP 基础指标
* 可以查看 AI / RAG / Agent 核心指标
* 能解释 P95 / P99
* 能根据 Trace ID 定位核心请求链
* GitHub PR 可以自动运行测试
* 系统能够通过 Docker Compose 可靠启动
* 日志不包含敏感 Secret

---

# 最终项目 Stop Condition

达到下面状态以后，不再继续无限堆功能。

## 后端基础

* [x] MySQL
* [x] Flyway
* [x] Transaction
* [x] JWT
* [x] Multi-user Permission
* [x] OpenAPI

## AI Core

* [x] AIService
* [x] Mock / Real Provider
* [x] Prompt Engineering
* [x] Structured Output
* [x] Retry
* [x] Timeout
* [x] Usage Log
* [x] SSE
* [x] Conversation
* [x] Context Engineering

## File & RAG

* [x] File Upload
* [x] Local / MinIO
* [x] Async Parsing
* [x] Chunking
* [x] Embedding
* [x] Qdrant
* [x] Permission-aware Retrieval
* [x] Context Budget
* [x] Grounded Generation
* [x] Citation
* [x] Retrieval Evaluation Engine

## Agent

* [ ] Function Calling
* [ ] 2～3 个 Tool
* [ ] Tool Parameter Validation
* [ ] Tool Permission Validation
* [ ] Agent Loop
* [ ] Loop Boundary
* [ ] Execution Trace

## Engineering

* [ ] Dockerfile
* [ ] Docker Compose
* [ ] Volume
* [ ] Environment Variable
* [ ] Actuator
* [ ] Metrics
* [ ] Prometheus
* [ ] Trace ID
* [ ] GitHub Actions

达到以上核心能力之后：

```text
停止继续堆功能
↓
复盘核心源码
↓
整理架构图
↓
准备项目面试问题
↓
优化简历
↓
开始投递 AI 应用开发实习
```

---

# 最终项目交付物

项目最终至少应包含：

* Spring Boot 后端服务
* MySQL + Flyway
* JWT 用户认证与资源隔离
* 大模型同步调用
* 大模型流式调用
* Prompt Template
* Structured Output
* Conversation / Context Management
* 文件上传
* MinIO
* 异步文档解析
* Document Chunking
* Embedding
* Qdrant Vector Database
* Permission-aware Retrieval
* Context Assembly
* Grounded RAG
* Citation
* Retrieval Evaluation Engine
* Function Calling
* 受控 Agent Loop
* Docker Compose
* Actuator / Micrometer
* Prometheus
* GitHub Actions

RabbitMQ 和公网 Deployment 根据时间作为增强项。

---

# 求职展示材料

## 1. GitHub README

* [ ] 项目介绍
* [ ] 项目背景
* [ ] 技术栈
* [ ] Architecture Diagram
* [ ] Core Features
* [ ] RAG Pipeline
* [ ] Agent Pipeline
* [ ] Docker Compose 启动方式
* [ ] Environment Variables
* [ ] Swagger
* [ ] Health Check
* [ ] Demo Screenshots

---

## 2. 项目架构图

建议最终至少准备：

```text
docs/
├── architecture.png
├── rag-sequence.png
├── agent-sequence.png
└── er-diagram.png
```

包括：

* [ ] Overall Architecture
* [ ] RAG Sequence Diagram
* [ ] Agent Sequence Diagram
* [ ] Database ER Diagram

---

## 3. Demo

至少展示：

* [ ] 用户注册 / 登录
* [ ] 文件上传
* [ ] 文档解析
* [ ] RAG 问答
* [ ] Citation
* [ ] SSE Streaming
* [ ] Agent Tool Call
* [ ] Docker Compose
* [ ] Monitoring

可选：

* [ ] 3～5 分钟 Demo Video

---

# 项目名称建议

## 基于 Spring Boot 的多用户 AI 知识库与智能工具调用平台

英文：

**Multi-user AI Knowledge Base and Tool-Calling Platform Based on Spring Boot**

---

# 最终简历能力主线

项目介绍应围绕以下能力展开，而不是简单罗列技术框架。

### 1. AI Model Integration

通过 `AIService` 抽象模型调用层，支持 Mock 与 OpenAI-compatible Provider，并统一处理模型配置、Timeout、Retry、指数退避和异常分类。

### 2. Context Engineering

设计多轮 Conversation Context 和 RAG Context 两套上下文管理机制，通过 Token Budget 控制真正发送给模型的信息，而不是无限累积历史内容。

### 3. File & RAG Pipeline

实现：

```text
Upload
→ Parse
→ Chunk
→ Embedding
→ Qdrant
→ Retrieval
→ Context Assembly
→ LLM
→ Citation
```

的完整文档知识库 Pipeline。

### 4. Permission-aware Retrieval

Qdrant 检索阶段按照 userId、fileIds 和 indexVersion 过滤，并通过 MySQL Source of Truth 对 Retrieval Result 进行二次资源归属校验。

### 5. Grounded Generation

通过 Context Assembly、Grounded Prompt、Empty Context Refusal 和 Citation Whitelist，降低模型脱离检索证据生成答案的风险。

### 6. Function Calling & Agent

通过 Tool Schema 建立模型与后端业务能力之间的受控接口，模型只负责 Tool Selection 和 Arguments Generation，后端负责 Validation、Permission、Execution 和 Safety Boundary。

### 7. Deployment

使用 Docker Compose 统一管理 Spring Boot、MySQL、Qdrant 和 MinIO，实现项目环境的一键启动和数据持久化。

### 8. Observability

使用 Actuator、Micrometer 和 Prometheus 对 HTTP、AI、RAG 和 Agent 的延迟、错误率、Token、P95/P99 和 Tool Execution 进行基础监控。

---

# 后续实际学习顺序

```text
当前 Phase 7 完成
↓
Phase 8
Docker / Dockerfile / Docker Compose
↓
Phase 9
Function Calling / Agent
↓
Phase 10
RabbitMQ（时间允许）
↓
Phase 11
Observability / Prometheus / Trace / CI
↓
项目收尾
↓
源码复盘
↓
项目面试题
↓
简历
↓
投递实习
```

---


```


