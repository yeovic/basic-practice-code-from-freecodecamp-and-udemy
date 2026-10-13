# Velvet Atelier Studio — secure signing for future one-app upgrades

Android will allow an APK to upgrade `org.velvetatelier.studio` only when the package ID AND signing certificate match its installed version. The earlier v2.2–v2.8 releases used independent temporary certificates, so they cannot be upgraded in place.

The combined v3.0 release workflow **supports** private persistent signing. Configure these secrets in the GitHub repository BEFORE you make a release intended to receive future in-place updates. If the workflow runs without secrets, it deliberately generates an ephemeral certificate and prints a warning. The resulting standalone combined APK installs, but its next independently built update will not be compatible.

## Private keystore generation (local machine only)
```bash
keytool -genkeypair -storetype PKCS12 -keystore velvet-studio-release.p12 \
  -alias velvet -keyalg RSA -keysize 3072 -validity 10950 \
  -dname "CN=Velvet Atelier Studio, O=Personal Local, C=DK"
```
Choose a long unique password and keep an encrypted offline backup of the PKCS12 file. Never commit it to a public repository or include it in an APK.

Prepare GitHub Actions secrets in **Repository Settings → Secrets and variables → Actions**:
- `VELVET_SIGNING_KEYSTORE_B64`: single-line Base64 of the entire .p12 keystore (local shell `base64 -w0 velvet-studio-release.p12` on Linux; `base64 < velvet-studio-release.p12 | tr -d '\n'` on macOS).
- `VELVET_SIGNING_PASSWORD`: the keystore password, also used for the alias' private key.

The workflow expects the key alias `velvet`. Both secrets must be set together. Do not paste the secret values into ChatGPT or public issues. Once configured, the same keystore must be used for every subsequent Android release under the same app ID.

## Migration to the combined APK
1. From v2.8 Guided Studio, use **Export Cards** to save JSON. Verify it exists in shared Documents/Downloads.
2. Install the combined Studio APK. Check **Included Model Setup** and let first-run conversion complete.
3. Guided Studio → **Import Cards** and select the JSON. It carries profiles, custom styles and draft where present.
4. Test a sample image on your phone. Test Pixel Studio and Upscale Studio.
5. Only after confirming success, manually uninstall old v2.x packages (which otherwise occupy separate app + model storage). Do NOT uninstall old packages before exporting profiles and validating the new one.

**Model data migration:** Model files live in Android app-specific directories. Android 13 prevents this new package from silently accessing the other apps' private data; full Meina model setup is required once in the new installation. After that, it is shared across all screens within the combined app.

## Build checks
GitHub Actions `.github/workflows/velvet-combined30.yml`: JVM quality/image tests, Android Java compile, Apktool rebuild, native executable / model checksum, DEX class scan, signed APK, release attachment. Mobile real-world inference is a separate test and is not implied by CI.

Security trade-off: private signing secrets cannot be written through the current limited GitHub connection. Creating a signing key during each build works only for standalone, non-upgradable test releases.
