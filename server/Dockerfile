FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q test package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S chat && adduser -S chat -G chat
COPY --from=build /build/target/tcp-chat-server.jar /app/server.jar
USER chat
EXPOSE 5000
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
