# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Baixa wrapper e dependências em cache BuildKit (sobrevive entre builds).
# Maven Central devolve 429 a IPs do GitHub Actions; o loop espera e tenta de novo.
RUN --mount=type=cache,id=controle-gastos-gradle,target=/root/.gradle,sharing=locked \
    attempt=1; \
    until ./gradlew dependencies --no-daemon; do \
      if [ "$attempt" -ge 5 ]; then exit 1; fi; \
      attempt=$((attempt + 1)); \
      echo "Download de dependências falhou; tentativa $attempt/5 em 30s" >&2; \
      sleep 30; \
    done

COPY src ./src
RUN --mount=type=cache,id=controle-gastos-gradle,target=/root/.gradle,sharing=locked \
    attempt=1; \
    until ./gradlew bootJar -x test --no-daemon; do \
      if [ "$attempt" -ge 5 ]; then exit 1; fi; \
      attempt=$((attempt + 1)); \
      echo "bootJar falhou; tentativa $attempt/5 em 30s" >&2; \
      sleep 30; \
    done \
    && JAR="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && cp "$JAR" /workspace/application.jar \
    && java -Djarmode=tools -jar /workspace/application.jar extract --layers --destination /extracted

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
