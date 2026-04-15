# Multi-stage build: Maven (Java 21) → JRE 21 runtime (PRD §10.1)
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY payment-gateway/pom.xml .
COPY payment-gateway/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -sf http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
