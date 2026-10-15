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
FROM azul-zulu:27-jdk

WORKDIR /app

COPY --from=builder /build/target/backend-1.0-SNAPSHOT.jar /app/backend.jar

EXPOSE 65535

ENV LANG=C.UTF-8 \
    LANGUAGE=C.UTF-8 \
    LC_ALL=C.UTF-8

# JVM 参数说明（改动前先读）：
#   MaxGCPauseMillis 是 G1 的**停顿目标**，不是上限。取 100 等于告诉 G1「100ms 可以接受」，
#   于是它会一路把年轻代养大、直到单次停顿贴近该值——访问日志里那些原本该 <5ms 的接口
#   （challenge / nonce / favorites 等）零散出现 100~106ms，就是卡在这条线上。
#   降到 25 会让 G1 缩小年轻代、用更频繁但更短的停顿替代。
#   UseStringDeduplication 在每次年轻代回收里多扫一遍存活字符串，对以文件响应为主、堆里
#   字符串周转量不大的服务只增加停顿时间，不省内存，故去掉。
#   ParallelRefProcEnabled 让引用处理并行，缩短该阶段（G1 默认串行，是停顿的一大来源）。
#   若要彻底消除这类停顿，可改用 ZGC（-XX:+UseZGC）并把下面所有 G1 专属参数一并删除。
ENTRYPOINT ["java", "-server", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=25", \
    "-XX:+ParallelRefProcEnabled", \
    "-XX:InitialRAMPercentage=25.0", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+DisableExplicitGC", \
    "-Dfile.encoding=UTF-8", \
    "-Dsun.jnu.encoding=UTF-8", \
    "-Djava.awt.headless=true", \
    "-jar", "/app/backend.jar"]
