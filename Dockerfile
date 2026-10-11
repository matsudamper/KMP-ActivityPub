FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY backend backend

RUN ./gradlew :backend:installDist --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /build/backend/build/install/backend /app

EXPOSE 8080

ENTRYPOINT ["/app/bin/backend"]
