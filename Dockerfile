FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies
COPY src src
# Tests run separately with Docker available: ./gradlew build
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/build/libs/callback-server-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
