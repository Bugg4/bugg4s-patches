# OPL Monitor — development notes

Notes for maintaining the patches for `com.insigniadpfgmailcom.oplmonitor` (OPL Monitor —
Opel/Vauxhall/Chevrolet diagnostics app: DTC reading/clearing and DPF monitoring over
ELM327-based OBD2 dongles).

## Target app

- Package: `com.insigniadpfgmailcom.oplmonitor`
- Target version in this repo: `1.0.3.65` (versionCode 65)
- Distribution: XAPK (base APK + `config.arm64_v8a` native split + language/density splits)
- minSdk 23, targetSdk 35, arm64-v8a only
- Launcher name: "OPL Monitor"; icon: dark tile with orange (#E54701) accents

## Architecture (important!)

The app is a **.NET MAUI (Xamarin)** application. All business logic is C# compiled into
`libassemblies.arm64-v8a.blob.so` + `libaot-*.dll.so` inside the native split. The Dalvik
layer only contains Xamarin/Mono glue (`mono.*`, `crc64*` classes, `functionexecute`).

Consequences:

- Morphe patches can modify the **manifest, resources and Dalvik bytecode** only.
  They **cannot** change C# behavior (features, update checks, network fetching, purchases).
- Hooking third-party Java SDKs that the C# code calls (e.g. the AdMob SDK) is the only
  way to influence app behavior, and only for SDK-mediated features.

## Protection: PairIP

`com.pairip.licensecheck` (LicenseContentProvider → LicenseClient) runs at app startup.
It checks that the installer is the Play Store and verifies the license with Play.

- Repacked/re-signed builds may show a license dialog or exit.
- **Verify the patched app launches on first device test before building out more patches.**
- License/tamper logic is intentionally left untouched by these patches.

## Ads

- Google Mobile Ads SDK (unobfuscated), driven from C# through `Plugin.MauiMTAdmob`
  (Java callback glue in `crc64509fec87287e985b.*`, e.g. `InterstitialService`,
  `RewardService`, `AppOpenAdManager`, `NativeAdManager`, `UMPImplementation`).
- Ad formats referenced by the app: BANNER, INTERSTITIAL, REWARDED; ads also appear in
  the gauges panel and the DTC-clearing flow (`GaugesPanelRun*Ad`, `ClearDtcAdsShowMessage`).
- Ad unit IDs live in the .NET assemblies blob.
- The app has an ad-free IAP (`OplMonitorAdFree1yPeriod`) — purchase logic is untouched.

## Update checks

- C#-driven; update-related resource keys: `DontUpdatePanel`, `AppWasUpdated`,
  `FirmwareUpdate*` (dongle firmware notices), `VersionCheckFail*`.
- Exact comparison logic is not known (inside the .NET assemblies).
- `SpoofAppVersionPatch` (manifest `versionName`) is a best-effort mitigation.
  Next iteration if it fails: also spoof `versionCode`, or fall back to removing internet.

## Network-dependent features

Relevant when disabling network access (e.g. "Remove internet permission" patch):

| Feature | Resource keys | Impact if offline |
|---|---|---|
| DTC descriptions download | `DownloadDtcDesc`, `DtcDownloadErrorMessage` | Descriptions unavailable |
| VIN decode + gauge download | `DecodeVinOnline`, `VinDecodeMessageDownloadingGauges`, `VinDecodeFailedNoDecodeData` | VIN auto-setup and gauges unavailable |
| Paid "Function" packages | `FunctionDownloadingSoftware`, `FunctionCliCanNotDownload` | Purchased functions cannot run |
| Ads / update checks / telemetry | — | Disabled (desired) |

## Patches in this repo

| Patch | File | Default | Notes |
|---|---|---|---|
| Remove ads | `patches/src/main/kotlin/app/bugg4/patches/oplmonitor/ads/RemoveAdsPatch.kt` | on | No-ops all Google Mobile Ads load methods (banner/interstitial/rewarded/rewarded interstitial/app open/native) |
| Spoof app version | `.../misc/SpoofAppVersionPatch.kt` | off | Manifest `versionName` option (default `9.9.9`) |
| Remove internet permission | `.../misc/RemoveInternetPermissionPatch.kt` | off | Removes `android.permission.INTERNET`; see table above |

## Testing checklist

Use [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop/releases/latest) and the
original XAPK:

1. Apply **Remove ads** only → install → verify the app launches (PairIP!) → verify no ads
   (gauges panel, DTC flow, interstitials) → check VIN decode still works (needs internet).
2. Add **Spoof app version** → verify no update prompt appears (about screen shows spoofed version).
3. If the update prompt persists → iterate (spoof `versionCode` too, or reconsider internet removal).
