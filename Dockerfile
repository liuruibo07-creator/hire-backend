FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
ARG SERVICE=cloud-gateway
RUN mvn -B -pl ${SERVICE} -am package -DskipTests
RUN cp ${SERVICE}/target/${SERVICE}-1.0.jar /app.jar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system hire && useradd --system --gid hire hire
COPY --from=build --chown=hire:hire /app.jar /app/app.jar
USER hire
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
