# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY --chmod=0755 mvnw mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests -Djacoco.skip=true package

FROM eclipse-temurin:17-jre-jammy

RUN groupadd --gid 10001 billing && useradd --uid 10001 --gid billing --no-create-home billing

WORKDIR /app
COPY --from=build /app/target/mecanica-billing-service-0.0.1-SNAPSHOT.jar app.jar

USER 10001:10001
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS=""
ENTRYPOINT ["java", "-jar", "app.jar"]
