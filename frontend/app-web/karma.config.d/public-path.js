// Karma はバンドルをルート以外から配信するため、本番用の絶対パスを使わない。
delete config.webpack.output.publicPath;
