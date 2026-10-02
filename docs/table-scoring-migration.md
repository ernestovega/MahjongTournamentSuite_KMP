# Table scoring migration

The migration converts legacy tables with `usePointsCalculation=false` to Manual Scores.
It preserves every hand document. It writes a JSON audit before apply mode can update tables.

Run a report first:

```bash
EMA_FIREBASE_PROJECT=mahjong-tournament-suite \
npm --prefix firebase/functions run migrate:table-scoring -- \
  --project=mahjong-tournament-suite
```

Review the audit file in `firebase/functions/migration-audits/`.
Apply only after review:

```bash
EMA_FIREBASE_PROJECT=mahjong-tournament-suite \
npm --prefix firebase/functions run migrate:table-scoring:apply -- \
  --project=mahjong-tournament-suite \
  --confirm-project=mahjong-tournament-suite
```

Apply mode stops before any write when the project confirmation does not match.
Tables without four integer scores remain in the audit with a skip reason.
