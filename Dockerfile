# ビルドは外部（CI など）で済ませ、成果物だけを詰める。事前に次を実行しておくこと。
# ./gradlew :backend:distTar :frontend:app-web:wasmJsBrowserDistribution
FROM amazoncorretto:25-alpine
WORKDIR /app

RUN adduser -S -H -s /sbin/nologin app

COPY frontend/app-web/build/dist/wasmJs/productionExecutable static

COPY backend/build/distributions/backend.tar backend.tar
RUN tar -xf backend.tar && rm backend.tar

USER app

ENV STATIC_SRC_DIR=/app/static

EXPOSE 8080

ENTRYPOINT ["/app/backend/bin/backend"]
