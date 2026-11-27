#!/usr/bin/env bash
# نیازمند JDK 17 و Android SDK (ANDROID_HOME). یک بار keystore می‌سازد و APK ساین‌شده تولید می‌کند.
set -e
if [ ! -f release.jks ]; then
  PASS=$(openssl rand -hex 8)
  keytool -genkeypair -v -keystore release.jks -alias callmanager -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$PASS" -keypass "$PASS" -dname "CN=CallManager"
  printf "storeFile=release.jks\nstorePassword=%s\nkeyAlias=callmanager\nkeyPassword=%s\n" "$PASS" "$PASS" > keystore.properties
fi
if [ -x ./gradlew ]; then ./gradlew assembleRelease; else gradle assembleRelease; fi
echo "APK: app/build/outputs/apk/release/app-release.apk"
