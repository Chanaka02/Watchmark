# syntax=docker/dockerfile:1
# Production image of the backend. Build from the backend/ folder:
#   docker build -t watchmark-backend:<tag> backend
# Tests are not run here: CI runs them (./mvnw verify) before an image is built.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && cp target/watchmark-backend-*.jar /app.jar

FROM eclipse-temurin:25-jre
# Never run as root.
RUN useradd --system --uid 10001 --no-create-home watchmark
COPY --from=build /app.jar /app/app.jar
USER watchmark
EXPOSE 8080
# Heap follows the container memory limit; exit (and let Docker restart) on OOM
# instead of limping on.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
