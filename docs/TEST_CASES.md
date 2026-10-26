# V0 テストケースと検証結果

設計書「新V0 詳細設計 v0.2｜端末内完結・04:00完成地図」9章（T01〜T12）と5〜7章から洗い出したテストケース。

- **自動**: `app/src/test` の JVM ユニットテスト（`./gradlew :app:testDebugUnitTest`）
- **実機**: エミュレーター／実機での手動確認が必要な項目（外部カメラアプリ・MediaStore・地図SDKの実挙動）

## 自動テスト

| 仕様 | ケース | テスト | 期待結果 |
|---|---|---|---|
| 5章 / T07 | 03:59:59 は前日、04:00 は当日の記録日 | `RecordDayTest.before0400BelongsToPreviousDay` / `at0400BelongsToSameDay` | 境界どおりに判定 |
| 5章 | 月末・年末・うるう日をまたぐ 04:00 前 | `RecordDayTest.monthAndYearBoundaries` | 前日（12/31, 2/29） |
| 5章 | 撮影当時のタイムゾーンで判定（UTCではない） | `RecordDayTest.usesGivenZoneNotUtc` | ゾーンごとに異なる記録日 |
| 5章 | 夏時間の切替日もローカル時刻 04:00 で判定 | `RecordDayTest.dstTransitionUsesLocalWallClock` | 03:30=前日、04:00=当日、重複時刻も前日 |
| 5章 / T07 | `recordDate < 現在の記録日` のときだけ完成 | `RecordDayTest.completedOnlyWhenBeforeCurrentRecordDate` / `completionSwitchesAcross0400` | 03:59 未完成 → 04:00 完成 |
| 6章 / T02 | 撮影成功で SAVED 1件・MediaStore 1件、基準時刻・TZ・座標を保存 | `CaptureCoordinatorTest.successfulCaptureSavesOneRowAndOneMediaItem` | PENDING → SAVED、placeName は null |
| 6章 / T03 | キャンセルで SAVED が増えず、空の URI も残らない | `cancelLeavesNoSavedRowAndNoGarbageUri` | 行・項目とも 0 |
| 6章 / T03 | 成功 true でも空ファイル／欠落なら保存しない | `successReportedButEmptyFileIsTreatedAsFailure` / `successReportedButMediaMissingIsFailure` | FAILED、行・項目とも片付け |
| 6章-6 | キャンセルでも中身のある原本は消さない（枠は返す） | `cancelKeepsRealImageButReleasesSlot` | 行のみ削除、原本は残る |
| 6章-6 | 成功後に検査不能なら PENDING のまま残す | `unverifiableAfterSuccessStaysPending` | UNVERIFIED、行・原本とも残る |
| 6章 | 完了処理の二重実行で状態が壊れない | `completeIsIdempotent` | ALREADY_HANDLED |
| 4章 / T04 | 12枚で撮影不可（位置取得もしない）、1件除外で再撮影可能、原本は残る | `twelvePhotosBlockCaptureAndExclusionReopensIt` | LimitReached → 除外 → 12枚目を保存 |
| 4章 | PENDING も上限に数え、キャンセルで枠が戻る | `pendingCountsTowardLimit` | 12件目 PENDING 中は LimitReached |
| 4章 | 上限は記録日ごと | `limitIsPerRecordDate` | 翌記録日は再び撮影可能 |
| 4章 | 位置取得中に上限へ達したら作成済みの空項目を消す | `limitRaceAfterLocationDeletesCreatedMedia` | LimitReached、項目 0 |
| 6章-2 / T05 | 位置情報の権限拒否で何も作らない | `locationPermissionDeniedCreatesNothing` | 行・項目とも 0 |
| 6章-2 / T05 | 位置取得失敗で何も作らず、再試行で撮影できる | `locationUnavailableCreatesNothingAndCanRetry` | 失敗後の再試行で新しい座標を保存 |
| 8章 | 保存先（MediaStore）を作れないとき行を作らない | `storageFailureCreatesNoRow` | StorageUnavailable |
| 4章 | 撮影準備の多重実行を拒否（カメラ多重起動防止） | `concurrentPrepareIsRejected` | 2回目は Busy、行は1件 |
| 5章 / T07 | 記録日はカメラ起動直前の時刻で決まる | `recordDateUsesTimeJustBeforeCameraLaunch` | 03:59 → 前日、04:00 → 当日 |
| 6章-6 / T11 | 起動時復旧：読める画像は SAVED、空・欠落は片付け、検査不能は残す | `recoveryRestoresValidDiscardsEmptyAndKeepsUnverifiable` | recovered / discarded / kept に分類 |
| 6章-6 / T11 | 復旧は結果待ちのカメラの行を対象外にする | `recoverySkipsActiveCameraPhoto` | kept、後から SAVED にできる |
| T11 | プロセス再生成で復旧が先に走り、その後にカメラ結果が届いても1件のまま | `resultAfterRecoveryIsHandledOnce` | ALREADY_HANDLED、SAVED 1件 |
| 6章-5 | 地名取得成功で placeName のみ更新 | `PlaceNameTest.successUpdatesOnlyPlaceName` | 対象行のみ更新 |
| 6章-5 / T06 | 地名取得失敗でも写真・座標は残る | `failureKeepsPhotoAndCoordinates` / `resolverExceptionIsSwallowed` | SAVED のまま、placeName は null |
| 2章 | 地名は市区町村程度に整形 | `formatterBuildsCityLevelName` | 「福岡市中央区」「渋谷区」等 |
| 7章 | 0枚：ピン・線・範囲なし | `RouteMapModelTest.emptyDayHasNoPinsNoLineNoBounds` | すべて空 |
| 7章 / T08 | 1枚はピンのみで線なし | `singlePhotoHasPinOnlyWithoutLine` | polyline 空 |
| 7章 | 日付変更線をまたぐ写真は短い側の範囲で表示（179.9° と -179.9° → 約0.2°） | `boundsAcrossAntimeridianUseTheShortSide` / `boundsAcrossAntimeridianWithSeveralPoints` / `ordinaryBoundsAreUnchanged` | 世界全体に縮小しない |
| 7章 / T08 | 2枚以上は撮影順の直線 | `multiplePhotosAreConnectedInCaptureOrder` / `sameTimestampIsOrderedStablyByPhotoId` | 基準時刻→photoId 順 |
| 7章 | PENDING は地図に描かない | `pendingRowsAreNotDrawn` | SAVED のみ |
| 3章 / T09 | 完全に重なるピンも分離せず全件保持 | `overlappingPinsAreAllKept` | 2ピン（一覧から到達可能） |
| 3章 / T10 | 除外後の再描画でピンと線から消える | `excludedPhotoDisappearsOnRedraw` | 残りの写真だけを接続 |
| 4章 | SQL：一覧は記録日・SAVED で絞り、基準時刻→photoId で安定ソート | `PhotoSqlTest.savedByDateIsFilteredAndOrdered` | 実 SQLite で検証 |
| 4章 | SQL：上限判定は当日の SAVED + PENDING | `activeCountIncludesPendingAndSavedOfThatDateOnly` | 2件 |
| 4章 | SQL：過去日付は SAVED 行の DISTINCT、PENDING だけの日は出ない | `savedDatesAreDistinctNewestFirstAndSkipPendingOnlyDays` | 新しい順・件数付き |
| 4章 | SQL：状態遷移と削除は状態を越えない | `markSavedOnlyAffectsPending` / `deletePendingAndExcludeSavedDoNotCrossStates` | 対象外は 0 件 |
| 3章 | 表示：撮影当時の TZ で時刻、日付は曜日付き | `DisplayFormatTest` | 「10:05」「2026年10月22日(木)」 |
| 5章 | 次の 04:00（夏時間の日を含む）を求める | `RecordDayTest.nextBoundaryIsUpcomingLocal0400` | 次のローカル 04:00 |
| 6章-5 | 地名取得中の id を公開し、DB エラーでも取得中のまま残らない | `PlaceNameTest.serviceTracksResolvingIdsAndSurvivesRepositoryErrors` | 完了後に空集合 |
| T01 | 初回：0/12・空状態・撮影可能、空の日次レコードを作らない | `HomeControllerTest.initialStateIsEmptyAndCapturable` | 行 0 件 |
| T02 | ホームの撮影フロー：準備→カメラ要求（1回だけ消費）→保存→地名補完 | `captureFlowSavesPhotoAndResolvesPlaceName` | 1枚・地名あり・撮影中 id クリア |
| 4章 | 連打・カメラ起動中のタップで準備は1回だけ | `repeatedTapsPrepareOnlyOnce` | 位置取得 1 回、PENDING 1 件 |
| T03 | キャンセルでメッセージ表示、枠と空項目を返す | `cancelShowsMessageAndFreesSlot` | 再び撮影可能 |
| 8章 | カメラアプリなしで後片付け | `cameraLaunchFailureCleansUp` | CAMERA_NOT_FOUND、行 0 件 |
| 6章 | 撮影中でないときのカメラ結果は無視 | `resultWithoutActiveCaptureIsIgnored` | 変化なし |
| T05 | 権限拒否・位置取得失敗は再試行可能なメッセージ | `locationFailuresShowRetryableMessages` | 行・項目とも 0 |
| T04 | 12枚で撮影不可、除外で再び可能（原本は残る） | `limitDisablesCaptureUntilExclusion` | canCapture が戻る |
| T11 | プロセス再生成後、SavedState の撮影中 id は復旧対象外で、結果到着時に保存 | `restoredActivePhotoIsNotRecoveredAndCompletesOnResult` | SAVED 1件 |
| T11 | 起動時復旧が遅くても、撮影準備は復旧完了を待つ | `recoveryFinishesBeforeNewCaptureIsPrepared` | 新しい PENDING が消されない |
| 5章 / T07 | 画面を開いたまま 04:00 を跨ぐと記録日が切り替わる | `recordDateSwitchesAt0400WhileOpen` | 前日が「過去の記録」へ |
| T07 | 前日が12枚でも 04:00 後は撮影可能になる | `fullPreviousDayDoesNotBlockNewDayAfterBoundary` | canCapture が true |
| T12 | アプリ外で原本を削除すると復帰時に欠落表示、除外できる | `externallyDeletedOriginalIsDetectedOnResume` | missingPhotoIds に含まれる |
| 4章 / T03 | 空項目の削除が一時的に失敗しても、再起動を待たずに再試行して枠を戻す（自動の再試行・画面復帰時） | `slotIsFreedByRetryWhenEmptyItemDeletionFailsOnce` / `resumeRetriesLeftoverCleanup` | 11枚＋キャンセルで再び撮影可能 |
| 6章-6 | 残った PENDING の片付けは、準備中の撮影の行を消さない（同じロックで1つずつ実行） | `leftoverCleanupNeverDiscardsCaptureBeingPrepared` | 新しい PENDING とその項目が残る |
| 6章 | 現在地取得中にアプリを離れたら、戻ってもカメラを自動起動せず中止・片付け | `leavingAppDuringLocationFixAbortsInsteadOfLaunchingLater` / `unconsumedCameraRequestIsAbortedOnStop` | CAPTURE_ABORTED、行・項目 0 件 |
| 6章 | カメラ起動後の画面停止（カメラが前面）では中止しない | `stopAfterCameraLaunchDoesNotAbort` | 結果到着で保存 |
| 6章 | 画面回転などの構成変更では撮影を中止しない | `configurationChangeDuringPreparationDoesNotAbort` | 新しい画面が要求を消費できる |
| 6章-5 / T06 | 起動時に地名未取得の SAVED 写真を再取得（取得済みは呼ばない） | `missingPlaceNamesAreRetriedOnStart` | 未取得分だけ補完 |
| 6章-5 | 未取得が21件以上でも古い写真を取り残さず全件再取得 | `allMissingPlaceNamesAreRetriedEvenBeyondTwenty` | 25件すべて補完 |
| 6章-5 | 一括再取得は1件ずつ順に行い、取得中の写真は重複させない | `PlaceNameTest.bulkResolutionRunsOneAtATimeAndSkipsInProgressIds` | 同時実行は一括側1件 |
| 6章-5 | 一括再取得が中断されても「取得中」のまま残らない | `cancelledBulkResolutionDoesNotLeaveIdsResolving` | resolvingIds が空 |
| 6章-5 | 同じ写真の地名取得を重複して走らせない | `PlaceNameTest.duplicateRequestsWhileResolvingAreIgnored` | Geocoder 呼び出し 1 回 |
| 4章 | SQL：地名未取得の SAVED の id を新しい順に件数制限なしで取得 | `PhotoSqlTest.savedIdsWithoutPlaceNameAreNewestFirstWithoutLimit` | PENDING・取得済みを除外 |
| 7章 / T08 | 完成地図はその記録日の SAVED のみを撮影順に描く | `MapControllerTest.showsOnlySavedPhotosOfThatDateInCaptureOrder` | 他日付・PENDING を除外 |
| 3章 / T10 | 地図画面での除外でピンと線から消え、原本は残る | `excludeRemovesPinAndLineButKeepsOriginal` | 原本の削除なし |
| 8章 | 地図画面での除外失敗を通知 | `excludeFailureIsReportedAndDismissable` | excludeFailed |
| T12 | 地図画面でも復帰時に原本の欠落を検出 | `MapControllerTest.externallyDeletedOriginalIsDetectedOnResume` | missingPhotoIds |
| 6章-5 | 地図画面にも地名取得中の状態を反映 | `placeNameResolvingStateIsPropagated` | resolvingPlaceIds |
| 6章-6 | 空項目を削除できなかったときは PENDING 行を残し、次回の復旧で再試行（原本が元から無ければ行を消す） | `cancelKeepsRowWhenEmptyItemCannotBeDeleted` / `failedSaveKeepsRowWhenEmptyItemCannotBeDeleted` / `recoveryKeepsRowWhenEmptyItemCannotBeDeleted` / `missingItemRowIsRemovedEvenThoughDeleteReportsNothing` | 参照されない空項目を残さない |
| 6章-6 | 片付けが途中で止まっても行が残り、次回の復旧で片付く（原本→行の順） | `CaptureCoordinatorTest.interruptedCleanupKeepsRowSoRecoveryCanFinishIt` / `interruptedRecoveryDiscardKeepsRow` | PENDING 行が残る |

