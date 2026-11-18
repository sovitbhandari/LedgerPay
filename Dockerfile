FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system ledger \
    && useradd --system --gid ledger --create-home --shell /usr/sbin/nologin ledger
WORKDIR /app
COPY --from=build /src/target/ledgerpay.jar /app/ledgerpay.jar
USER ledger
EXPOSE 8080
ENV TZ=UTC
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=10 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/ledgerpay.jar"]
