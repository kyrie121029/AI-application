# CLAUDE.md

## 项目概述

Spring Boot 3 任务管理应用，逐步演进为 AI 应用开发平台。

- **技术栈**：Spring Boot 3.4.3、JDK 17、Maven、JPA、H2
- **端口**：`8080`
- **数据库**：H2 内存数据库（`jdbc:h2:mem:taskdb`），H2 控制台 `/h2-console`
- **学习路线**：见 [ROADMAP.md](./ROADMAP.md)

## 常用命令

```bash
# 启动应用
./mvnw spring-boot:run

# 运行测试
./mvnw test

# 运行单个测试类
./mvnw test -Dtest=TaskServiceTest

# 打包
./mvnw clean package

# 查看依赖树
./mvnw dependency:tree
```

## 目录结构

```
src/main/java/com/example/demo/
├── DemoApplication.java      # 启动类
├── config/                   # @Configuration 配置类
├── common/                   # 全局通用组件（ApiResponse、异常处理、AOP）
├── controller/               # 控制器 —— 只做参数校验和路由
├── service/                  # 业务逻辑层
├── repository/               # JPA Repository 接口
├── model/                    # JPA Entity 实体
├── dto/                      # 请求/响应 DTO
├── enums/                    # 枚举
├── exception/                # 自定义异常
└── util/                     # 工具类

src/main/resources/
├── application.properties    # 主配置
└── db/migration/             # Flyway 迁移脚本（后续使用）

src/test/java/com/example/demo/
└── service/                  # 按被测试类对应放置
```

## 编码规范

### 分层约定

- **Controller**：只做参数校验和路由，不写业务逻辑。入参用 DTO，出参用 `ApiResponse<T>` 包裹
- **Service**：业务编排层，调用 Repository 和外部服务。不操作 HttpServletRequest/Response
- **Repository**：只定义接口，不写实现，不加业务注解
- **Entity**：纯数据载体 + JPA 映射注解，不暴露到 Controller 返回值
- **DTO**：用于请求/响应传输，与 Entity 分离

### Java 代码风格

- JDK 17：能用 `record` 替代简单 DTO 就用 record
- 构造器注入（`private final` + 构造器），禁止 `@Autowired` 字段注入
- `@Valid` / `@Validated` 做参数校验，不在 Controller 里手写 if
- 日志用 `private static final Logger log = LoggerFactory.getLogger(Xxx.class)`
- 方法命名：查询用 `getXxx` / `listXxx`，创建用 `createXxx`，删除用 `deleteXxx`

### API 设计

- 路径前缀：`/api/` + 资源名复数（如 `/api/tasks`）
- 统一响应格式：`ApiResponse<T>`，包含 `code`、`message`、`data`
- 异常统一由 `@RestControllerAdvice` 处理，业务代码中不 try-catch 返回
- 分页参数用 Spring Data 的 `Pageable`

### 测试规范

- 测试类命名：`被测类名 + Test`（如 `TaskServiceTest`）
- 外部依赖用 `@MockBean` mock，不启动真实数据库连接
- 每个 Phase 完成前所有测试必须通过

## 禁止事项

- ❌ Controller 里写业务逻辑
- ❌ `@Autowired` 字段注入
- ❌ Entity 直接作为 Controller 返回值
- ❌ 业务代码中 try-catch 吃掉异常不处理
- ❌ 硬编码敏感信息（API Key、密码），一律走 `application.properties` 或环境变量
- ❌ 跳过测试直接提交代码
- ❌ `System.out.println` 替代日志

## 验证要求

每个 Phase 完成前：

1. `./mvnw test` 全部测试通过
2. 新增接口用 curl 或 Postman 验证过
3. 启动应用无报错
4. 新功能补充了对应的单元测试
5. 代码符合上述编码规范
