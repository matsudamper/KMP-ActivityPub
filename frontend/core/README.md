# :frontend:core

Compose に依存しない frontend の処理を置く。

Compose を使うモジュールは、wasmJs でテストを動かすのに `binaries.executable()` が要る。
ここは Compose に依存しないので、そのまま commonTest にテストを書ける。
