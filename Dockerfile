# ============================================================
# Phase 8.1 容器化 —— multi-stage build
#   构建阶段：Maven 编译打包
#   运行阶段：精简 JRE 运行 jar
# ============================================================

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先复制 pom 以利用依赖层缓存（仅 pom 变化时才重下依赖）
COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true

COPY src ./src
RUN mvn -q -B -DskipTests package

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 运行时 JVM 参数（内存等），可用 -e JAVA_OPTS 覆盖
ENV JAVA_OPTS=""

# 本地文件上传存储目录
RUN mkdir -p /app/data/uploads

COPY --from=build /app/target/demo-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
