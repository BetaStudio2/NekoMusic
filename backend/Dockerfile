# =========================
# 构建阶段
# =========================
FROM maven:3.9-eclipse-temurin-26 AS builder

WORKDIR /build

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl xz-utils \
    && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY src ./src

RUN mvn -B clean package -DskipTests


# =========================
# 运行阶段
# =========================
FROM azul/zulu-openjdk:26.0.2.1-jdk

WORKDIR /app

COPY --from=builder /build/target/backend-1.0-SNAPSHOT.jar /app/backend.jar

EXPOSE 65535

ENV LANG=zh_CN.UTF-8 \
    LANGUAGE=zh_CN:zh \
    LC_ALL=zh_CN.UTF-8

ENTRYPOINT ["java", "-server", \
    "-XX:+UseG1GC", \
    "-XX:+UseStringDeduplication", \
    "-XX:MaxGCPauseMillis=100", \
    "-XX:InitialRAMPercentage=25.0", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+DisableExplicitGC", \
    "-Djava.awt.headless=true", \
    "-jar", "/app/backend.jar"]
