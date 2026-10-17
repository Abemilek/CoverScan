<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="160" alt="CoverScan app icon" />
</p>

<h1 align="center">CoverScan</h1>

<p align="center">
  <strong>Free-libre open source software to scan the covers of theses, seminars, CDs and books with 100% on-device OCR and save everything into a single JSON file.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/github/actions/workflow/status/Abemilek/CoverScan/ci.yml?label=CI&logo=github" alt="CI status" />
  <img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 7.0+" />
  <img src="https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 1.9.24" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.06.00-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Material-3-6750A4?logo=materialdesign&logoColor=white" alt="Material 3" />
  <img src="https://img.shields.io/badge/Gradle-8.7-02303A?logo=gradle&logoColor=white" alt="Gradle 8.7" />
  <img src="https://img.shields.io/badge/AGP-8.5.2-3DDC84?logo=androidstudio&logoColor=white" alt="Android Gradle Plugin 8.5.2" />
  <img src="https://img.shields.io/badge/JDK-17%2B-ED8B00?logo=openjdk&logoColor=white" alt="JDK 17+" />
  <img src="https://img.shields.io/badge/CameraX-1.3.4-34A853" alt="CameraX 1.3.4" />
  <img src="https://img.shields.io/badge/ML%20Kit-16.0.1-4285F4?logo=google&logoColor=white" alt="ML Kit 16.0.1" />
  <img src="https://img.shields.io/badge/OCR-100%25%20on--device-4CAF50" alt="100% on-device OCR" />
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="MIT License" />
</p>

## Table of contents

