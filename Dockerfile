FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY build-logic build-logic
# settings.gradle.kts が include するプロジェクトのディレクトリが無いと、Gradle 9 は構成の時点で落ちる
COPY frontend frontend
COPY backend backend

RUN ./gradlew :backend:installDist --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --no-create-home --shell /usr/sbin/nologin app

COPY --from=build /build/backend/build/install/backend /app

USER app

EXPOSE 8080

ENTRYPOINT ["/app/bin/backend"]
