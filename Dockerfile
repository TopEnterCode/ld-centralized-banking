# syntax=docker/dockerfile:1.7
FROM node:22.18.0-bookworm-slim AS node-runtime

FROM maven:3.9.11-eclipse-temurin-21 AS build-env
COPY --from=node-runtime /usr/local /usr/local

FROM build-env AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS dtm-service
WORKDIR /app
COPY --from=build /workspace/dtm-service/target/dtm-service-1.0.0-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

FROM eclipse-temurin:21-jre-alpine AS payment-service
WORKDIR /app
COPY --from=build /workspace/payment-service/target/payment-service-1.0.0-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

FROM eclipse-temurin:21-jre-alpine AS customer-profile-service
WORKDIR /app
COPY --from=build /workspace/customer-profile-service/target/customer-profile-service-1.0.0-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

FROM eclipse-temurin:21-jre-alpine AS fraud-service
WORKDIR /app
COPY --from=build /workspace/fraud-service/target/fraud-service-1.0.0-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8084
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

FROM eclipse-temurin:21-jre-alpine AS notification-service
WORKDIR /app
COPY --from=build /workspace/notification-service/target/notification-service-1.0.0-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8085
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

FROM eclipse-temurin:21-jre-alpine AS web-gateway
WORKDIR /app
COPY --from=build /workspace/web-gateway/target/web-gateway-1.0.0-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

FROM mcr.microsoft.com/playwright/java:v1.55.0-noble AS e2e-tests
COPY --from=node-runtime /usr/local /usr/local
WORKDIR /workspace
COPY . .
RUN mvn -B -DskipTests install
RUN mvn -B -pl e2e-tests -Dtest=ArchitecturePolicyTest test
ENTRYPOINT ["mvn", "-o", "-B", "-pl", "e2e-tests", "test", "-De2e=true"]
