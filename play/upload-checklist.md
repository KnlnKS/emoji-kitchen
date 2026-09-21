# Emoji Kitchen — Google Play upload kit

App: Emoji Kitchen · com.kunalan.emojikitchen
Developer: KKS - Games
Release: 1.0.4 · version code 4

## 1. Store listing

Open https://play.google.com/console/u/0/developers/5880801109692258739/app/4974684541277526581/store-listings/default/edit

Upload the files from `01-store-listing`:

| Console field | File | Size |
|---|---|---|
| App icon | app-icon-512.png | 512 × 512 |
| Feature graphic | feature-graphic-1024x500.png | 1024 × 500 |
| Phone screenshots | phone-screenshots/01-mix.png through 04-dark-mode.png, in numbered order | Each 1080 × 1920 |

The listing text is already saved as a draft. `listing-copy.md` includes a backup of the copy, privacy-policy URL, and support details. Leave the optional video field blank. Add assets, review the listing, and save.

## 2. App bundle

In Test and release → Production → Create new release, upload:

`02-release/emoji-kitchen-1.0.4.aab`

This is the signed release bundle from GitHub CI run 35612781255, with the complete offline sticker pack. Upload the AAB, not an APK. Use release name `1.0.4`. The `release-notes.txt` file is ready to paste into the release-notes field.

`02-release/mapping.txt` is the matching R8 deobfuscation file, if Play requests it. Do not put it in the app-bundle upload field. The SHA256SUMS.txt file can be used to verify that the upload kit has not changed.

After upload, complete the country availability and any remaining Console requirements. Review the release, then send the changes for review from Publishing overview. Uploading files alone does not publish the app; Google must review and approve the submission.

## 3. Two remaining setup actions

- Audience: all six age groups have been selected in the open audience form, but the all-ages change is not saved yet. The children’s compliance certification must be confirmed and the form saved. The submitted content-rating questionnaire returned ESRB Everyone; other regions have their own ratings.
- Automatic releases: the GitHub workflow and scoped publisher secret are configured. Google Play Android Developer API enablement and its terms remain pending in Cloud project `emoji-kitchen-509314`. After the first manual release is established and the API is enabled, validate the automation with a fresh CI run. Do not re-upload version code 4 after Play has accepted it.

## Asset alt text

- Icon: A smiling yellow cooking pot with steam on a coral background.
- Feature graphic: An orange flower plus a sunglasses emoji becomes a smiling sticker wearing sunglasses and a flower.
- 01: Combine mode mixes an orange flower and a sunglasses emoji into one sticker.
- 02: Explore mode shows a grid of flower-themed emoji combinations.
- 03: Saved favorite stickers appear above the emoji picker in Combine mode.
- 04: Explore emoji combinations with the app’s dark theme.

## Design notes

The feature graphic uses the existing brand palette, large emoji artwork, a clear mixing equation, and short readable copy. Screenshots are direct captures of the signed release build; no invented screens or controls. Their clean status bar, 9:16 aspect ratio, and four-image sequence follow Google Play’s preview-asset recommendations. The feature graphic is AI-assisted promotional artwork based on an actual supported combination.

Sources checked September 21, 2026:
- Google Play preview asset requirements: https://support.google.com/googleplay/android-developer/answer/9866151
- Category reference, 10M+ installs: https://play.google.com/store/apps/details?id=com.emoji.merge.makeover.diy.mixer.funny
- Category reference, 1M+ installs: https://play.google.com/store/apps/details?id=emoji.merge.diy.mixer

Download counts show reach, not proof that any particular design caused conversions. No competitor artwork is included in this kit.
