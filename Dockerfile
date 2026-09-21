# Minimal Multi-Arch Runtime Container for Archlens Dynamic Workbench
FROM eclipse-temurin:25-jre-alpine

LABEL org.opencontainers.image.title="Archlens — Clean Architecture Dynamic Workbench" \
      org.opencontainers.image.description="Interactive architecture workbench and autonomous AI refactoring companion" \
      org.opencontainers.image.source="https://github.com/fmatar/archlens" \
      org.opencontainers.image.licenses="Apache-2.0"

ENV LANGUAGE='en_US:en'

WORKDIR /work

# Copy pre-built Quarkus fast-jar application
COPY backend/target/quarkus-app/lib/ /work/lib/
COPY backend/target/quarkus-app/*.jar /work/
COPY backend/target/quarkus-app/app/ /work/app/
COPY backend/target/quarkus-app/quarkus/ /work/quarkus/

# Create workspace directory for examined projects
RUN mkdir -p /workspace

EXPOSE 8088

ENTRYPOINT ["java", "-Dquarkus.http.host=0.0.0.0", "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", "-jar", "/work/quarkus-run.jar"]
