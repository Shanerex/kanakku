# syntax=docker/dockerfile:1
# Build context is the repository root: docker build -f app/Dockerfile .
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY app/pom.xml app/pom.xml
COPY app/src app/src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -DskipTests -pl app -am package \
    && cp app/target/kanakku-app-*.jar /tmp/app.jar

FROM eclipse-temurin:25-jre
RUN groupadd --system kanakku && useradd --system --gid kanakku --no-create-home kanakku
WORKDIR /app
COPY --from=build /tmp/app.jar app.jar
USER kanakku
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
