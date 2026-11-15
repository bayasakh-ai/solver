# Google Play: Data safety and App content answers

For Sudofolio 1.0 (`com.bay.sudokusolver`). Answers follow from what the build does;
the evidence is at the bottom. **Redo this page whenever the app gains the INTERNET
permission, an SDK that sends data, ads, accounts or crash reporting.**

Play Console wording changes over time, so match the answers below to the questions as
the Console words them on the day.

## Data safety form (App content > Data safety)

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not asked when the answer above is No. If asked: not applicable, nothing is transmitted |
| Do you provide a way for users to request that their data is deleted? | Not asked when the answer above is No. If asked: no account exists; uninstalling or Clear storage deletes everything |

Why "No" is the right answer:

- Google's definition of *collect* is transmitting data off the device. Data that is
  accessed and only processed on the device does not have to be declared.
- The camera picture, the handwriting read from it, the settings and the saved game
  never leave the phone. The app does not hold the INTERNET permission, so it cannot
  send them.
- No third-party SDK in the app collects data (no ads, analytics or crash reporting).

What the store listing will then show: "No data collected" and "No data shared with
third parties".

### One point to decide: Android backup

`android:allowBackup="true"` with the template's empty backup rules means Android's own
backup can copy the settings and the game in progress to the user's Google account.
Google's help pages do not say whether that counts as collection by the app; I could
not find a statement either way. The saved data contains no personal or sensitive
information (digits, option switches, a timer), so the answer above stands either way,
but two cleaner options exist:

1. Leave it (current). The privacy policy already describes it.
2. Exclude the app's data from cloud backup (`data_extraction_rules.xml`,
   `backup_rules.xml`), keeping device-to-device transfer. Then nothing leaves the
   phone by any route and the claim needs no footnote. Cost: a game in progress is not
   restored from a cloud backup on a new phone.

## Other App content declarations

| Section | Answer |
|---|---|
| Privacy policy | URL of the published `privacy-policy.md` (required even with no data collected; the policy must also be reachable inside the app) |
| Ads | No, the app does not contain ads |
| App access | All functionality is available without special access (no login) |
| Advertising ID | No, the app does not use an advertising ID |
| Content rating questionnaire | Category: game / puzzle. No violence, sexual content, profanity, drugs, gambling, user-to-user communication, location sharing or purchases |
| Target audience and content | Choose 13 and over. Including under-13 age groups brings in the Families policy requirements; the app is not designed for children |
| News app | No |
| Government app | No |
| Financial features | None |
| Health features | None |

## Permissions shown on the listing

| Permission | Where it comes from | Note |
|---|---|---|
| `CAMERA` | The app's manifest | Requested inside the app, after the welcome page explains why; the app works without it |
| `ACCESS_NETWORK_STATE` | Merged in by a library (not in the app's own manifest) | Lets code see whether a network exists; it cannot send data. Will appear on the listing as "view network connections". Can be removed with `tools:node="remove"` in the manifest, after checking on a phone that nothing depends on it |

No `INTERNET`, storage, location, microphone or contacts permission.

## Before submitting

- [ ] Fill in the developer name, contact email and date in `privacy-policy.md`.
- [ ] Publish the policy at a public, non-editable web address (not a PDF) and paste
      the URL into Play Console. GitHub Pages serving this `docs/` folder works if the
      repository is public.
- [x] The policy inside the app: Settings > About > Privacy policy shows
      `assets/privacy-policy.md`. After editing `docs/privacy-policy.md`, copy it there
      (`cp docs/privacy-policy.md android/app/src/main/assets/`); `PolicyTextTest`
      fails while the two differ.
- [ ] Decide the backup question above.
- [ ] Re-read the welcome page's promise ("no picture is saved, the app has no internet
      access") against the final build.

## Evidence (checked 2026-11-15)

- `android/app/src/main/AndroidManifest.xml`: `CAMERA` only.
- Merged debug manifest: `CAMERA`, `ACCESS_NETWORK_STATE`, and an internal androidx
  receiver permission. No `INTERNET`.
- Dependencies: CameraX, OpenCV, LiteRT, Compose, AndroidX. No ads, analytics or crash
  SDKs.
- Stored data: SharedPreferences for settings (`SettingsStore`) and the game in
  progress (`GameStore`). No files written; the Check photo stays in memory.

## Sources

- [Provide information for Google Play's Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
