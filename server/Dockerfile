# Giai đoạn 1: Build bằng Maven
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ ./src/
RUN mvn clean package -DskipTests

# Giai đoạn 2: Chạy bằng JRE nhẹ
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Tạo user riêng để chạy server (bảo mật)
RUN addgroup -S chat && adduser -S chat -G chat

COPY --from=build /app/target/simplechat-server-1.0-SNAPSHOT.jar ./server.jar
USER chat

EXPOSE 5000
CMD ["java", "-jar", "server.jar"]