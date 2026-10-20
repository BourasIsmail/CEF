# syntax=docker/dockerfile:1

# ---- Build -----------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Dépendances d'abord : cette couche n'est reconstruite que si pom.xml change
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar /workspace/app.jar

# ---- Runtime (utilisateur non-root) -----------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app \
    && mkdir -p /data/uploads && chown -R app:app /data
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar

USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8" \
    FILE_STORAGE_DIR=/data/uploads
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
