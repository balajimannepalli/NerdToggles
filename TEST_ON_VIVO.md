# Vivo V27 5G acceptance

Run on Android 15 with the phone unlocked. Record original settings privately, including the custom DNS hostname, the animation triple, timeout and Developer Options master state. Restore them in system Settings after testing. Keep personal hostnames and device identifiers out of reports.

## Install and establish the baseline

From PowerShell with platform-tools on PATH:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell settings get global window_animation_scale
adb shell settings get global transition_animation_scale
adb shell settings get global animator_duration_scale
adb shell settings get global development_settings_enabled
adb shell settings get system screen_off_timeout
```

- [ ] With secure access missing, verify unavailable tiles. For the one-time grant run `adb shell pm grant com.nerdtoggles.app android.permission.WRITE_SECURE_SETTINGS`. Record any refusal. Enable Modify system settings through Setup. Disconnect USB and repeat functional checks.
- [ ] Add each tile via Controls and via Quick Settings Edit. Check denial, already-added feedback, long press routing, large text, light/dark themes, and lock/unlock.
- [ ] Follow the DNS and Screen Timeout checks in README. Confirm the original DNS hostname survives the cycle. Compare physical screen-off timing with the saved value.
- [ ] Set mixed animation scales through system Settings. Check Mixed and the first configured target on tap. Cycle through every entry, inspect all three values individually, reorder the cycle, and repeat from a uniform unlisted scale. Record any failure or incomplete restoration.
- [ ] Use Developer Options configuration for a deliberate first write. Check the master switch in system Settings after each direction. Check that disabling requires opt-in and that withdrawing opt-in prevents another disable. Record OEM effects on debugging independently. Recheck permission if the connection drops.
- [ ] Open the experimental network shortcut and return without changing the preference. Confirm the manual path on systems without a suitable Settings activity. Verify the app reports unknown radio state.
- [ ] Revoke special access in system Settings. To test loss of the secure grant over USB, run `adb shell pm revoke com.nerdtoggles.app android.permission.WRITE_SECURE_SETTINGS`. Reopen the shade and check for unavailable tiles. Test external edits, app process recreation, reboot, offline use and cycle persistence.
- [ ] Repeat on a locally signed release APK. Check installed storage under Settings → Apps → Nerd Toggles → Storage and record the device-reported footprint.

## Personal release

In Android Studio choose **Build → Generate Signed App Bundle or APK → APK**, select the app module, choose your personal release key and select the release variant. Keep the keystore and its backup outside the repository. Enter passwords in the signing dialog. Keep the same key for future updates.

Verify the exact signed artifact with SDK Build Tools before installation. Substitute its actual path below. Compare the printed SHA-256 signer digest with your independently recorded release certificate digest. Record the file hash for this build.

```powershell
$signedApk = 'D:\Releases\NerdToggles\app-release.apk'
& "$env:USERPROFILE\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs $signedApk
Get-FileHash -Algorithm SHA256 $signedApk
```

Use a separate test device for a differently signed installation of the same package. Android checks the signing identity across user profiles. Complete the checks above on the signed artifact. Play distribution requires a separate policy review for the special permissions and device-specific functionality.
