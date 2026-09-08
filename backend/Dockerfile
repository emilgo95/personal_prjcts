# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget && addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/agriaudit-backend.jar app.jar
RUN mkdir -p /data/storage && chown -R app:app /data
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
