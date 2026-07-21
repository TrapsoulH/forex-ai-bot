FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
# Tune JVM heap for e2-medium (4GB RAM) — leaves headroom for Python services and MySQL
ENTRYPOINT ["java", "-Xms256m", "-Xmx768m", "-jar", "app.jar"]
