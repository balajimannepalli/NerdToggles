# Nerd Toggles

A small offline Android 15+ utility for the vivo V27 5G. Quick Settings tiles cover Private DNS, Screen Timeout, Animation Scale and Developer Options. Developer Options requires a user-initiated verified write before tile use. The experimental 4G/5G entry opens system Settings. Backup is disabled. Operation requires no root, Shizuku, wireless debugging or always-on ADB connection. See [Vivo acceptance and release signing](TEST_ON_VIVO.md).

## Build on Windows

Use JDK 17 or a Gradle-compatible newer JDK. In Android SDK Manager install Android SDK Platform 37 and Build Tools 36.0.0. Point `ANDROID_HOME` or the local `sdk.dir` in `local.properties` at the SDK. The first build needs internet access on the development computer to resolve Gradle and dependencies.

From the project directory in PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
(Get-Item .\app\build\outputs\apk\debug\app-debug.apk).Length
```

The second command reports the debug APK size in bytes. On the phone, compare Controls with the current system settings, including any unavailable values. In Setup, try Copy command and Check access. Open Modify system settings and return to verify the status refreshes. Check Device against About phone. A readable value or granted permission alone cannot verify future writes.

For the size of the shrunk, unsigned release APK:

```powershell
.\gradlew.bat :app:assembleRelease
(Get-Item .\app\build\outputs\apk\release\app-release-unsigned.apk).Length
```

## Private DNS device check

Record the original mode and hostname privately so you can restore them in Android Settings. With the one-time grant in place, add the tile from Controls, then check Off → Automatic → Custom → Off against Android Settings. Confirm the original hostname survives the cycle. With no configured hostname, tapping Automatic should open configuration and leave the mode unchanged until you apply a valid hostname. Check long press, the manual add path, an external mode change, lock/unlock and permission revocation. The tile verifies the two settings values only. DNS resolution requires a separate connectivity test.

## Screen Timeout device check

Record the original timeout, then enable Modify system settings in Setup. Add the tile from Controls or Quick Settings Edit. Long press the tile and save a reordered cycle. Check that taps follow that order, including after reopening the app. Change the timeout externally to a value outside the cycle and confirm the next tap selects the first entry. Check the displayed value after locking and unlocking, then revoke permission and check the unavailable state. Compare readback with Android Settings and time the actual screen turn-off separately to check OEM or device policy limits. Restore the original timeout in Android Settings.
