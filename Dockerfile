# ===== 阶段 1：构建 =====
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先只拷 pom.xml + 国内镜像配置 → 预下载依赖（层缓存：pom 没变就不重下）
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 再拷源码 → 打包
COPY src ./src
RUN mvn package -DskipTests -B

# ===== 阶段 2：运行 =====
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/target/RAGdemo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
