FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
# 先复制依赖描述以复用缓存；镜像打包跳过测试，完整业务测试由独立 Maven/CI 检查负责。
COPY backend-procedural/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline
COPY backend-procedural/src ./src
RUN mvn -B -ntp -DskipTests package
COPY deploy/Healthcheck.java /probe/Healthcheck.java
RUN javac -d /probe /probe/Healthcheck.java

FROM eclipse-temurin:21-jre-jammy
# 运行层仅包含 JRE、应用和探针，不携带 Maven、源码或构建缓存。
WORKDIR /app
RUN groupadd --gid 10001 rbac && useradd --uid 10001 --gid rbac --no-create-home rbac
COPY --from=build --chown=10001:10001 /workspace/target/*.jar /app/application.jar
COPY --from=build /probe/Healthcheck.class /app/probe/Healthcheck.class
USER 10001:10001
# 探针访问应用的数据库就绪接口；非 200 或连接超时令容器变为 unhealthy。
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 CMD ["java", "-cp", "/app/probe", "Healthcheck", "http://127.0.0.1:8080/api/v1/health"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/application.jar"]
