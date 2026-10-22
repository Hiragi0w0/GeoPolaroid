# GeoPolaroid

Android 標準カメラで位置付き写真を1記録日につき最大12枚撮影し、04:00 を過ぎた記録日を「撮影場所のピン＋撮影順の直線」の地図で振り返るアプリ（新V0）。データはすべて端末内（Room + MediaStore）で管理する。

## セットアップ

1. Android Studio でプロジェクトを開く（minSdk 29 / targetSdk 37）。
2. Google Maps の Android 用 API キーを `local.properties` に設定する（ソースには書かない）。

   ```properties
   MAPS_API_KEY=AIza...
   ```

   キー未設定でもアプリは動作し、完成地図の代わりに写真一覧を表示する。キーには「Android アプリ」制限と「Maps SDK for Android」への API 制限をかけること。

## 構成

```
app/src/main/java/com/hiragi0w0/geopolaroid/
  MainActivity.kt / GeoPolaroidApplication.kt / AppContainer.kt  手動DI
  domain/    写真モデル、04:00 境界の記録日判定、地図モデル、各ポート（Android 非依存）
  capture/   撮影・保存シーケンスと PENDING 復旧（Android 非依存）
  data/      Room（photos 1テーブル）
  location/  FusedLocationProvider、Geocoder、地名整形
  media/     MediaStore への原本作成・検査・削除
  ui/        ホーム（SC-01）、完成地図（SC-03）、写真ダイアログ
```

## テスト

```
./gradlew :app:testDebugUnitTest
```

仕様とテストケースの対応・実機確認手順は [docs/TEST_CASES.md](docs/TEST_CASES.md)。

## V0 の暫定判断

- 撮影基準時刻は「位置取得後・カメラ起動直前」。03:59 に起動して 04:01 に撮影した写真は前日の記録日になる。
- 位置が取れない場合は撮影を開始しない。`getLastLocation()` は使わず、15秒より古い位置も採用しない。
- 時刻・タイムゾーン変更への特別対応はしない。撮影時点の zoneId を保存し、時刻表示に使う。現在より未来の記録日（時計を戻した場合など）はその日が来るまで一覧に出ない。
- 外部カメラが「キャンセル」を返しても中身のある画像が書かれていた場合、原本は消さず記録にも入れない。
- 端末バックアップ（allowBackup）は無効。DB だけ復元されて原本がない状態を避けるため。
- 画面デザインは暫定。
