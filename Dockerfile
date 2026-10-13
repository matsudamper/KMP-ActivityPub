FROM debian:13-slim
WORKDIR /app

ENV STATIC_SRC_DIR=/app/static
ENV DB_PATH=/app/data/kmp-activitypub.db

EXPOSE 8080

RUN useradd --system --no-create-home --shell /usr/sbin/nologin app \
    && mkdir /app/data \
    && chown app:app /app/data

COPY frontend/app-web/build/dist/wasmJs/productionExecutable static

COPY backend/build/native/nativeCompile/kmp-activitypub kmp-activitypub

USER app

ENTRYPOINT ["/app/kmp-activitypub"]
