# Forge

Forge is an open-source Magic: The Gathering game engine with AI opponents, supporting all official rules. Originally started in 2007 by CardForge.

- **Repository**: https://github.com/Card-Forge/forge
- **Website**: https://www.cardforge.org/
- **Version**: 2.0.10-SNAPSHOT
- **Java**: 17 (Maven multi-module project)

## Project Structure

| Module | Description |
|--------|-------------|
| `forge-core` | Core data types, card parsing, mana system |
| `forge-game` | Game engine, rules, turns, combat, stack |
| `forge-ai` | AI opponent logic and decision-making |
| `forge-gui` | Shared GUI logic and game resources (`res/` folder with card DB, skins, etc.) |
| `forge-gui-desktop` | Desktop (Swing) frontend |
| `forge-gui-mobile` | Shared mobile UI code (libGDX) |
| `forge-gui-mobile-dev` | Desktop runner for mobile UI (for testing without a phone) |
| `forge-gui-android` | Android APK packaging (uses android-maven-plugin) |

Card definitions live in `forge-gui/res/cardsfolder/` as individual `.txt` files (one per card).

## Prerequisites

- **JDK 17**: `C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot`
- **Apache Maven 3.9.6**: `C:\Users\Dong Hoon\tools\apache-maven-3.9.6`
- **Android SDK**: `C:\Users\Dong Hoon\tools\android-sdk` (installed via `setup-android-sdk.bat`)

## Scripts

### Android APK

| Script | Purpose |
|--------|---------|
| `setup-android-sdk.bat` | **One-time setup.** Downloads Android SDK command-line tools, accepts licenses, and installs platform-tools, android-35, and build-tools 35.0.0. No Android Studio required. |
| `build-apk.bat` | **Builds the Android APK (debug).** Two-step Maven build: (1) compiles core modules, (2) builds the Android APK with ProGuard and D8 dexing. Uses `subst` drive mappings (`F:` for project, `G:` for Maven repo) to keep D8's classpath command under Windows' 8191-char limit. Invokes Java directly instead of `mvn.cmd` to avoid path-resolution issues with spaces in the user directory. |
| `update-and-build.bat` | **Pulls latest from GitHub and builds APK.** Stashes local changes, runs `git pull origin master` to get the latest card DB and fixes, restores local changes, then calls `build-apk.bat`. |

### Desktop and Mobile (Dev)

| Script | Purpose |
|--------|---------|
| `build-and-run-desktop.bat` | Builds the desktop module (`forge-gui-desktop`) with Maven and launches it. |
| `build-and-run-mobile.bat` | Builds the mobile-dev module (`forge-gui-mobile-dev`) with Maven and launches it. Runs the mobile UI in a desktop window for testing. |
| `run-desktop.bat` | Launches an already-built desktop jar (skips build). |
| `run-mobile.bat` | Launches an already-built mobile-dev jar (skips build). |

### Networking

| Script | Purpose |
|--------|---------|
| `setup-firewall.bat` | **Run as Administrator.** Opens Windows Firewall port 36743 (TCP+UDP) for Forge multiplayer LAN server so your phone can connect. |

## Build Workarounds

`build-apk.bat` contains several workarounds for building on Windows with Java 17:

1. **Direct Java invocation** -- Bypasses `mvn.cmd` which fails when called from a `subst`-mapped drive (prints Java help page instead of running Maven).
2. **Short drive mappings** -- `subst F:` for the project and `subst G:` for the Maven repo (`C:\m2\repository`) to keep D8 dex compiler's command line under 8191 chars.
3. **`--add-opens` flags** -- Opens `sun.security.pkcs` and `sun.security.x509` to unnamed modules so the android-maven-plugin can sign APKs on Java 17 (it uses internal APIs designed for Java 8).
4. **ProGuard cleanup** -- Deletes stale ProGuard output files before building to avoid "access denied" errors from locked files.
5. **Custom Maven settings** -- Generates `mvn-settings-build.xml` at build time pointing `localRepository` to `G:\` so all dependency paths use the short drive letter.

## Keeping Up to Date

```bash
# Quick update (just pull latest cards and code)
git pull origin master

# Update and rebuild APK in one step
.\update-and-build.bat
```

## Leftover / Temporary Scripts

These were created during debugging and can be safely deleted:

- `build-apk-short.bat`
- `create-junction.bat`
- `run-apk-build-temp.bat`
