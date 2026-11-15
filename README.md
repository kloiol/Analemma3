# Sun Analemma Planetary Ephemeris Android App

An Android application (Kotlin, API 26–34, Gradle 8.7) that estimates and plots the **Sun Analemma** relative to user-entered geopotential coordinates (`Latitude`, `Longitude`, `Altitude`) and exports the resulting plot to **PDF** (`android.graphics.pdf.PdfDocument`) or **PNG** (`Bitmap.CompressFormat.PNG`).

## Cloud APK Compilation via GitHub Actions

This archive is structured so you can extract its contents directly into the root of a GitHub repository:

1. Extract `SunAnalemmaAndroidProject.zip` into your repository root (so `.github/workflows/android-build.yml` and `app/src/main/AndroidManifest.xml` are at the root of the repository).
2. Commit and push to `main` or `master` (or trigger **Build Android APK** manually under the **Actions** tab via `workflow_dispatch`).
3. GitHub Actions automatically provisions **JDK 17**, **Android SDK 34**, and **Gradle 8.7**, verifies `app/src/main/AndroidManifest.xml` (auto-detecting the project root even if extracted into a subfolder), compiles the APK with `gradle assembleDebug`, and publishes `SunAnalemma-Debug-APK` (`app-debug.apk`) in the workflow run's **Artifacts** section.

## Project Structure

```
├── .github/workflows/android-build.yml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── README.md
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/astro/sunanalemma/
        │   ├── MainActivity.kt
        │   ├── AnalemmaEphemerisEngine.kt
        │   ├── AnalemmaPlotView.kt
        │   └── ExportHelper.kt
        └── res/xml/
            └── file_paths.xml
```
