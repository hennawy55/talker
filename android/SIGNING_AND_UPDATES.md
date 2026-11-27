# Android signing and update policy

The Android package identity is fixed at `com.talker.offline`. The Gradle build reads a monotonically increasing `talkerVersionCode` and a display `talkerVersionName`; CI supplies the GitHub Actions run number so each new workflow run gets a higher version code.

## Required one-time repository secrets

Create one persistent upload key and store its values as GitHub Actions repository secrets:

- `TALKER_KEYSTORE_BASE64`: base64-encoded JKS/PKCS12 keystore bytes
- `TALKER_KEYSTORE_PASSWORD`: keystore password
- `TALKER_KEY_ALIAS`: key alias
- `TALKER_KEY_PASSWORD`: key password

Generate a key locally (keep the keystore private and backed up):

```bash
keytool -genkeypair -v -keystore talker-release.jks -alias talker -keyalg RSA -keysize 4096 -validity 10000
# Linux/macOS:
base64 -w 0 talker-release.jks
# PowerShell:
[Convert]::ToBase64String([IO.File]::ReadAllBytes("talker-release.jks"))
```

In GitHub: Settings -> Secrets and variables -> Actions -> New repository secret. Add the four values above. Never commit the keystore or its passwords.

## Important migration note

The first APK produced by the original debug-only workflow was signed with GitHub runner's temporary Android debug key. GitHub-hosted runners do not preserve that key between runs, so it cannot be used as a reliable update signer. The first APK signed with the persistent key may require a one-time uninstall of that old debug build before installation. After that migration, keep the same keystore and alias forever; subsequent APKs with a higher versionCode install as updates without uninstalling.

Losing or replacing the signing key breaks in-place updates. Back up the keystore and passwords securely.
