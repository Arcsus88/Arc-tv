# Release signing

Release builds are signed with a keystore that is **not** committed to the
repository. Signing configuration is read (in this order) from:

1. `keystore.properties` at the repo root (gitignored) — for local builds:

   ```properties
   storeFile=release.keystore
   storePassword=…
   keyAlias=arctv
   keyPassword=…
   ```

2. Environment variables — used by CI:

   | Variable            | Meaning                                  |
   |---------------------|------------------------------------------|
   | `KEYSTORE_FILE`     | Path to the keystore, relative to repo root |
   | `KEYSTORE_PASSWORD` | Keystore password                        |
   | `KEY_ALIAS`         | Key alias (`arctv`)                      |
   | `KEY_PASSWORD`      | Key password                             |

If neither is present, `assembleRelease` produces an unsigned APK.

## GitHub Actions release flow

Pushing a tag matching `v*` (e.g. `v1.1.0`) triggers `.github/workflows/release.yml`,
which builds the signed APK and attaches it to a GitHub Release. Before the
first tagged release, add these **repository secrets**
(Settings → Secrets and variables → Actions):

| Secret              | Value                                        |
|---------------------|----------------------------------------------|
| `KEYSTORE_BASE64`   | `base64 -w0 release.keystore`                |
| `KEYSTORE_PASSWORD` | the keystore password                        |
| `KEY_ALIAS`         | `arctv`                                      |
| `KEY_PASSWORD`      | the key password                             |

Keep a copy of `release.keystore` somewhere safe — the in-app updater only
works while every release is signed with the same key. Remember to bump
`versionCode`/`versionName` in `app/build.gradle.kts` so installed apps detect
the new release (the update banner compares the release tag against
`versionName`).
