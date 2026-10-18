FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend-procedural/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline
COPY backend-procedural/src ./src
RUN mvn -B -ntp -DskipTests package
COPY deploy/Healthcheck.java /probe/Healthcheck.java
RUN javac -d /probe /probe/Healthcheck.java

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --gid 10001 rbac && useradd --uid 10001 --gid rbac --no-create-home rbac
COPY --from=build --chown=10001:10001 /workspace/target/*.jar /app/application.jar
COPY --from=build /probe/Healthcheck.class /app/probe/Healthcheck.class
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 CMD ["java", "-cp", "/app/probe", "Healthcheck", "http://127.0.0.1:8080/api/v1/health"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/application.jar"]
