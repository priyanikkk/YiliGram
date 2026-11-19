# YiliGram: план работ

> Статус: **черновик на согласование**. Код не меняется, пока план не утверждён.
> База: Telegram for Android 13.2.2 (build 7303), коммит `21f7d10` из DrKLO/Telegram (сейчас TelegramMessenger/Telegram-Android).

## 0. Что выяснил при изучении

### Исходники Telegram
- Около 2050 Java-файлов, Kotlin нет. Главные файлы огромные: `ChatActivity` ≈ 48 тыс. строк, `ChatMessageCell` ≈ 24 тыс., `DialogsActivity` ≈ 14 тыс., `Theme.java` ≈ 10 тыс.
- Темы устроены так: около 930 целочисленных ключей `Theme.key_*`, массив `defaultColors` из `ThemeColors`, `ThemeInfo`/`ThemeAccent` и `Theme.ResourcesProvider` для локальных переопределений. Цвета читаются через `Theme.getColor(key, provider)`, о смене темы оповещает `NotificationCenter.didSetNewTheme`. Сюда удобно встроить мост M3 → Theme: подменить источник цветов, не трогая тысячи вызовов.
- Навигация собственная: `ActionBarLayout` + `BaseFragment`, без Activity/Fragment из AndroidX. Ни AppCompat, ни MDC не используются. `androidx.dynamicanimation` уже подключён, `SpringAnimation` встречается в 39 файлах.
- Сборка: AGP 8.13.2, Gradle 8.13, compileSdk/targetSdk 36, minSdk 23, NDK 27.2.12479018, CMake 3.22.1. Нативная часть (`TMessagesProj/jni`: tgnet, ffmpeg, webrtc/tgcalls, libvpx, dav1d, opus и т.д.) **собирается из исходников** через git submodules.
- Модули: `TMessagesProj` (library), `TMessagesProj_App` (afat/bundleAfat), а также Huawei, HockeyApp, Standalone и Tests.
- В `BuildVars.java` захардкожены публичные `APP_ID = 4` и `APP_HASH` Telegram, в `gradle.properties` лежат пароли тестового keystore. И то и другое убираем.
- Лицензия: в корне лежит текст GPL v2, но в заголовках файлов написано *«GNU GPL v. 2 or later»*. Для выбора библиотек это важно, см. раздел «Решения».

### Telegram API ToS (core.telegram.org/api/terms)
- Обязательно свой `api_id` (п. 2.1).
- Пользователь должен понимать, что приложение работает на Telegram API: это надо указать на интро-экране и в описании (п. 2.2).
- В названии нельзя «Telegram» (п. 2.3), официальный логотип и самолётик использовать нельзя (п. 2.4).
- Монетизацию надо раскрывать (п. 3.2). Если показываем каналы, обязательна поддержка sponsored messages (п. 3.3).
- Данные Telegram нельзя использовать для обучения ИИ (п. 1.5).
- Публиковать исходники требует GPL, а не ToS.

### Material 3 Expressive
- m3.material.io рендерится на клиенте, и автоматически получилось прочитать только заголовки страниц. Поэтому **значения токенов** (springs, шкала скруглений, 35 expressive-форм, line-height) беру из исходников первоисточников: `material-components-android` (токены 31–34 в `res/values`), репозитория material-design-tokens и `androidx.graphics:graphics-shapes` (`MaterialShapes`). Визуальные гайды по компонентам сверяю с сайтом вручную.
- MDC-Android 1.14.0 (stable) содержит View-реализации expressive-компонентов: `MaterialButtonGroup` (connected), `MaterialSplitButton`, `FloatingToolbarLayout`/`DockedToolbarLayout`, `LoadingIndicator` (морфинг форм), expressive-стили FAB, Top App Bar, Navigation Bar/Rail, Slider, Progress, Lists, а также emphasized typescale.
- Типографика проверена по MDC `Typography.md`: baseline и emphasized-варианты (Display/Headline/Title/Body/Label × L/M/S).
- `material-color-utilities` (Apache-2.0) есть на Java. Отдельного Maven-артефакта нет, поэтому код вендорим в проект. Варианты схем: TonalSpot, Vibrant, Expressive, Fidelity, Content, Monochrome, Neutral, Rainbow, FruitSalad. Контраст задаётся непрерывно от −1 до 1; standard/medium/high соответствуют 0 / 0.5 / 1.0.

