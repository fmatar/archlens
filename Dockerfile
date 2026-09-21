# Multi-stage build for Clean Architecture Dynamic UML Workbench
# Stage 1: Build Frontend and Backend into a single fast-jar
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build

# Copy project descriptors
COPY pom.xml .
COPY frontend/pom.xml frontend/
COPY frontend/package.json frontend/
COPY backend/pom.xml backend/

# Copy sources
COPY frontend frontend
COPY backend backend

# Build full unified package (pnpm installs & compiles Svelte, backend embeds it into fast-jar)
RUN mvn clean package -DskipTests

# Stage 2: Minimal Runtime Container
FROM eclipse-temurin:25-jre-alpine

LABEL org.opencontainers.image.title="Clean Architecture Dynamic UML Workbench" \
      org.opencontainers.image.description="Interactive architecture workbench and autonomous AI refactoring companion" \
      org.opencontainers.image.source="https://github.com/fmatar/archlens" \
      org.opencontainers.image.licenses="Apache-2.0"

ENV LANGUAGE='en_US:en'

WORKDIR /work

# Copy Quarkus fast-jar application
COPY --from=builder /build/backend/target/quarkus-app/lib/ /work/lib/
COPY --from=builder /build/backend/target/quarkus-app/*.jar /work/
COPY --from=builder /build/backend/target/quarkus-app/app/ /work/app/
COPY --from=builder /build/backend/target/quarkus-app/quarkus/ /work/quarkus/

# Create workspace directory for examined projects
RUN mkdir /workspace

EXPOSE 8088

ENTRYPOINT ["java", "-Dquarkus.http.host=0.0.0.0", "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", "-jar", "/work/quarkus-run.jar"]
