# AI-application

基于 Spring Boot 3 的任务管理应用，提供任务创建、查询、删除以及模拟 AI 分析结果的 RESTful API。

## 技术栈

| 技术 | 版本 / 说明 |
|------|-------------|
| Java | 17 |
| Spring Boot | 3.4.3 |
| Spring Data JPA | 持久化层 |
| H2 Database | 内存数据库（开发/测试用） |
| Spring Validation | 请求参数校验 |
| Maven | 构建工具 |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+

### 启动项目

```bash
# 克隆项目
git clone https://github.com/kyrie121029/AI-application.git
cd AI-application

# 启动（Windows）
mvnw.cmd spring-boot:run

# 启动（macOS / Linux）
./mvnw spring-boot:run
```

启动后访问 `http://localhost:8080`。

### H2 控制台

启动后可访问 `http://localhost:8080/h2-console` 查看数据库：

- **JDBC URL**: `jdbc:h2:mem:taskdb`
- **用户名**: `sa`
- **密码**: （留空）

## API 接口

所有接口前缀：`/api/tasks`

### 创建任务

```
POST /api/tasks
Content-Type: application/json

{
  "title": "分析客户反馈",
  "taskType": "文本分析",
  "inputText": "产品质量不错，但发货速度太慢了"
}
```

### 查询所有任务

```
GET /api/tasks
```

### 查询单个任务

```
GET /api/tasks/{id}
```

### 删除任务

```
DELETE /api/tasks/{id}
```

### 生成模拟分析结果

```
POST /api/tasks/{id}/mock-result
```

## 项目结构

```
src/main/java/com/example/demo/
├── common/              # 公共组件（统一响应、全局异常处理）
│   ├── ApiResponse.java
│   └── GlobalExceptionHandler.java
├── controller/          # 控制器层
│   └── TaskController.java
├── dto/                 # 数据传输对象
│   ├── CreateTaskRequest.java
│   ├── CreateTaskResponse.java
│   └── MockResultResponse.java
├── enums/               # 枚举
│   └── TaskStatus.java
├── exception/           # 自定义异常
│   └── TaskNotFoundException.java
├── model/               # 数据模型（JPA Entity）
│   └── Task.java
├── repository/          # 数据访问层
│   └── TaskRepository.java
├── service/             # 业务逻辑层
│   └── TaskService.java
└── DemoApplication.java # 启动类
```

## License

MIT
