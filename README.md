# Historical Camp Layout

A planner for historical and medieval reenactment camps. Lay out tents, sun sails and fire pits on a
to-scale parcel before you arrive on site, and see right away where things collide or block walkways.

## Features

- **Camp canvas**: place, move, rotate and lock tents, sun sails and fire pits on a scaled plot.
- **Collision and spacing checks**: overlaps, blocked tent entries and walkway width are flagged as you edit.
- **Auto setup**: generates a camp draft from your tent inventory and parcel size (edge, spread and U-shape
  layouts), with a step-by-step setup sheet.
- **PNG export** of the finished layout.
- Runs on **Android** and **desktop (JVM)**, in English and German.

## Project structure

* [/composeApp](./composeApp/src) holds the shared Compose Multiplatform app.
  - [commonMain](./composeApp/src/commonMain/kotlin) contains the UI, geometry/collision engines and auto layout.
  - [androidMain](./composeApp/src/androidMain/kotlin) and [jvmMain](./composeApp/src/jvmMain/kotlin) contain
    the platform-specific code.
* [/androidApp](./androidApp/src/main) is the thin Android app shell (`MainActivity`, manifest, launcher icons, theme).
  AGP 9 no longer allows the Android application plugin in a Kotlin Multiplatform module, so `composeApp`
  is an Android library and `androidApp` packages it into the installable app.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :androidApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :androidApp:assembleDebug
  ```

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…