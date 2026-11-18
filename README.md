# YiliGram

YiliGram is an **unofficial** Telegram client for Android, based on the official
[Telegram for Android](https://github.com/DrKLO/Telegram) source code. It uses the
[Telegram API](https://core.telegram.org/api) but is not affiliated with or endorsed by Telegram.

Goals:

- **Material 3 Expressive** design: dynamic color, expressive shapes, spring motion.
- **Deep customization**: colors, shapes, fonts, density, motion, navigation and more, in one place.
- **Plugins**: a Python plugin engine compatible with exteraGram plugins.

See [PLAN.md](PLAN.md) for the roadmap and architecture.

> Status: early development (stage 1: fork, rebranding, build). Not ready for daily use.

## Building

### Requirements

| Tool | Version |
|---|---|
| JDK | 21 |
| Android SDK | platform 36, build-tools 36.0.0 |
| Android NDK | 27.2.12479018 |
| CMake | 3.22.1 (from the SDK manager) |

The native part (MTProto, media codecs, VoIP) is compiled from source, so a full build
takes 30–60 minutes the first time and needs ~15 GB of disk space.

### Steps

1. Clone with submodules:
   ```bash
   git clone --recursive --shallow-submodules https://github.com/yiligram/YiliGram.git
   ```
   For an existing clone: `git submodule update --init --recursive --depth 1`.
2. Get your own `api_id` and `api_hash` at <https://my.telegram.org> (API development tools).
3. Copy `local.properties.example` to `local.properties` and fill in `sdk.dir`,
   `yili.apiId` and `yili.apiHash`. Environment variables `YILI_API_ID` / `YILI_API_HASH`
   work too. **Never commit these values.**
4. Build a debug APK:
   ```bash
   ./gradlew :TMessagesProj_App:assembleAfatDebug
   ```
   To speed up local builds, compile native code for one ABI only:
   `./gradlew -PyiliAbis=arm64-v8a :TMessagesProj_App:assembleAfatDebug`.
   The APK is written to `TMessagesProj_App/build/outputs/apk/afat/debug/`.

### Release signing

Debug builds are signed with the standard Android debug key. For release builds, create a
keystore and describe it in `keystore.properties` (see `keystore.properties.example`; the
file and `*.jks` are git-ignored), then run:

```bash
./gradlew :TMessagesProj_App:assembleAfatRelease
```

Without `keystore.properties` (or the `YILI_KEYSTORE_*` environment variables) the
release APK is left unsigned.

### Continuous integration

`.github/workflows/build.yml` builds a debug APK on every push and pull request, and a
signed release APK for `v*` tags (published as a GitHub release). It needs these
repository secrets: `YILI_API_ID`, `YILI_API_HASH`, `YILI_KEYSTORE_BASE64`,
`YILI_KEYSTORE_PASSWORD`, `YILI_KEY_ALIAS`, `YILI_KEY_PASSWORD`.

### Branding assets

Launcher, monochrome (themed), notification and splash icons are generated from
`branding/logo.png`, and the wordmark from `branding/fonts/GoogleSansFlex.ttf`:

```bash
python3 -m venv .venv && .venv/bin/pip install -r tools/branding/requirements.txt
.venv/bin/python tools/branding/gen_icons.py
.venv/bin/python tools/branding/gen_wordmark.py
```

## Plugins

YiliGram runs exteraGram-compatible Python plugins (`.plugin` / `.py`, Python 3.11 via
Chaquopy): **Settings → Plugins → Install from file**. Implemented so far: metadata,
lifecycle, request / response / update / outgoing-message hooks, plugin settings, menu items
registry, `client_utils`, `android_utils`, `hook_utils` (reflection), `file_utils`,
`log_utils`, `ui.settings`, `ui.alert`, `ui.bulletin`, `extera_utils.text_formatting`, plus
safe mode. Java method hooks, the pip installer and the dev server come next (see PLAN.md).
Examples: [examples/plugins](examples/plugins).

Building needs a host Python 3.11 for Chaquopy: put `yili.buildPython=/path/to/python3.11`
into `local.properties` (or have `python3.11` on PATH). Engine tests:
`python3.11 -m unittest discover -s TMessagesProj_App/src/test/python`.

## Notifications

YiliGram does not use Firebase Cloud Messaging. Until UnifiedPush support lands,
notifications are delivered through Telegram's background connection (like on devices
without Google Play Services).

## License

YiliGram is free software: you can redistribute it and/or modify it under the terms of the
**GNU General Public License, version 3 or (at your option) any later version**
([LICENSE](LICENSE)).

Telegram for Android is licensed under the GNU GPL version 2 or later
([LICENSE.upstream-GPL-2.0](LICENSE.upstream-GPL-2.0)); YiliGram uses the "or later"
option. The source code of every YiliGram release must stay publicly available.
Third-party components and their licenses are listed in [NOTICE.md](NOTICE.md).

## Telegram API Terms of Service

YiliGram follows the [Telegram API Terms of Service](https://core.telegram.org/api/terms):
it uses its own `api_id`, does not use the name "Telegram" or the Telegram logo as its own
name or logo, and tells users that it is an unofficial client built on the Telegram API.
If you build and distribute your own version, you are responsible for complying with these
terms, starting with obtaining your own `api_id`.
