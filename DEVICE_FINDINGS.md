# Actual device findings (user supplied)

Device: vivo V27 5G, model V2246; Android 15, API 35; build `PD2269HF_EX_A_15.2.26.1.W30`; vivo OS property 6.0. Original tests before changes: Private DNS mode `hostname`, with an existing user-configured hostname (do not embed the personal hostname in code or logs). Developer options `1`. All three animations `0.5`. Screen timeout `30000` milliseconds.

When user selects 5G in Vivo's own settings, `adb shell cmd phone get-allowed-network-types-for-users -s 0` includes `NR`; `Settings.Global preferred_network_mode` is `33,33`; `preferred_network_mode1` is `32`. On choosing 4G in Vivo's Settings, allowed network types no longer include `NR`, `preferred_network_mode` stays `33,33`, `preferred_network_mode1` becomes `20`. Slot 1 query returned 'No valid subscription found'. This supports *observing* radio selection from adb shell; it DOES NOT prove app-visible access or app control after USB is disconnected. Note 5G selected can physically connect to 4G when 5G signal is unavailable. Do NOT write legacy network keys from app.

Unverified on phone: ordinary-app WRITE_SECURE_SETTINGS permission grant success; per-feature API write applicability; complete tile behavior; manufacturer-specific AOD control; Play Store eligibility. Validate before declaring supported.