- [JSON format](#json-format)
- [Building the APK (universal release)](#building-the-apk-universal-release)
  - [Requirements](#requirements)
  - [1. Set up the SDK (once)](#1-set-up-the-sdk-once)
  - [2. Sign the release (once)](#2-sign-the-release-once)
  - [3. Build](#3-build)
  - [4. Install on the phone](#4-install-on-the-phone)
  - [Alternative: Android Studio](#alternative-android-studio)
- [Usage](#usage)
- [Tuning field recognition](#tuning-field-recognition)
- [Structure](#structure)
- [License](#license)

- **100% on-device OCR** (ML Kit, bundled model). The app **has no internet permission**.
- Flow: **Capture** → the cover is read and split into fields → **editable preview** → **Save and continue** → … → **Export** (a single `.json`).
- You can also import several photos you already took using **Gallery** (they are reviewed one by one).
- Every saved document is written immediately to the app's internal storage, so nothing is lost if you close the app. Anything still in the "Pending review" queue is lost if the app is closed.
- Fields: category (Thesis/Seminar, Disc/CD, Book, Other), work type, title, authors, tutors/advisors, institution, faculty/area/center, date/place, other texts, notes, full OCR text.
- Covers are not all alike: whatever the program fails to recognize goes to "Others", and everything can be fixed on the review screen. The full text is always saved.

## JSON format

The keys always exist; if a piece of data does not appear on the cover (date, tutor, etc.) its value is `null`.
The labels Autor/Alumno/Estudiante/Sustentante/Presentado por are all stored under `autores`.

```json
{
  "version": 1,
  "exportado_en": "2026-10-16T15:30:00",
  "total": 1,
  "documentos": [
    {
      "id": "…",
      "categoria": "Tesis/Seminario",
      "tipo_trabajo": "Inscripción de tema",
      "titulo": "Resultados Perinatales en adolescentes embarazadas, …",
      "autores": ["Gutiérrez Narváez Edmy Shasary", "Hernández Mora Marilia Gabriel"],
      "tutores": ["Tutor científico: Dr. Alan José Juárez Vásquez"],
      "institucion": "UNIVERSIDAD NACIONAL AUTÓNOMA DE NICARAGUA UNAN-MANAGUA",
      "facultad_area": "FACULTAD DE CIENCIAS MÉDICAS",
      "fecha_lugar": "Jinotepe, Carazo, diciembre del 2018",
      "otros": null,
      "texto_completo": "…",
      "notas": "",
      "escaneado_en": "2026-10-16T15:12:00"
    }
  ]
}
```

## Building the APK (universal release)

The project produces **a single universal APK**: the same file works on any phone, with no need to choose an architecture or generate several APKs. Release builds include the native libraries for every phone architecture (arm64-v8a and armeabi-v7a); the x86/x86_64 libraries (only useful for emulators) are excluded, keeping the APK around half the size (~26 MB instead of ~48 MB). Debug builds keep all four architectures for emulator testing.

The instructions apply to Linux, macOS and Windows (the variant is only noted where the commands differ). Everything is downloaded as an official zip: no app stores, snaps or flatpaks.

### Requirements

- **JDK 17 or later** (tested with 17 and 21). Suggested download: [Adoptium / Eclipse Temurin](https://adoptium.net).
- **Android SDK → "Command line tools only"** (official zip, choose your system): <https://developer.android.com/studio#command-line-tools-only>
- **Gradle**: the project already ships the wrapper (`gradlew`), which downloads the required version (8.7) the first time. If you are missing the wrapper: generate one with `gradle wrapper --gradle-version 8.7` using any installed Gradle, or download the zip from <https://services.gradle.org/distributions/gradle-8.7-bin.zip>.
- **Internet connection** only for the first build (Gradle downloads the dependencies); after that you can build with `--offline`.

> Tested combination: Gradle 8.7 + Android Gradle Plugin 8.5.2 + SDK 34 + JDK 21.
> The Android command line tools and Gradle do not send usage telemetry. If you ever use Android Studio, the initial setup wizard asks whether you share statistics: you can decline it (or turn it off in *Settings → Data Sharing*).

### 1. Set up the SDK (once)

Unzip the *command line tools* zip so that you end up with `…/cmdline-tools/latest/bin/sdkmanager`, and set the environment variables (in `~/.bashrc`, `~/.zshrc`, `$PROFILE`… so you don't have to repeat them):

**Linux / macOS:**

```bash
export ANDROID_HOME="$HOME/Android"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
```

**Windows (PowerShell):**

```powershell
$env:ANDROID_HOME = "$env:USERPROFILE\Android"
$env:Path += ";$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:ANDROID_HOME\platform-tools"
```

Then, the same on any system, accept the licenses and install the platform used by the project:

```bash
sdkmanager --licenses                          # answer "y" to everything
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

### 2. Sign the release (once)

Android requires the release APK to be signed. The project reads the credentials from a `keystore.properties` file next to the project (it is ignored by git). If you already have the keystore and the `keystore.properties`, skip to step 3.

If you are starting from scratch, create your keystore and its credentials file (change the example password):

```bash
keytool -genkeypair -keystore coverscan-release.keystore -alias coverscan \
  -keyalg RSA -keysize 2048 -validity 10950
```

```properties
# keystore.properties
storeFile=coverscan-release.keystore
storePassword=YOUR_PASSWORD
keyAlias=coverscan
keyPassword=YOUR_PASSWORD
```

⚠️ Keep `coverscan-release.keystore` and `keystore.properties` safe: future updates are signed with them. If you lose them, the phone will not be able to update the installed app (you would have to uninstall it, losing the app's internal data).

### 3. Build

Inside the project folder, create the `local.properties` file with the path to your SDK:

**Linux / macOS:**

```bash
echo "sdk.dir=$HOME/Android" > local.properties
./gradlew assembleRelease
```

**Windows:**

```powershell
Set-Content -Path local.properties -Value "sdk.dir=$env:USERPROFILE\Android" -Encoding ascii
.\gradlew.bat assembleRelease
```

The APK ends up in:

```
app/build/outputs/apk/release/app-release.apk
```

### 4. Install on the phone

1. On the phone: Settings → About phone → tap "Build number" 7 times → enable **USB debugging** in Developer options.
2. Connect over USB and accept the RSA fingerprint prompt that appears on the phone's screen (on Windows you may need to install the manufacturer's USB driver first).
3. Install with `adb` (included in the SDK's `platform-tools`):

```bash
adb devices
adb install -r app/build/outputs/apk/release/app-release.apk
```

(Or copy the `.apk` to the phone and open it; you will have to allow "install unknown apps".)

### Alternative: Android Studio

If you prefer a graphical interface: open the project folder with [Android Studio](https://developer.android.com/studio) (available for Linux, macOS and Windows). The wizard downloads any missing SDK/Gradle and can use the same `keystore.properties` for signing. Then *Build → Build APK(s)*.

## Usage

1. **Scan** tab: frame the cover with good light, avoid reflections (glossy covers cause them) and tap **Capture**.
2. Review/fix the fields. Pick the category. **Save and continue** goes back to the camera.
3. In **List** you can tap a document to edit or delete it.
4. **Export** opens the system picker so you can choose where to save the `.json` (all documents in one file). You can export as many times as you want; the internal content is kept.

## Tuning field recognition

Everything lives in `app/src/main/java/com/example/portadas/PortadaParser.kt`: regular expressions for roles (author, tutor, advisor…), units (faculty, area, center…), work type and dates. If you see a pattern that keeps failing on your covers, add the word there.

## Structure

```
app/src/main/java/com/example/portadas/
  MainActivity.kt     entry point
  Ui.kt               screens (camera, list, review)
  MainViewModel.kt    capture, OCR, review queue, saving
  PortadaParser.kt    splits the OCR text into fields
  DocumentStore.kt    JSON read/write
  Model.kt            Documento class

keystore.properties          signing credentials (ignored by git)
coverscan-release.keystore   signing keystore (ignored by git)
gradlew / gradlew.bat        Gradle wrapper
```

## License

Released under the [MIT License](LICENSE).