### exteraGram Plugin SDK (plugins.exteragram.app/docs, прочитаны все ~30 страниц)
- Целевая поверхность API: **SDK 1.4.4.3** (в примерах встречается и 1.4.5.x), Python 3.11, Chaquopy 16, Xposed-подобные хуки.
- Метаданные читаются через AST: `__id__` (2–32 символа, `[A-Za-z][A-Za-z0-9_-]*`), `__name__`, `__description__`, `__author__`, `__version__`, `__icon__`, `__app_version__`, `__sdk_version__`, `__requirements__`, `__category__`; legacy `__min_version__`.
- Модули: `base_plugin` (BasePlugin, HookResult/HookStrategy, MethodHook/MethodReplacement, HookFilter, MenuItemData/MenuItemType, AppEvent), `client_utils`, `android_utils`, `hook_utils`, `file_utils`, `log_utils`, `ui.settings`, `ui.alert`, `ui.bulletin`, `extera_utils.text_formatting`, `extera_utils.classes` (class proxy на DexMaker + MVEL), `intents`, `elyx` (многофайловые плагины `.elyx`/`.eaf`).
- Файлы: `.plugin` (в репозиториях) и `.py` (на устройстве и в dev-сервере). Поддерживаем оба формата.
- Dev-сервер: TCP `127.0.0.1:42690`, поток JSON-объектов `{"@": cmd, "#": id, ...}`. Команды `ping`, `get_plugins`, `enable/disable/reload/write/remove_plugin`, `start/stop_debugger` (debugpy), а также elyx-команды.
- Предустановлены `requests`, `beautifulsoup4`, `lxml`, `pillow`, `PyYAML`, `packaging`, `debugpy`. Версии в документации не указаны.
- **Пробелы в документации:** CLI `extera` / `exteragram-utils` не описан; полного списка TL-имён для хуков нет; конец страницы Class Proxy и часть значений по умолчанию не описаны. Эти места закрываем по поведению реальных публичных плагинов.

### Chaquopy и хукинг
- Chaquopy 17.2.0 под лицензией MIT. Поддерживает Python 3.10–3.15, для 3.11 доступны все 4 ABI. Требует **minSdk 24** (у Telegram сейчас 23), AGP 7.3–9.5.
- **pip в Chaquopy работает только при сборке**, поэтому runtime-установку колёс с PyPI пишем сами. Для pure-Python wheel это несложно: wheel — это zip.
- Готовые колёса `lxml` 5.3.0 и `Pillow` 11.0.0 есть для cp311 под все ABI.
- LSPlant: **LGPL-3.0**, поддерживает Android 5–17 и 5 ABI, inline-hook бэкенд подключается отдельно (Dobby, Apache-2.0).
- Альтернативы не подходят: Pine распространяется под лицензией Anti-996, не совместимой с GPL; YAHFA и SandHook заброшены и не работают на Android 15/16.

### Окружение
- На машине **нет JDK, Android SDK и NDK**. Свободно 129 ГБ, 8 ядер, 30 ГБ RAM; этого достаточно.
- Логотип лежит в `branding/logog.png`: **213×216, растр, кот на тёмном фоне с металлическим градиентом**. Для adaptive icon (432 px на xxxhdpi), монохромной иконки и иконки уведомлений этого мало (см. вопросы).

---

## 1. Архитектура

