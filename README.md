# LifeAdmin

An offline Android app that reminds you before documents expire and payments renew:
passports, visas, driving licences, subscriptions, bills, insurance, vehicle papers,
warranties and home maintenance.

- No account, no bank access, no SMS reading: everything stays on the phone.
- Reminders at the times you choose (90 / 30 / 7 / 1 days before, or on the day).
- Repeating items (monthly, yearly, ...) with optional automatic renewal.
- Monthly spend on recurring items at a glance.

## Getting a test build on your phone

Every push to `main` builds the app automatically on GitHub.

1. Open the repository's **Actions** tab and click the latest green **Build app** run.
2. Under **Artifacts**, download **LifeAdmin-test-apk** (a .zip) and unzip it.
3. Copy the `.apk` to your Android phone and open it. Allow "Install unknown apps" if asked.

New test builds install over the old one and keep your data.

## Tech

Kotlin, Jetpack Compose (Material 3), Room for local storage, WorkManager for the
daily reminder check. Minimum Android 8.0.
