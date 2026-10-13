FROM debian:13-slim
WORKDIR /app

# スキーマの初回適用と、コンテナの中からのユーザー登録に使う
RUN apt-get update \
    && apt-get install -y --no-install-recommends sqlite3 \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --no-create-home --shell /usr/sbin/nologin app \
    && mkdir /app/data \
    && chown app:app /app/data

COPY frontend/app-web/build/dist/wasmJs/productionExecutable static

COPY backend/build/native/nativeCompile/kmp-activitypub kmp-activitypub

COPY backend/repository/src/main/resources/db/schema.sql schema.sql

COPY docker/entrypoint.sh entrypoint.sh

USER app

ENV STATIC_SRC_DIR=/app/static
ENV DB_PATH=/app/data/kmp-activitypub.db

VOLUME /app/data

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
