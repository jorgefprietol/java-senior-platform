# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY domain domain
COPY platform platform
COPY ledger-service ledger-service
COPY audit-service audit-service
RUN --mount=type=cache,target=/root/.m2 mvn -B verify
FROM eclipse-temurin:25-jre-alpine AS runtime
RUN apk upgrade --no-cache && addgroup -S app && adduser -S -G app app
WORKDIR /app
ARG SERVICE=ledger-service
COPY --from=build --chown=app:app /workspace/${SERVICE}/target/${SERVICE}-1.0.0.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java","-jar","app.jar"]
