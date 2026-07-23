FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
# Tune JVM heap for e2-medium (4GB RAM) — leaves headroom for Python services and MySQL
ENTRYPOINT ["java", "-Xms256m", "-Xmx768m", "-jar", "app.jar"]
