# Paparazzi

## UI 変更時
- `@Preview` を追加/更新しスナップショットを撮影する
- スナップショット画像はコミットしない。セッションのチャットに貼る
- 画像なしで UI 作業完了にしない

## 実行
- `./gradlew test` では Paparazzi は自動除外
- Paparazzi は `:frontend:app-android` で個別実行する

```bash
./gradlew :frontend:app-android:verifyPaparazziDebug
./gradlew :frontend:app-android:recordPaparazziDebug -Dpaparazzi.filter="PreviewName"
```
