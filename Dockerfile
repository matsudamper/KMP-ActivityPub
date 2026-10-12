FROM debian:13-slim
WORKDIR /app

RUN useradd --system --no-create-home --shell /usr/sbin/nologin app

COPY frontend/app-web/build/dist/wasmJs/productionExecutable static

COPY backend/build/native/nativeCompile/kmp-activitypub kmp-activitypub

USER app

ENV STATIC_SRC_DIR=/app/static

EXPOSE 8080

ENTRYPOINT ["/app/kmp-activitypub"]
