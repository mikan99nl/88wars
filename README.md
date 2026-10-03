# 88Wars (Paper 1.21.11)

ポイント制ミニゲームプラグイン。第1弾: **Randomizer** (DUO = 2人1組・ボーダーあり / TEAM = チーム戦・ボーダーなし)
(Spleef / Walls / Wool Wars / Survival Games / Skywars は投票メニューに「準備中」で枠だけ用意済み)

## 導入
1. `88Wars.jar` を `plugins/` に入れて起動 (Java 21)
2. ゲーム内で OP として:
   - `/wars setlobby` … ロビー地点
   - `/wars sethologram` … ランキングホログラム位置
   - `/wars setpodium 1` `2` `3` … 表彰台 (優勝者のマネキンが出る位置)
   - アリーナ: 平らな場所(61x61を上書き)に立って `/wars admin` → 「アリーナ一覧」→「現在地に Randomizer アリーナを新規生成」
     (コマンドなら `/wars arena create a1 randomizer` → `/wars arena build a1`)
   - **生成・貼り付け直後のアリーナは「編集中」** で試合に使われません。準備ができたら
     `/wars arena enable a1` (またはアリーナ一覧で右クリック) で有効化。
   - 自作マップを使う (WorldEdit 必須):
     1. 作ったマップを `//wand` などで範囲選択し、**中心(中央5x5の真ん中)に立って** `/wars map save mymap`
        → `plugins/88Wars/maps/mymap.schem` に保存
     2. 貼り付けたい場所に立って `/wars arena paste a2 mymap` (立ち位置=マップ中心。`//undo` で取り消し可)
     3. スポーン地点に立って `/wars arena addspawn a2` を繰り返す
        (スポーンはマップ側にも記録され、次に同じマップを貼るときは自動で入ります)
     4. `/wars arena enable a2`
3. `/wars admin` で各種数値・切替をGUIから変更可能 (config.yml に保存)
   - 「人数が揃ったら自動開始」(`lobby.auto-start`) を OFF にすると `/wars start` でのみ開始
4. 2人以上がロビーにいるとカウントダウン開始。星(投票メニュー)で1票 or ランダム抽選。

## 主なコマンド
`/wars admin` `/wars start [mode]` `/wars stop` `/wars vote` `/wars top` `/wars points [name]`
`/wars arena create|build|paste|addspawn|clearspawns|enable|disable|delete|tp|list` `/wars map save|list|delete`
`/wars setpoints|addpoints <player> <n>`

## ビルド
`mvn package` → `target/88Wars.jar`
