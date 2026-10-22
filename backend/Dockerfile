# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace/backend

COPY backend/gradlew backend/settings.gradle backend/build.gradle ./
COPY backend/gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --console=plain

COPY LICENSE THIRD_PARTY_NOTICES.md /workspace/
COPY backend/src ./src
RUN ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN groupadd --system --gid 10001 sluice \
    && useradd --system --uid 10001 --gid sluice --home-dir /app --shell /usr/sbin/nologin sluice
WORKDIR /app
ADD --checksum=sha256:93a70c8f5d364c7e777f6c4d1b235dba91aef8448bd3fa94359f1d7f3e2eb0ec https://repo.maven.apache.org/maven2/com/microsoft/azure/applicationinsights-agent/3.7.10/applicationinsights-agent-3.7.10.jar /app/applicationinsights-agent.jar
COPY backend/docker-entrypoint.sh /app/entrypoint.sh
RUN chmod 644 /app/applicationinsights-agent.jar && chmod 755 /app/entrypoint.sh && sh -n /app/entrypoint.sh
COPY --from=build --chown=10001:10001 /workspace/backend/build/libs/*.jar /app/sluice.jar
COPY --from=build --chown=10001:10001 /workspace/LICENSE /workspace/THIRD_PARTY_NOTICES.md /licenses/
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=UTC"
USER 10001:10001
ENTRYPOINT ["/app/entrypoint.sh"]

FROM runtime AS worker
ENV SPRING_PROFILES_ACTIVE=production \
    SLUICE_RUNTIME_MODE=worker \
    SPRING_FLYWAY_ENABLED=false \
    SPRING_MAIN_WEB_APPLICATION_TYPE=none
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD ["bash", "-c", "kill -0 1"]

FROM runtime AS api
ENV SPRING_PROFILES_ACTIVE=production \
    SLUICE_RUNTIME_MODE=api
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n" >&3 && IFS= read -r status <&3 && [[ "$status" == *" 200 "* ]]'
