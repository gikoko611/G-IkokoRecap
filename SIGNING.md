# Android release signing (one-time setup)

The GitHub Actions workflow can use a persistent release keystore. This is important: Android only permits an in-place app update when the new APK is signed with the same private key as the installed APK.

## Create a permanent keystore in Termux

Choose your own strong passwords and keep them private. Do not paste them into ChatGPT or commit the keystore to the repository.

```bash
pkg install openjdk-17 coreutils -y
keytool -genkeypair -v \
  -keystore ~/gikoko-release.keystore \
  -storepass 'CHOOSE_A_STRONG_STORE_PASSWORD' \
  -keypass 'CHOOSE_A_STRONG_KEY_PASSWORD' \
  -alias gikoko \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=GIkoko, OU=Recap, O=GIkoko, L=Yangon, C=MM"

base64 -w 0 ~/gikoko-release.keystore > ~/gikoko-release.keystore.b64

## Add the keystore to GitHub Actions secrets

1. Open the repository on GitHub.
2. Go to **Settings → Secrets and variables → Actions → New repository secret**.
3. Create these four secrets:

| Secret name | Secret value |
|---|---|
| ANDROID_KEYSTORE_BASE64 | Entire one-line contents of ~/gikoko-release.keystore.b64 |
| ANDROID_KEYSTORE_PASSWORD | The store password used when creating the keystore |
| ANDROID_KEY_ALIAS | gikoko |
| ANDROID_KEY_PASSWORD | The key password used when creating the keystore |

In Termux, display the one-line Base64 value with:

```bash
cat ~/gikoko-release.keystore.b64
```

Copy the complete output into the ANDROID_KEYSTORE_BASE64 secret. Keep the original .keystore file and both passwords in a safe private place.

After saving all four secrets, open **Actions → Android CI Build → Run workflow**. The workflow will then sign and publish the stable v0.0.5 APK. When the secret is absent, CI still builds a temporary-signed artifact for testing, but it withholds the GitHub Release asset.

**Important:** The earlier CI workflow generated a fresh random keystore on every run. Android cannot update an app signed by one of those older temporary keys using a different key. Preserve any existing local data before uninstalling an older installation. Once the permanent key is used for the first stable release, keep it for every future update.