```
YiliGram/
├─ TMessagesProj/            # upstream-код Telegram, правим точечно
│  └─ src/main/java/
│     ├─ org/telegram/...    # минимальные «врезки» с пометкой // YILI
│     └─ com/yiligram/
│        ├─ bridge/          # ThemeBridge (M3 → Theme.key_*), BulletinBridge, BackGestureBridge
│        ├─ config/          # YiliConfig
│        ├─ ui/              # экраны YiliGram: внешний вид, менеджер плагинов
│        └─ plugins/         # Java-часть движка плагинов (контроллер, хуки, dev-сервер)
├─ yili-design/              # Android library без зависимости от Telegram
│  ├─ color/                 # вендоренный material-color-utilities + YiliColorScheme
│  ├─ tokens/                # YiliTheme: color roles, type, shape, motion, elevation
│  └─ widget/                # M3 Expressive View-компоненты
├─ yili-plugins-py/          # Python-часть SDK: base_plugin, client_utils, ui/*, yili_ui, …
├─ yili-hook/                # нативный хукинг: LSPlant + Dobby, Xposed-совместимый API
├─ TMessagesProj_App/        # единственный app-модуль (Huawei/HockeyApp/Standalone убираем из settings.gradle)
├─ tools/branding/           # генератор иконок из логотипа
├─ tools/yili-dev/           # dev-клиент hot-reload (протокол 42690)
├─ examples/plugins/         # примеры плагинов
└─ .github/workflows/        # CI
```

**Главные принципы:**
1. **Пакеты `org.telegram.*` не переименовываем.** Меняется только `applicationId`. Так проще подтягивать изменения из upstream, и плагины exteraGram, которые обращаются к `org.telegram.ui.ChatActivity` и подобным классам, продолжают работать.
2. Изменения в upstream-файлах держим минимальными и помечаем `// YILI`, логику выносим в `com.yiligram.*`. Цель — регулярно мержить новые версии Telegram.
3. Дизайн-система живёт в отдельном модуле, не зависит от Telegram и покрывается unit-тестами.
4. MDC-Android как зависимость **не используем**. Его View завязаны на XML-атрибуты `Theme.Material3`, а у нас цвета меняются в рантайме, и тема Telegram не основана на AppCompat. Свои компоненты пишем по спецификации и токенам MDC. Как runtime-зависимости берём `androidx.graphics:graphics-shapes` (морфинг форм) и `androidx.dynamicanimation`.

---

## 2. Этапы

После каждого этапа: сборка проходит, тесты зелёные, коммиты логичными кусками, короткий отчёт.

### Этап 1. Форк, ребрендинг, сборка
1. `origin` → `upstream`, отдельная ветка `main`, `git submodule update --init --depth 1`.
2. `APP_PACKAGE=com.yiligram.messenger`, `app_name` = YiliGram, свой `versionName` (`1.0.0 (based on 13.2.2)`).
3. `APP_ID`/`APP_HASH` → `BuildConfig` из `local.properties` (`yili.apiId`, `yili.apiHash`) или переменных окружения `YILI_API_ID`/`YILI_API_HASH`. Если их нет, сборка падает с понятной ошибкой. Добавляю `local.properties.example`.
4. Подпись: `keystore.properties` (в `.gitignore`); debug подписывается стандартным debug-ключом. Тестовые пароли из `gradle.properties` удаляем, тестовый `release.keystore` из сборки исключаем.
5. Ребрендинг UI: название, интро-экран (без самолётика, с надписью *«Неофициальный клиент на Telegram API»*, п. 2.2 ToS), «О программе», каналы уведомлений, ярлыки. Упоминания **сервиса** Telegram («Telegram Premium», «контакты в Telegram») оставляем: это корректно и ToS разрешает.
6. Отключаем то, что привязано к инфраструктуре Telegram: апдейтер, Play Integrity/SafetyNet-ключи, Google Play Billing (без настоящего Telegram ID покупки не работают). Premium остаётся доступен через официальный бот.
7. Иконки (`tools/branding/gen_icons.py`): legacy mipmap (mdpi–xxxhdpi), adaptive foreground и background (108dp, safe zone 66dp), monochrome (`<monochrome>`, Android 13+), иконка уведомлений (белый силуэт 24dp), splash screen (Android 12+ `windowSplashScreenAnimatedIcon` и тема-фолбэк для старых версий).
8. GitHub Actions: JDK 21, NDK 27.2, кэш Gradle и ccache, debug APK на каждый push/PR, release APK по тегу (keystore и API-ключи из Secrets, keystore в base64).
9. README (сборка, GPL, ToS, дисклеймер), LICENSE, NOTICE со списком сторонних лицензий.

