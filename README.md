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
3. `/wars admin` で各種数値・切替をGUIから変更可能 (config.yml に保存)
4. 2人以上がロビーにいるとカウントダウン開始。星(投票メニュー)で1票 or ランダム抽選。

## 主なコマンド
`/wars admin` `/wars start [mode]` `/wars stop` `/wars vote` `/wars top` `/wars points [name]`
`/wars arena create|build|addspawn|clearspawns|delete|tp|list` `/wars setpoints|addpoints <player> <n>`

## ビルド
`mvn package` → `target/88Wars.jar`
