# GeoPolaroid

Android 標準カメラで位置付き写真を1記録日につき最大12枚撮影し、04:00 を過ぎた記録日を「撮影場所のピン＋撮影順の直線」の地図で振り返るアプリ（新V0）。データはすべて端末内（Room + MediaStore）で管理する。

## セットアップ

1. Android Studio でプロジェクトを開く（minSdk 29 / targetSdk 37）。
2. Google Maps の Android 用 API キーを `local.properties` に設定する（ソースには書かない）。

   ```properties
   MAPS_API_KEY=AIza...
   ```

   キー未設定でもアプリは動作し、完成地図の代わりに写真一覧を表示する。キーには「Android アプリ」制限と「Maps SDK for Android」への API 制限をかけること。

## release 版の署名

鍵ファイルとパスワードはリポジトリに含めない（`*.jks` / `*.keystore` / `local.properties` は `.gitignore` 済み）。

1. Android Studio の「Build → Generate Signed App Bundle / APK → APK → Create new...」で鍵（`.jks`）を作る。保存先はリポジトリの外を推奨。
2. `local.properties` に4項目を書く。`RELEASE_STORE_FILE` は絶対パスか、プロジェクトのルートからの相対パス。

   ```properties
   RELEASE_STORE_FILE=/path/to/geopolaroid-release.jks
   RELEASE_STORE_PASSWORD=...
   RELEASE_KEY_ALIAS=...
   RELEASE_KEY_PASSWORD=...
   ```

3. `./gradlew :app:assembleRelease` で `app/build/outputs/apk/release/app-release.apk` ができる。

- 4項目すべて未設定なら、release 版は署名なしでビルドされる（インストールはできない）。一部だけ設定した場合と鍵ファイルが見つからない場合はビルドを止める。
- 鍵を失うと同じアプリとして上書き更新できなくなり、アンインストールすると記録（DB）は戻らない。鍵ファイルとパスワードはリポジトリの外にもバックアップすること。
- debug 版と release 版は署名が違うため、切り替えるときはアンインストールが必要になる。
- Maps API キーに「Android アプリ」制限をかけている場合は、release 鍵の SHA-1（`./gradlew signingReport` で確認）も登録する。

## 構成

```
app/src/main/java/com/hiragi0w0/geopolaroid/
  MainActivity.kt / GeoPolaroidApplication.kt / AppContainer.kt  手動DI
  domain/    写真モデル、04:00 境界の記録日判定、地図モデル、各ポート（Android 非依存）
  capture/   撮影・保存シーケンスと PENDING 復旧（Android 非依存）
  home/      ホームの状態と撮影フロー HomeController（Android 非依存、ViewModel から利用）
  map/       完成地図の状態 MapController（Android 非依存、ViewModel から利用）
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
- 端末バックアップ・端末間移行の対象から外している（allowBackup と dataExtractionRules）。DB だけ移って原本と対応しない状態を避けるため。
- 記録日は画面復帰時に加え、画面を開いたままでも次の 04:00 に切り替わる。アプリ外で削除された原本は画面復帰時に再検査する。
- 画面デザインは暫定。