### Этап 2. Дизайн-система M3 Expressive
**2a. Токены (`yili-design`)**
- `YiliColorScheme`: все роли M3 (primary/on/container/onContainer/fixed/fixedDim/onFixed/onFixedVariant ×3 + error, surface, surfaceDim/Bright, surfaceContainerLowest…Highest, onSurface/Variant, outline/Variant, inverse*, scrim, shadow, surfaceTint).
- Генерация: seed → `DynamicScheme` (вариант схемы, light/dark, контраст). Dynamic color на Android 12+ берётся из системных `system_accent1_*`, а не из пересчёта обоев. AMOLED: surface-роли сдвигаются к чистому чёрному с сохранением контейнерной иерархии.
- `YiliType` (15 + 15 emphasized стилей, переменные шрифты), `YiliShapes` (none … extraExtraLarge, full; `MaterialShapes` для expressive-форм), `YiliMotion` (spatial/effects × fast/default/slow, схемы standard и expressive, глобальный множитель скорости из конфига), `YiliElevation` (тональные уровни 0–5).
- Unit-тесты: сверка сгенерированных цветов с эталонами material-color-utilities.

**2b. Мост с `Theme.java`**
- `ThemeBridge`: таблица ~930 ключей → роль M3 (+ альфа, + смешивание). Ключи группируются по префиксам (`actionBar*`, `windowBackground*`, `dialogs*`, `chat_in*/out*`, `avatar_*`, `switch*` и т.д.); каждый ключ явно сопоставлен, непокрытые выявляет тест.
- Встроенная тема «YiliGram M3» строится на лету и пересобирается при смене seed, обоев или контраста. Обычные `.attheme` продолжают работать: если выбрана пользовательская `.attheme`, мост отключается или работает в режиме «гармонизации» (на выбор).
- Применяем через стандартный `Theme.applyTheme` и `didSetNewTheme`, поэтому весь существующий UI перекрашивается сразу.

**2c. Компоненты (`yili-design/widget`)**: top app bar (small/medium/large, collapsing), FAB и FAB menu, extended FAB, chips, navigation bar и rail, list item, switch, slider, button (+ connected/standard button group, split button), icon button, segmented, floating/docked toolbar, loading indicator (morph), circular/linear progress (wavy), snackbar, bottom sheet, dialog, menu.

**2d. Редизайн экранов** (в порядке приоритета из ТЗ):
1. `DialogsActivity`: M3 top app bar с поиском, чипсы папок, FAB menu, navigation bar/rail (Чаты / Контакты / Звонки / Настройки, настраивается).
2. Чат: пузыри с M3-формами (радиусы и хвост из конфига), панель ввода как M3 toolbar, spring-анимации отправки и появления.
3. Настройки: M3 list items, switches, sliders, button groups.
4. Профили, диалоги, bottom sheets, меню; snackbar вместо bulletin через тот же API `BulletinFactory` (адаптер, без ломки вызовов).
5. Индикаторы загрузки.
- Анимации на `SpringAnimation` во всех переделанных местах. Тотальная замена ~тысяч `CubicBezierInterpolator` не планируется, только там, где это видно.
- Predictive back (Android 14+): `OnBackAnimationCallback` в `ActionBarLayout`, bottom sheets и диалогах; `enableOnBackInvokedCallback="true"`.

### Этап 3. Кастомизация
- `YiliConfig`: типизированные ключи `Key<T>` (bool/int/float/enum/color/string), одно хранилище (SharedPreferences `yili_config`), слушатели и событие `NotificationCenter.yiliConfigChanged` для реактивного UI.
- «Настройки → Внешний вид YiliGram» с живым превью (мини-список чатов и чат): цвет (seed, схема, dynamic, контраст, AMOLED), формы (аватары, пузыри, кнопки), шрифт (система, Google Sans Flex / Roboto Flex, свой `.ttf`/`.otf`; размер, насыщенность через `wght`), плотность и отступы, анимации (скорость и интенсивность, выключение), навигация (низ/бок, вкладки), иконки, обои, панель ввода, кнопки шапки и пунктов меню.
- Экспорт и импорт одним файлом `.yiliconfig` (JSON с версией схемы и миграциями), встроенные пресеты.
- Лицензии шрифтов: Google Sans Flex и Roboto Flex распространяются под OFL, это допустимо (проверю перед включением).

