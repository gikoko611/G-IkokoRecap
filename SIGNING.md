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
