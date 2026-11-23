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
# Tuned for a 512 MB free instance: a modest heap, the serial GC (one small CPU), and capped
# code cache and thread stacks so the whole process stays well under the limit.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55 -XX:+UseSerialGC -XX:ReservedCodeCacheSize=48m -XX:MaxMetaspaceSize=128m -Xss512k -XX:TieredStopAtLevel=1"
ENTRYPOINT ["java", "-jar", "app.jar"]