### Этап 4. Движок плагинов
**4a. Ядро:** Chaquopy 17.2 с Python 3.11, minSdk поднимается до 24. `PluginsController`: загрузка `.plugin`/`.py`/`.elyx`, метаданные через `ast` (без исполнения), проверка `__app_version__`/`__sdk_version__`, жизненный цикл, настройки, изоляция исключений.
**4b. Хуки Telegram:** точки врезки в `ConnectionsManager.sendRequest` (pre/post request), обработку апдейтов в `MessagesController` (`on_update_hook`/`on_updates_hook`) и `SendMessagesHelper` (`on_send_message_hook`). Цепочка плагинов по priority, семантика `HookStrategy` (DEFAULT/CANCEL/MODIFY/MODIFY_FINAL), привязка к аккаунту (`get_hook_account`).
**4c. Python SDK** с теми же именами и сигнатурами: `base_plugin`, `client_utils`, `android_utils`, `hook_utils`, `file_utils`, `log_utils`, `ui.settings`/`ui.alert`/`ui.bulletin`, `extera_utils.text_formatting`, `markdown_utils` (alias), `intents`, `elyx`; меню (MenuItemType ×5).
**4d. Хукинг Java:** `yili-hook` на LSPlant + Dobby, свой Xposed-совместимый слой (`XC_MethodHook`, `MethodHookParam`, `HookFilter`, в том числе MVEL-условия). Поддерживаемые ABI: arm64-v8a, armeabi-v7a, x86_64.
**4e. Расширенные возможности:** class proxy (`extera_utils.classes`) на DexMaker (Apache-2.0) и модификаторы `JA`/`JGS`/`JS`/`JIR`. Модификаторы требуют патча в Chaquopy (MIT, форкаем), поэтому это отдельный и самый рискованный подэтап.
**4f. pip:** свой резолвер (PyPI JSON API + `packaging`), только `py3-none-any` колёса, общий `libs/` с учётом ссылок от плагинов. Предустановлены `requests`, `beautifulsoup4`, `pillow`, `lxml`, `PyYAML`, `packaging`, `debugpy`.
**4g. Safe mode:** флаг «загрузка в процессе» до вызова `on_plugin_load`. Если процесс упал при загрузке, плагин отключается при следующем старте. После 2 крашей подряд при старте все плагины отключаются, показывается уведомление. Исключения в хуках ловятся; после N ошибок плагин автоматически отключается.
**4h. UI менеджера плагинов (M3):** список, переключатели, настройки, установка из файла, сообщения или ссылки, репозитории (`extera-plugins.json` и каналы), обновления, логи (`log_utils` → кольцевой буфер в UI), developer mode.
**4i. Dev-сервер:** `127.0.0.1:42690` по задокументированному протоколу (+ elyx-команды), debugpy. Публичный CLI `extera` не задокументирован, поэтому совместимость проверяю по протоколу и пишу свой клиент `tools/yili-dev` (watch → `write_plugin`).
**4j. `yili_ui`:** доступ к M3-компонентам и токенам YiliTheme из плагинов. Отдельный модуль, остальной SDK не меняется.
**4k. Примеры и проверка:** 3–5 примеров (команда `.hello`, автоперевод с настройками, хук на typing, плагин на `yili_ui`, Java-хук) и прогон популярных публичных плагинов exteraGram с таблицей совместимости.

**Что заведомо не будет совместимо:** плагины, которые обращаются к классам самого exteraGram (`com.exteragram.messenger.*`) или к абсолютному пути `/data/user/0/com.exteragram.messenger/...`. Сделаем shim-классы для самых частых случаев.

---

## 3. Риски

