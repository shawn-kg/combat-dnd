# 1. Build the React app.
FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. Build the Spring Boot jar with the React build inside it.
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /backend
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY backend/src ./src
COPY --from=frontend /frontend/dist ./src/main/resources/static
RUN ./gradlew --no-daemon bootJar -x test

# 3. Run it.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /backend/build/libs/combat-dnd-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
