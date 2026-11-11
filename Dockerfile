# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /src
COPY pom.xml .
# Dependencies live in this layer (not a cache mount) so the CI layer cache keeps them until pom.xml changes.
RUN mvn -B -q dependency:go-offline
COPY src src
# Tests run in CI (they need Docker for Testcontainers), not inside the image build.
RUN mvn -B -q package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /src/target/cinema-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