| Риск | Влияние | Что делаем |
|---|---|---|
| Объём: полный редизайн Telegram — это месяцы работы | Высокое | Строгий приоритет экранов, мост тем даёт «всё перекрашено» с первого дня, редизайн идёт итерациями |
| Мержи с upstream (ChatActivity на 48 тыс. строк) | Высокое | Минимальные врезки `// YILI`, логика в `com.yiligram.*`, мерж каждого релиза Telegram отдельной веткой |
| Лицензии: LSPlant LGPL-3.0 и Apache-2.0 несовместимы с **GPL-2.0-only** | Высокое | Использовать опцию «or later» из заголовков Telegram и выпускать YiliGram под **GPL-3.0-or-later** (см. вопрос 1). Я не юрист |
| Хукинг на Android 15/16, 16 KB page size, обновления ART | Среднее | LSPlant поддерживает до API 37; CI-тесты на эмуляторах 29/34/36 |
| Совместимость плагинов: не всё задокументировано (Chaquopy-форк, class proxy, TL-имена) | Среднее | Ориентируемся на поведение реальных плагинов, matrix-тест, shim |
| Размер APK +25–40 МБ (Python, колёса, хукинг) | Среднее | Сплиты по ABI, `extractPackages` только где нужно |
| Google Play запрещает скачивание исполняемого кода (pip, плагины) | Среднее | Распространяем через GitHub Releases / F-Droid-подобный канал |
| Безопасность: плагины имеют полный доступ к аккаунту | Среднее | Предупреждения, «неизвестные источники», показ кода перед установкой |
| Push-уведомления: FCM привязан к Firebase-проекту | Среднее | См. вопрос 2 |
| Нативная сборка: долгая (30–60 мин), тяжёлый toolchain | Низкое | ccache, кэш в CI, локальная установка SDK/NDK |
| Сайт M3 нельзя прочитать автоматически | Низкое | Токены берём из исходников MDC и material-design-tokens, визуал сверяем вручную |

---

## 4. Принятые решения (2026-11-18)
1. **Лицензия: GPL-3.0-or-later.** Telegram распространяется как «GPL v2 or later», что позволяет подключать LSPlant (LGPL-3.0), Dobby, material-color-utilities и DexMaker (Apache-2.0). В README и NOTICE указываем происхождение кода.
2. **Push: UnifiedPush.** Своей поддержки в upstream нет (`PushListenerController` знает только Firebase и Huawei). План: регистрируем endpoint UnifiedPush-дистрибьютора (ntfy и др.) как Web Push (`account.registerDevice`, `token_type = 10`, JSON с endpoint и ключами p256dh/auth), расшифровываем полезную нагрузку по RFC 8291 и передаём её в существующий обработчик пушей. FCM и google-services из сборки убираем. Без дистрибьютора работает фоновое соединение. *Риск:* поведение Telegram с web-push токенами от Android-клиента нужно проверить на практике; это отдельный подэтап 1.10.
3. **Тулчейн: локально + CI.** JDK 21, SDK 36, NDK 27.2.12479018 и CMake 3.22.1 ставим в `~/Android` без root; в GitHub Actions собираются релизы.
4. **Логотип: работаем с текущим** `branding/logog.png`: апскейл, вырезание фона, ручная векторизация силуэта для монохромной иконки и иконки уведомлений. Позже исходник можно заменить, генератор перезапускается одной командой.
5. `applicationId = com.yiligram.messenger` (по умолчанию из ТЗ).

---

## 5. Статус (2026-11-19)

