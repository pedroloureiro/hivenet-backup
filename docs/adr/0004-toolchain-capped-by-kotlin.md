# Toolchain versions are the newest Kotlin has been tested with

Every library and tool is on its newest stable release, except where that would leave a combination nobody tested. Kotlin is the constraint: KGP 2.4.21, the newest stable Kotlin, is tested up to AGP 9.3 and Gradle 9.7, so the build uses AGP 9.3.3 and Gradle 9.7.1 although AGP 9.5 and Gradle 9.9 exist and lint suggests them. Upgrade AGP and Gradle together with the Kotlin release whose compatibility table covers them.

## Consequences

- Compile SDK is 37.1: Compose 1.13 requires it, and AGP 9.3 is tested up to 37.1 (37.2 builds, with a warning).
- App bytecode stays at Java 17, the highest level the Android docs list. Unit tests run on a Java 21 launcher because Robolectric needs Java 21 to emulate SDK 37; Gradle provisions that JDK.
- Coroutines are aligned through their BOM so the app and its tests resolve the same version.
