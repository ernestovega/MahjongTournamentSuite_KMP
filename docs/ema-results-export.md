# EMA results export

The Tournament screen has an `Export` action. It saves a UTF-8 CSV file that opens in Excel.

The file keeps the column order used by the original Windows app:

1. Place
2. First Name
3. Last name
4. EMA number
5. Table points
6. Score
7. Ema Member
8. Country

The export preview shows the tournament period. New tournaments require start and end dates in `YYYY-MM-DD` format. The end date cannot be before the start date. The file name includes both dates. Legacy tournaments use their stored single date for both dates, or their creation date as a fallback.

The export stops when a tournament slot has no assigned EMA player. This prevents incomplete EMA submissions. The export reads names and countries from the assigned shared EMA player record. It does not use stale slot names or countries.

The original Windows app did not write an Excel file. Its `EMA Report` action showed a grid for manual copying. The new `EMA report` action shows the same grid and the tournament period, then saves the CSV. It uses CSV because the project has no Excel library and because CSV works on Android, Desktop, and Web.

EMA asks organizers to use an Excel sheet and verify all EMA IDs before sending results. Confirm the CSV columns against the current EMA template before submission.