| Этап | Готово | Осталось |
|---|---|---|
| 1. Форк и ребрендинг | Пакет, ключи из local.properties, подпись, иконки/splash/wordmark, ToS-тексты, CI, лицензии | UnifiedPush (1.10) |
| 2a. Токены | `yili-design`: цвет (MCU, 9 стилей, контраст, AMOLED, dynamic color), формы, типографика, пружины; 12 тестов | — |
| 2b. Мост Theme | Встроенные темы «Material You», тональный перенос 866 ключей + 283 привязки к ролям, fallback-ключи для тёмной темы | Тонкая подстройка по экранам |
| 2c. Компоненты | Кнопки, FAB, 20 expressive-форм, loading indicator с морфингом, debug-галерея , **M3-переключатели** во всём приложении (52×32, кружок 16→24dp, галочка), **M3-слайдеры** в настройках (дорожка 16dp, ручка-полоска, точки остановки; плееры не тронуты) | Button group, split button, FAB menu, chips, navigation rail, snackbar-стиль, чекбоксы/радио, смена цвета шапки при прокрутке (тень уже убрана, перекраска не срабатывает — фон ActionBar не ColorDrawable) |
| 2d. Экраны | M3 FAB во всех фрагментах, служебные плашки чата | **Пузыри без хвостиков** (все углы скруглены, в пресете «Экспрессия»). Осталось: настройки, профили (predictive back уже есть в Telegram 13: сжатие страницы до 90% со скруглением — совпадает с M3) |
| 3. Кастомизация | YiliConfig, экран «Оформление YiliGram» с превью, экспорт/импорт/сброс, **шрифты** (системный, Roboto Flex, Google Sans Flex, свой файл; насыщенность), **7 встроенных пресетов** (цвет+формы+анимации), **масштаб интерфейса** 80–125% (плотность, через DisplayMetrics) , **скрытие пунктов меню сообщения** (16 пунктов; удаление и реклама не скрываются), **нижние вкладки** (скрыть «Контакты»/«Профиль», только значки) , **панель ввода** (скрыть кнопку подарка, только голосовые) | Навигация (боковая панель), иконки, обои, кнопки шапки, ещё пункты панели ввода |
| 4. Плагины | Chaquopy/Python 3.11, движок, хуки запросов/апдейтов/сообщений, SDK-модули, pip, safe mode, менеджер, установка из чата, dev-сервер, совместимость с менеджером exteraGram; **Java-хуки** (`yili-hook`: LSPlant из исходников + Dobby + свой резолвер libart), **`intents`**, **class proxy** (`extera_utils.classes`, DexMaker), **пункты меню** в 4 меню Telegram, **debugpy** (VS Code, адаптер в процессе), **каталог из Git-репозиториев** (`extera-plugins.json`, проверка размера/SHA-256/метаданных); 7 примеров, 19 тестов + on-device самотесты | Telegram-каналы как репозитории, MVEL (`jMVELmethod`, `HookFilter.Condition`, `condition` у пунктов меню), статические модификаторы на классах (`SomeClass.JA`) |

### Совместимость с публичными плагинами (статически, `tools/plugin_compat.py`)
5/8 полностью. Остальные три используют внутренние классы самого exteraGram (бейджи,
реклама в ленте, `com.exteragram.messenger.*`) — к YiliGram не относятся.

### Java-хуки: как устроено
- `yili-hook/lsplant` — сабмодуль LSPlant (C++23-модули), собирается NDK 30 + CMake 3.31.6
  вместе с prefab libcxx от LSPosed; Maven-релиз 6.4 не работает на текущей ART
  (`JitCodeCache::GarbageCollectCache` → `DoCollection`).
- Dobby (prefab) — inline-хуки; символы libart — свой резолвер (`.dynsym`, `.symtab`,
  MiniDebugInfo через XZ Embedded), с поиском по префиксу.
- `YiliHook` / `MethodHookParam` — семантика Xposed; HiddenApiBypass включается при первом хуке.
- Проверено на эмуляторе API 35 x86_64 (`tools/device-tests/hook_selftest.plugin`).
  На реальных arm64-устройствах и Android 16 ещё не проверялось.

### Совместимость: что ещё отличается от exteraGram
- Форк Chaquopy у exteraGram по умолчанию отдаёт `obj.title` через `getTitle()` и даёт
  модификаторы `JA`/`JGS`/`JIR`/`JS`. У нас обычный Chaquopy 17.2, а `yili_java_ext`
  повторяет это для объектов (getter/setter-фолбэк, `JA`/`JNA`, `JGS`/`JNGS`, `JIR`/`JNIR`,
  `JS`/`JNS`, `java.JNone`). Отличия: getter используется, только если нет публичного поля
  с таким именем; статические варианты на классах (`SomeClass.JA`) недоступны.
- Release-сборка теперь не обфусцирует Telegram (`-dontobfuscate`, keep `org.telegram.**`):
  иначе плагины не нашли бы классы по именам.