### 実行結果（2026-10-22）

- 90 件すべて成功（初回 48 件、レビュー1回目の改修で 15 件、2回目で 5 件、3回目で 1 件、4回目で 3 件、6回目で 5 件、PR レビュー対応で 13 件追加）。
- テストの検出力確認として、境界判定（`<`→`<=`）、上限比較（`>=`→`>`）、キャンセル時の削除条件、上限カウント SQL、1枚時の線の抑止、04:00 タイマー、復旧完了待ち、復帰時の原本再検査、撮影中の再タップ防止、背面での撮影中止、片付けの順序をそれぞれ意図的に壊すと、対応するテストが失敗することを確認した。
- 注意：作業環境から Google Maven（dl.google.com）に到達できないため、Android 依存部分（Room / Compose / Play Services / Maps）はこの環境でコンパイルしていない。上記はドメイン層・ホーム／完成地図の状態管理（HomeController / MapController）・SQL を Kotlin/JVM で実行した結果。Android Studio でのビルドと `./gradlew :app:testDebugUnitTest` での再実行が必要。

## 実機・エミュレーターで確認する項目

| 仕様 | 手順 | 期待結果 |
|---|---|---|
| T01 | 初回起動 | 記録日・「0 / 12 枚」・空状態・撮影ボタン・過去の記録ボタン |
| T02 | 位置情報を許可して撮影 | 一覧に写真・時刻（カメラ起動時）・地名（取得中→地名）。写真アプリの Pictures/GeoPolaroid に1枚 |
| T03 | カメラで戻る／キャンセル | 一覧・枚数は変わらず、写真アプリに空の項目が残らない |
| T04 | 12枚撮影 | ボタンが無効「この記録日は12枚に達しました」。写真を開き「記録から外す」で再び撮影可能 |
| T05 | 位置情報を拒否／端末の位置情報をオフ | カメラは起動せず、再試行・設定を開くを表示 |
| T06 | 機内モードで撮影 | 写真は保存、「場所名を取得できませんでした」 |
| T07 | 端末時刻を 03:59 → 04:00 に、またはアプリをバックグラウンドから復帰 | 前日の記録が「過去の記録」に移り、地図で開ける |
| T08 | 1枚／複数枚の日を開く | 1枚はピンのみ、複数はピン＋直線 |
| T09 | ピンをタップ／同位置に複数撮影 | 写真・時刻・地名のダイアログ。重なったピンは下の一覧から開ける |
| T10 | 地図画面で写真を除外 | ピンと線から消える。写真アプリの原本は残る |
| T11 | カメラ起動中にプロセスを終了（開発者オプション「アクティビティを保持しない」等）して撮影→戻る | 写真が SAVED として1件だけ表示 |
| 6章-6 | 撮影中にカメラアプリを強制終了するなどで途中まで書かれたファイルを作り、アプリを再起動 | 画像データまで読めないファイルは SAVED にせず片付ける（縮小デコードで検査） |
| T12 | 写真アプリで原本を削除／機内モードで地図を開く／MAPS_API_KEY 未設定 | 「元写真が見つかりません」表示で除外可能。地図が出なくても一覧で閲覧でき、クラッシュしない |
