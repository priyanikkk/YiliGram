# Third-party notices

YiliGram as a whole is distributed under the GNU GPL v3 or later (see `LICENSE`).
It includes the following components:

| Component | Source | License |
|---|---|---|
| Telegram for Android | https://github.com/DrKLO/Telegram | GPL-2.0-or-later (`LICENSE.upstream-GPL-2.0`) |
| Native libraries bundled by Telegram (FFmpeg, libvpx, dav1d, Opus, libyuv, OpenH264, BoringSSL, WebRTC, TDLib parts, rlottie, SQLite, …) | git submodules under `TMessagesProj/jni` | see each submodule's license file |
| Google Sans Flex font | https://github.com/googlefonts/googlesans-flex | SIL Open Font License 1.1 (`branding/fonts/OFL.txt`); "Google Sans" is a trademark of Google LLC (`branding/fonts/TRADEMARKS.md`) |
| material-color-utilities (Java) | https://github.com/material-foundation/material-color-utilities | Apache-2.0 (`yili-design/src/main/java/com/yiligram/design/mcu/LICENSE`) |
| androidx.graphics:graphics-shapes | https://developer.android.com/jetpack/androidx/releases/graphics | Apache-2.0 |
| Chaquopy (Python on Android) | https://github.com/chaquo/chaquopy | MIT |
| CPython 3.11 | https://www.python.org | PSF License |
| LSPlant (ART method hooking) | https://github.com/LSPosed/LSPlant (git submodule `yili-hook/lsplant`) | LGPL-3.0 |
| DexBuilder, slicer (used by LSPlant) | https://github.com/LSPosed/DexBuilder | LGPL-3.0; slicer: Apache-2.0 |
| parallel-hashmap (used by DexBuilder) | https://github.com/greg7mdp/parallel-hashmap | Apache-2.0 |
| LSPosed libcxx (LLVM libc++ with std modules, prefab) | https://github.com/LSPosed/libcxx | Apache-2.0 WITH LLVM-exception |
| Dobby (inline hooks, prefab `io.github.vvb2060.ndk:dobby`) | https://github.com/jmpews/Dobby | Apache-2.0 |
| HiddenApiBypass | https://github.com/LSPosed/AndroidHiddenApiBypass | Apache-2.0 |
| XZ Embedded (MiniDebugInfo decompression) | https://github.com/tukaani-project/xz-embedded (`yili-hook/src/main/cpp/xz`) | 0BSD (`yili-hook/src/main/cpp/xz/COPYING`) |
| Bundled Python packages: requests, urllib3, idna, certifi, charset-normalizer, beautifulsoup4, soupsieve, lxml, Pillow, PyYAML, packaging | PyPI | Apache-2.0 / MIT / MPL-2.0 (certifi) / BSD / HPND (Pillow) — see each package |

The plugin engine implements the publicly documented exteraGram plugin SDK
(https://plugins.exteragram.app/docs); no exteraGram code is included.

LSPlant and DexBuilder are linked statically into `libyilihook.so`; their complete source is
part of this repository (submodule), which satisfies LGPL-3.0 §4 for a GPL-3.0 application.

Planned for later stages: DexMaker (Apache-2.0) for class proxies.
