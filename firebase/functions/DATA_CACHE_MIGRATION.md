# Data cache migration

Deploy the additive Functions changes before you release a client that uses the sync manifest.

## Local authentication

The backfill uses Google Application Default Credentials. A Firebase CLI login alone is not sufficient.

```bash
gcloud auth application-default login
```

The script uses `mahjong-tournament-suite` by default. Set `EMA_FIREBASE_PROJECT` to use another project.

```bash
EMA_FIREBASE_PROJECT=another-project npm run backfill:data-versions
```

## Deployment order

1. Build and test the Functions package.

   ```bash
   cd firebase/functions
   npm test
   ```

2. Deploy the Functions package.

3. Preview the Firestore backfill. This command does not write data.

   ```bash
   npm run backfill:data-versions
   ```

4. Review the reported tournament and table counts.

5. Apply the backfill.

   ```bash
   npm run backfill:data-versions:apply
   ```

6. Release the client applications.

The backfill adds missing revision fields, table versions, and table summary fields. It does not change business timestamps.

The old table and hand update routes remain available for older clients. They also update the new revision data.
