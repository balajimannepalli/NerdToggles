# Acceptance checklist (V1)

- [ ] minSdk 35, Kotlin, minimal Compose, app ID `com.nerdtoggles.app`.
- [ ] `INTERNET` is absent; no root/Shizuku/wireless debug/ADB server/background service; no analytics/ads/DB.
- [ ] Release APK built, APK size measured, no unnecessary runtime dependencies.
- [ ] UI: Controls / Setup / Device, readable state, dynamic system theme, usable light/dark, accessibility semantics.
- [ ] Permission setup: check `WRITE_SECURE_SETTINGS`; correct ADB instructions; special Settings `WRITE_SETTINGS` access via `Settings.System.canWrite()`/official Settings screen.
- [ ] Private DNS reads existing custom mode and hostname without replacing it; Off/Auto/Custom cycle; missing Custom hostname triggers chooser; write/readback tested.
- [ ] Screen timeout list editable and retained locally; cycling works with arbitrary external values; readback verified.
- [ ] One animation tile changes window/transition/animator; handles mixed values and partial failures truthfully.
- [ ] Developer Options reads correct state, handles permissions, confirms disruptive disable, and does not claim USB debugging is the same toggle.
- [ ] 4G/5G shown experimental; no fake direct toggle; only honest manual Settings navigation.
- [ ] Tile tap applies (if permitted), refreshes state; long press opens corresponding configuration; add tile request/manual alternative; no silent tile management.
- [ ] Permission revoked, app killed, phone rebooted, external setting changed, no network/airplane, and screen locked: no crashes or deceptive state.
- [ ] All feature support labels distinguish implementation from tested device support.
- [ ] No hostname, device identifiers, user network data or serials in release logs/source.
- [ ] Automated pure logic tests + manual device test instructions; user settings retained while installed; app backup disabled.
