# native の実行ファイルが glibc 向けなので alpine は使わない
FROM amazoncorretto:25
WORKDIR /app

EXPOSE 8080

RUN dnf install -y shadow-utils \
    && dnf clean all \
    && useradd --system --no-create-home --shell /sbin/nologin app \
    && mkdir /app/data \
    && chown app:app /app/data

COPY frontend/app-web/build/dist/wasmJs/productionExecutable static

COPY backend/build/native/nativeCompile/kmp-activitypub bin/backend

USER app

ENTRYPOINT ["/app/bin/backend"]
