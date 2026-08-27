# YMPlayer Changelog

This file tracks only working, testable versions. Broken or experimental
intermediate builds should not be added here.

## 1.0.0-beta.10 - 2026-08-27

Добавлена встроенная проверка и установка обновлений приложения.

- «О приложении YMPlayer» перенесено на первое место в главном списке
  настроек; в диалоге доступны QR-код и прямая ссылка Donate.Stream.
- В настройках появился отдельный раздел «Обновление приложения» с текущей
  версией, ручной проверкой и включаемой автопроверкой при запуске.
- Успешная автоматическая проверка выполняется не чаще одного раза в сутки и
  не показывает ошибку при отсутствии сети.
- Манифест обновления запрашивается сначала через GitHub, затем через
  резервный домен jsDelivr; загрузка APK также автоматически переключается на
  вторую ссылку при недоступности основной.
- Перед запуском системного установщика проверяются имя пакета, минимальная
  версия Android, размер APK и обязательная контрольная сумма SHA-256.
- APK передается установщику через закрытый `FileProvider`; на Android 8+
  приложение открывает штатную настройку разрешения установки из этого
  источника и продолжает после возврата.
- Сохранена совместимость с Android 10+ (`minSdk 29`).
- Выпуск: `YMPlayer-v1.0.0-beta.10-release-b110.apk`.

## 1.0.0-beta.9 - 2026-07-29

Добавлена добровольная поддержка автора.

- В окне «О программе» появился ненавязчивый блок с QR-кодом и поясняющим
  текстом.
- QR-код и кнопка «Поддержать автора» открывают страницу Donate.Stream во
  внешнем браузере.
- Блок доступен как для сенсорного управления, так и для ТВ-пульта.
- В README GitHub добавлен отдельный раздел поддержки с тем же QR-кодом и
  прямой ссылкой.
- Выпуск: `YMPlayer-v1.0.0-beta.9-release-b109.apk`.

## 1.0.0-beta.8 - 2026-07-22

Исправлено продолжение аудио «Моей волны» и разделено сенсорное управление с
навигацией ТВ-пультом.

- Rotor-ответ больше не считает уже играющий трек новой рекомендацией. Очередь
  пополняется только одним действительно новым следующим треком.
- Если сессия Яндекса вернула пустое или повторяющееся продолжение, YMPlayer
  восстанавливает rotor-сессию и повторяет запрос без добавления дублей.
- При временной сетевой ошибке на границе треков выполняются повторные попытки,
  а единичный недоступный трек волны автоматически пропускается.
- Фоновая подготовка следующего трека и переход по окончании текущего больше не
  создают два конкурирующих запроса продолжения.
- В настройках появился раздел «Управление» с вариантами «Авто», «Сенсор» и
  «Пульт». Автоматический режим выбирает пульт на Android TV и сенсорное
  управление на обычном Android.
- На смартфонах кнопки, чекбоксы и строки снова срабатывают одним касанием;
  белая рамка и D-pad-навигация сохранены для режима пульта.
- В диагностику добавлены выбранный и фактический режим управления, а также
  подробный путь восстановления очереди «Моей волны».
- Выпуск: `YMPlayer-v1.0.0-beta.8-release-b108.apk`.

## 1.0.0-beta.7 - 2026-07-15

Исправлен порядок кнопок встроенного SideBar.

- Кнопки «Сон» и «Перезагрузка» остаются в конце панели.
- Кнопка сворачивания SideBar теперь всегда расположена последней независимо
  от выбранной стороны экрана.
- Другие функции плеера и SideBar не изменялись.
- Выпуск: `YMPlayer-v1.0.0-beta.7-release-b107.apk`.

## 1.0.0-beta.6 - 2026-07-14

Добавлена отдельная синхронизация постоянных обложек для офлайн-избранного.

- В разделе настроек «Кэш» появилась кнопка «Синхронизировать обложки для
  избранного».
- Операция проверяет только уже скачанные треки «Мне нравится», обновляет их
  сохраненные метаданные и докачивает отсутствующие либо поврежденные обложки,
  не загружая аудиофайлы повторно.
- Синхронизация выполняется фоновым сервисом, учитывает ограничения Wi-Fi и
  зарядки, поддерживает остановку и показывает подробный итог.
- Постоянные обложки избранного отделены от очищаемого временного сетевого
  кэша: после успешной синхронизации они доступны без интернета.
- Резервная обложка YMPlayer заменена с launcher-иконки 192×192 px на отдельное
  изображение 1254×1254 px для четкого отображения в плеере, медиасессии и
  автомобильном лаунчере. Она используется только для показа и не записывается
  в файлы треков.
- Выпуск: `YMPlayer-v1.0.0-beta.6-release-b106.apk`.

## 1.0.0-beta.5 - 2026-07-14

Повторное исправление D-pad-фокуса после проверки beta.4 на реальном устройстве.

- Отказались и от `ViewOverlay`: на целевом устройстве эта подсветка также не
  отображалась над многими стандартными кнопками.
- Добавлен собственный stateful-фон `FocusHighlightDrawable`. Он получает
  состояния `focused`, `pressed` и `hovered` непосредственно от Android и
  рисует хорошо заметный белый контур толщиной 4 dp и светлую заливку как
  часть фона элемента, не полагаясь на callback фокуса или отдельный overlay.
- Нажатие получает более яркую заливку, а геометрия элемента не меняется.
- Динамические кнопки — источник воспроизведения, play/pause, режим очереди и
  лайк — сохраняют stateful-подсветку после каждой смены своего цвета.
- Общий рекурсивный аудит beta.4 сохранён: новый фон применяется на главном
  экране, в библиотеке, поиске, диалогах, настройках и Волне клипов.
- Выпуск: `YMPlayer-v1.0.0-beta.5-release-b105.apk`.

## 1.0.0-beta.4 - 2026-07-14

Исправление видимости фокуса на всех экранах и TV-прошивках.

- Подсветка больше не зависит от `View.setForeground()`, который на части
  Android TV и автомобильных прошивок не отображался поверх стандартных
  кнопок. Белая рамка и осветление теперь рисуются через `ViewOverlay`.
- Все интерактивные элементы принимают D-pad-фокус даже тогда, когда окно
  Android остаётся в touch-mode после касания или использования мыши.
- Выполнен полный рекурсивный аудит главного экрана, библиотеки, поиска,
  диалогов, всех разделов настроек и Волны клипов. Кнопки, поля ввода,
  чекбоксы, перемотка, кликабельные строки и selectable-тексты автоматически
  подключаются к единому визуальному отклику.
- Кнопки «Моя волна», «Режим оффлайн», «Волна клипов», пункты настроек и
  «Закрыть» используют одинаковую белую рамку, мягкую подсветку фокуса и
  более яркую вспышку нажатия без изменения размеров.
- Системная focus-анимация отключена для интерактивных элементов, поэтому
  прошивка не должна дополнительно увеличивать выбранные кнопки.
- Встроенный SideBar намеренно остаётся нефокусируемым D-pad: его overlay
  управляется касанием и не перехватывает пульт у активного приложения.
- Выпуск: `YMPlayer-v1.0.0-beta.4-release-b104.apk`.

## 1.0.0-beta.3 - 2026-07-14

Уточнение интерфейса пульта, управления Волной клипов и структуры настроек.

- Фокус при навигации пультом больше не увеличивает элементы и не сдвигает
  соседние кнопки: выбранный пункт отмечается белым контуром и мягким
  осветлением, а нажатие — более яркой вспышкой без изменения размеров.
- Единый визуальный отклик применяется к главному экрану, библиотеке, поиску,
  диалогам, настройкам и панели Волны клипов.
- Панель управления клипами автоматически скрывается через пять секунд после
  показа или последнего действия; первое нажатие Back скрывает панель, а
  повторное закрывает видеоплеер.
- Прогресс и время клипа перенесены в верхнюю часть панели. Полоса перемотки
  занимает всю её ширину, а название, исполнитель и крупные кнопки находятся
  ниже.
- Настройки стали полноэкранными и больше не выглядят как один длинный список:
  сначала показываются логические разделы, каждый открывается на отдельном
  экране, а Back возвращает к выбранному пункту списка.
- Навигация по разделам настроек получила тот же стабильный белый контур,
  осветление фокуса и усиленную индикацию нажатия.
- Выпуск: `YMPlayer-v1.0.0-beta.3-release-b103.apk`.

## 1.0.0-beta.2 - 2026-07-14

Исправление отображения YMPlayer в лаунчерах Android TV.

- Добавлена отдельная фирменная плитка-баннер 16:9 с логотипом YMPlayer,
  названием приложения и пометкой Android TV.
- Баннер назначен приложению и главной активности, поэтому TV-лаунчер больше
  не должен показывать пустой прямоугольник вместо оформления YMPlayer.
- Добавлена отдельная точка входа `LEANBACK_LAUNCHER`, при этом обычный
  launcher-вход для смартфонов и магнитол сохранён без изменений.
- Сенсорный экран и Leanback остаются необязательными возможностями, поэтому
  единый APK по-прежнему подходит для Android 10+ на всех целевых устройствах.
- Выпуск: `YMPlayer-v1.0.0-beta.2-release-b102.apk`.

## 1.0.0-beta.1 - 2026-07-14

Первый исправляющий beta-релиз с полноценным управлением на Android TV.

- Код устройства Яндекса теперь запрашивается автоматически при открытии
  раздела аккаунта и показывается над кнопкой входа с понятной инструкцией.
- Кнопка «Войти через Яндекс» использует уже показанный код и больше не
  перегенерирует его перед переходом в браузер; новый код выдаётся только
  отдельной кнопкой «Обновить код» или после истечения предыдущего.
- Android TV определяется по режиму устройства и системным TV-признакам.
  На телевизоре Волна клипов не включает принудительный immersive-режим, а
  при паузе, выходе и закрытии явно восстанавливает системные панели.
- Добавлена навигация крестовиной и кнопкой OK по основному экрану,
  библиотеке, поиску, диалогам, настройкам и управлению Волной клипов.
- Фокус пульта и мыши отмечается белой рамкой и увеличением элемента, а
  нажатие OK, касание и щелчок получают короткую анимацию нажатия.
- Рядом с настройками появилась отдельная кнопка перехода к поиску и
  плейлистам; на Android TV после смены страницы фокус переносится в рабочую
  область нового экрана.
- В настройках добавлена заметная строка статуса действий. Она подтверждает
  нажатия и отображает дальнейший результат, включая авторизацию и ход
  синхронизации кэша.
- Сенсорный экран объявлен необязательным, при этом сохранены Android 10+
  (`minSdk 29`) и обновляемая подпись release-сборки.
- Выпуск: `YMPlayer-v1.0.0-beta.1-release-b101.apk`.

## 1.0.0-beta - 2026-07-12

Первый полнофункциональный beta-релиз YMPlayer.

- Выпущен установочный `release` APK вместо отладочной сборки; сохранена
  возможность обновления установленных версий 0.x без потери данных.
- Волна клипов получила прогресс, текущее/общее время и перемотку.
- Следующий клип заранее добавляется в очередь Media3, поэтому штатный переход
  выполняется без отдельного экрана «Буферизация».
- В начале нового клипа панель управления больше не раскрывается: на пять
  секунд появляется отдельная подпись с названием и исполнителем.
- В строке следующего клипа теперь отображаются и название, и исполнитель.
- Добавлено автоскрытие системных панелей только в Волне клипов; настройка
  вынесена в отдельный переключатель, а при выходе панели Android возвращаются.
- Панель SideBar сохранила проверенную механику, при этом кнопки сна и
  перезагрузки перенесены в конец списка.
- Настройки полностью перегруппированы: аккаунт Яндекса расположен первым,
  отдельно оформлены качество, кэш, клипы, SideBar, DSP, системные действия и
  диагностика.
- Диагностический журнал можно сохранить в `Downloads` как текстовый файл с
  датой и временем в названии.
- По нажатию на логотип открывается окно с названием, версией и автором.
- Поиск получил обновлённую строку, мини-обложки и графические кнопки действий.
  Запоздалые ответы больше не перерисовывают закрытое окно и не заменяют
  результат более нового запроса.
- Обновлено пользовательское описание проекта и зафиксирован статус
  `1.0.0-beta`, `versionCode 100`, Android 10+ (`minSdk 29`).

## 0.5.2 - 2026-07-12

Clip Wave pre-test hardening and permanent liked-track artwork.

- Matched the Clip Wave start payload to the provided Kinopoisk Android TV
  client and removed two fields that its production rotor request leaves unset.
- Probe the complete initial clip response until the first playable stream is
  found instead of failing when only the first two clips are unavailable.
- Promote an item to the one-slot next queue only after its VH stream has been
  resolved successfully; unavailable initial items no longer delay the next
  transition.
- Preserve the rotor session that owns each current, next, and history item so
  start/finish/skip feedback remains attached to the right session after an
  automatic rotor restart.
- Added permanent real-cover sidecars beside liked-track audio. They survive
  Android's normal temporary-cache cleanup and are preferred by the main
  player, notification, and MediaSession during offline playback.
- Favorite synchronization now revisits already downloaded tracks, refreshes
  their metadata, validates existing artwork, and downloads missing real
  covers without redownloading valid audio.
- Kept cached audio bytes untouched. The YMPlayer logo is never embedded or
  persisted as track artwork; it remains only a UI/MediaSession fallback when
  no real cover is available.
- Removing a like, disliking a track, pruning favorites, or clearing local
  cache now removes the corresponding permanent cover as well.
- Updated the build to `YMPlayer-v0.5.2-debug-b62.apk` with `minSdk 29` kept.

## 0.5.1 - 2026-07-12

Final SideBar power-button simplification.

- Replaced the non-working SideBar shutdown button with an explicit Sleep
  button and crescent-moon icon.
- Restored the command proven by the original standalone SideBar on TS18:
  `com.nwd.action.ACTION_KEY_VALUE` with byte `extra_key_value=0`.
- Collapse the SideBar after a successful sleep request so it wakes in its
  unobtrusive edge-handle state.
- Removed the shutdown confirmation overlay, hidden StatusBar/PowerManager
  reflection, `sys.powerctl`, vendor shutdown-property broadcasts, and long
  power-key experiments from the active APK.
- Kept the separately confirmed TS18 reboot button and reduced its helper to
  only the direct/launcher-mediated `RebootActivity` paths.
- Kept audio playback, Clip Wave, My Wave, cache, playlists, artwork, and all
  other SideBar controls unchanged.
- Updated the build to `YMPlayer-v0.5.1-debug-b61.apk` while preserving
  Android 10 compatibility (`minSdk 29`).

## 0.5.0 - 2026-07-12

First testable Clip Wave release.

- Reverse-engineered the provided Kinopoisk Android TV client and implemented
  its dedicated Yandex Music video rotor:
  `rotor/combined/session/new`, one-item continuation requests, and clip
  playback feedback.
- Added a separate full-screen `Clip Wave` player based on AndroidX Media3,
  with adaptive HLS/DASH playback and a clip preview fallback when a full
  unprotected stream is unavailable.
- Added a modern auto-hiding video overlay with artist/title, previous clip,
  play/pause, next clip, like, and close controls for phone and landscape car
  screens.
- Kept only the current and one next clip locally. The next VH manifest is
  resolved in the background, and an empty rotor continuation restarts the
  dynamic clip session instead of ending the list.
- Connected clip likes to the first linked Yandex Music track. A like updates
  the account's global `Liked` collection and uses the existing automatic
  permanent-cache setting; removing it also removes that track from the liked
  cache.
- Added a dedicated platform MediaSession for full-screen clip playback, with
  clip metadata/artwork and previous/next/play/pause controls for system and
  car surfaces.
- Kept the established audio player, My Wave, cache, playlists, artwork, and
  SideBar implementations unchanged apart from stopping audio when Clip Wave
  opens.
- Preserved Android 10 compatibility (`minSdk 29`) and changed the build output
  to `YMPlayer-v0.5.0-debug-b60.apk`.
- YMPlayer does not bypass video DRM. If Yandex exposes only DRM-protected
  streams and no preview for a clip, the player skips it and requests another.

## 0.4.12 - 2026-07-11

TS18 shutdown confirmation and vendor power-off path.

- Added a YMPlayer-owned confirmation overlay for the embedded SideBar power
  button, so confirmation no longer depends on firmware allowing a third-party
  app to open Android SystemUI GlobalActions.
- Added the NWD privileged system-property bridge found in the factory TS18
  libraries: after explicit confirmation YMPlayer mirrors the factory
  `nwd_system_prop` record and requests
  `sys.powerctl=shutdown,userrequested` through
  `com.nwd.action.ACTION_SET_SYSTEM_PROP`.
- Added guarded direct Android `PowerManager`/`SystemProperties` attempts for
  firmware variants that expose them, plus a launcher-mediated shutdown
  activity request.
- Replaced the old single `extra_key_value=0` fallback with the factory NWD
  power-event sequence `DOWN -> LONGPRESS -> UP` using `extra_key_type`.
- Kept playback code and the already confirmed separate reboot button
  unchanged.

## 0.4.11 - 2026-07-03

Artwork stability after ACC/sleep resume.

- Added a shared artwork cache used by the main screen, playback notification,
  and MediaSession metadata, so CarWebGuru can reuse already downloaded covers
  after the head unit wakes up instead of seeing the YMPlayer logo while the
  network is still recovering.
- Added bounded retry for current-track artwork when a cover request fails
  during wake-up or delayed storage/network availability.
- Decode remote and embedded artwork with a size cap before putting bitmaps into
  UI, notification, and MediaSession metadata to reduce memory pressure and
  binder-size risk on Android car launchers.
- Kept the 0.4.10 SideBar shutdown/reboot buttons unchanged for testing.

## 0.4.10 - 2026-07-03

TS18 shutdown request path from firmware analysis.

- Investigated the provided TS18 3.1 firmware image and confirmed that the
  visible power menu is Android SystemUI `GlobalActions`, while the old SideBar
  power-key fallback only sends the TS18/NWD power key and can put the unit to
  sleep.
- Added non-Accessibility shutdown handling through hidden Android
  `StatusBarManager.showGlobalActions()` /
  `IStatusBarService.showGlobalActionsMenu()` before the old
  `ACTION_REQUEST_SHUTDOWN` fallback.
- Kept the confirmed separate TS18 reboot button and its launcher-mediated
  `RebootActivity` path unchanged.
- Kept direct `com.nwd.action.ACTION_MCU_POWER_OFF` disabled: firmware analysis
  showed it is a power-off state/event broadcast, not a confirmation UI request.

## 0.4.9 - 2026-07-02

Separate SideBar shutdown and reboot buttons, with Accessibility removed again.

- Removed YMPlayer `AccessibilityService`, its manifest declaration, XML
  metadata, settings status, and the button that opened Android Accessibility
  settings.
- Changed the embedded SideBar power button to request shutdown through
  Android `ACTION_REQUEST_SHUTDOWN`.
- Added a separate embedded SideBar reboot button that uses the confirmed TS18
  reboot UI path from 0.4.8.
- Kept direct `com.nwd.action.ACTION_MCU_POWER_OFF` unused because it is a
  direct firmware power-off signal, not a confirmation request.

## 0.4.8 - 2026-07-01

TS18 SideBar power-menu fallbacks that do not rely only on Accessibility.

- Kept the working optional `GLOBAL_ACTION_POWER_DIALOG` path through the
  manually enabled YMPlayer accessibility service.
- Added a second TS18 reboot UI candidate from the factory launcher table:
  `com.nwd.toolallinone.app/com.nwd.tools.reboot.RebootActivity`.
- Added launcher-mediated TS18 start requests for the reboot UI through
  `com.nwd.ACTION_REQUEST_START_ACTIVITY`, `com.nwd.action.ACTION_START_ACTIVITY`,
  and `com.nwd.action.ACTION_START_NWD_ACTIVITY` with `extra_package_name` and
  `extra_class_name`.
- Kept the Android `ACTION_REQUEST_SHUTDOWN` fallback, but still avoids direct
  MCU power-off and the old `extra_key_value=0` sleep path.

## 0.4.7 - 2026-06-23

Optional SideBar power-menu service.

- Restored a minimal optional YMPlayer accessibility service only for the
  embedded SideBar power button.
- The service is not enabled automatically and is not required for normal
  player playback. Settings show its current status and open Android
  Accessibility settings, matching the standalone SideBar flow.
- The power button first uses Android `GLOBAL_ACTION_POWER_DIALOG` when the
  service is enabled, then falls back to the TS18 reboot activity and Android
  shutdown confirmation request.
- The service does not retrieve window content and is not used for playback,
  library, Yandex Music authorization, or cache behavior.

## 0.4.6 - 2026-06-22

Best-effort SideBar power menu without AccessibilityService.

- Added a non-accessibility SideBar power path based on the TS18 SystemUI
  finding: first YMPlayer tries to open
  `com.android.launcher/com.nwd.tools.reboot.RebootActivity`.
- If the TS18 reboot activity is unavailable or blocked by firmware, YMPlayer
  falls back to Android's hidden `ACTION_REQUEST_SHUTDOWN` confirmation dialog.
- Removed the old TS18 `extra_key_value=0` power fallback from YMPlayer because
  on the test head unit it turns the screen off instead of opening the power
  menu.
- The APK still contains no `AccessibilityService` or
  `BIND_ACCESSIBILITY_SERVICE`.

## 0.4.5 - 2026-06-22

Install-safe build after Google Play Protect blocked the APK.

- Removed `AccessibilityService` from YMPlayer. It triggered Google Play
  Protect hard blocking on a Redmi Note 14 Pro and was unreliable on TS18 head
  units because the firmware can disable accessibility services.
- Kept the embedded SideBar overlay, edge pull-out behavior, volume, mute,
  home, and back controls.
- The embedded SideBar power button is temporarily disabled in this build and
  shows an explanatory message instead of using an unreliable accessibility
  shortcut.
- Kept version and APK names informative: `YMPlayer-v0.4.5-debug-b45.apk`.

## 0.4.4 - 2026-06-20

Legacy cleanup and local artwork polish.

- Removed the active Poweramp-era cache sync path: `CacheSyncService` now uses
  the standalone YMPlayer repository and keeps permanent sync limited to global
  liked tracks.
- Moved `YandexTrackCache` into the neutral `dev.petrov.yaplay.cache` package
  while preserving the existing on-device cache directories.
- Deleted unused Poweramp provider/tree-picker classes and obsolete
  `tree_picker_*` strings.
- The main player screen now reads embedded artwork from local files when there
  is no remote cover URL.
- Settings now show whether the YMPlayer Accessibility service needed for the
  SideBar power menu is enabled.

## 0.4.3 - 2026-06-20

Artwork fallback fix for CarWebGuru and YMPlayer.

- Fixed sticky artwork in CarWebGuru when the next track has no cover URL, no
  embedded local artwork, or a failed cover download.
- MediaSession metadata and playback notifications now always publish an artwork
  bitmap: the real cover when available, otherwise the YMPlayer launcher icon.
- The main player screen now resets to the YMPlayer icon immediately when a new
  cover URL starts loading, so it no longer shows the previous track cover while
  waiting for a failed or slow cover request.
- Source selection without a current track now also publishes default YMPlayer
  artwork instead of leaving old MediaSession metadata intact.

## 0.4.2 - 2026-06-20

Power menu, playback-position restore, and passive source switching.

- Changed the embedded SideBar power button to use YMPlayer's accessibility
  service and Android `GLOBAL_ACTION_POWER_DIALOG`, matching the working
  power-menu path from the standalone SideBar instead of sending the TS18 power
  key that puts some head units to sleep.
- Added a settings shortcut to Android Accessibility settings for enabling the
  YMPlayer power-menu service.
- Added periodic playback-position persistence while a track is prepared or
  playing, plus an explicit save on pause, stop, source switch, and service
  destroy.
- Source switching now stops the current playback and only selects the next
  source. The selected My Wave/cache/playlist starts when Play is pressed.
- YMPlayer now remembers the last track, queue index, position, and queue mode
  per offline cache, Yandex playlist, and local playlist.

## 0.4.1 - 2026-06-20

Player state, SideBar parity, artwork, and lighter My Wave prefetch.

- Restored the embedded SideBar button behavior and edge-swipe model from the
  standalone SideBar project while keeping YMPlayer's larger panel buttons.
- Added an embedded SideBar auto-hide checkbox in settings.
- Fixed the audio-quality settings buttons being invisible because they were
  laid out with row-only zero-width parameters in the vertical settings list.
- Persisted the current playback source, queue, selected track, play mode,
  shuffle state, like state, and approximate position so the UI can restore the
  last player state after app restart or orientation changes.
- Reset MediaSession and notification artwork per current track before loading
  a new bitmap, preventing stale covers in launchers such as CarWebGuru during
  shuffle playback.
- Reduced My Wave expansion to one next track at a time: YMPlayer starts from
  the first track and only asks the rotor API for the next item when needed.

## 0.4.0 - 2026-06-15

Visible audio quality settings and milestone bump.

- Promoted YMPlayer to 0.4.0 after the accumulated player, search, local
  playlist, My Wave, and cache-quality changes became substantial enough for a
  minor-version milestone.
- Moved audio quality controls into a dedicated `Audio quality` settings
  section near the top of the settings dialog.
- Made online and permanent-cache quality controls more visible with separate
  accent-colored full-width buttons.
- Added an explanatory hint that changed quality applies to new downloads, while
  existing cached files keep their current media quality until cache refresh.

## 0.3.7 - 2026-06-15

My Wave startup and audio quality controls.

- My Wave now starts from the first received rotor-session batch instead of
  waiting until the initial queue is expanded to the full target size.
- After the first track is prepared, YMPlayer preloads the next audio file and
  loads more My Wave batches in the background.
- Added separate quality settings for online playback cache and permanent
  liked-track cache.
- Quality profiles select the closest available Yandex Music download variant:
  Auto/Maximum, Economy 128, Standard 192, and High 320.
- Existing cached files are reused as-is; changed quality applies to new
  temporary downloads and to liked tracks downloaded after cache refresh.

## 0.3.6 - 2026-06-15

Search UI polish.

- Reworked the search entry field into a rounded search bar with the action
  button inside the frame.
- Replaced plain text search results with media-style result cards.
- Added mini cover artwork for track, album, and artist search results.
- Added explicit result actions: play track, add found track to a Yandex Music
  playlist, play album, and play artist top tracks.
- Adding a found track to a playlist can use an existing account playlist or
  create a new Yandex Music playlist.

## 0.3.5 - 2026-06-15

Built-in local media browser.

- Replaced separate local "Add files" and "Add folder" actions with one Add
  action.
- Added YMPlayer's own SAF-based browser for previously granted storage roots.
- The browser shows folders and audio files with checkboxes, supports folder
  navigation, and imports the selected files and folders in one pass.
- Added persistent storage-root history, while keeping Android's system folder
  picker only for the first permission grant to a new local/USB storage root.
- Folder selections still integrate with local playlist folder refresh and
  removed-track exclusions from 0.3.4.

## 0.3.4 - 2026-06-15

Local playlist management polish.

- Added local playlist rename from the Library screen.
- Added a local track list dialog with per-track removal.
- Removed local folder tracks are now remembered as exclusions so they do not
  reappear after folder refresh.
- Folder imports now store their source folder URI for future refreshes.
- Added refresh for imported local folders.
- Local playlist rows now show both track count and imported folder count.

## 0.3.3 - 2026-06-14

Search playback foundation.

- Added real Yandex Music search from the Library page.
- Search results now show separate track, album, and artist sections.
- Track results start playback directly as a search queue.
- Album results load album tracks and start album playback.
- Artist results load artist top tracks and start artist playback.
- Added search source labels to the player status area.
- Favorite-artist actions remain in research until the Yandex endpoint is
  validated on a real account.

## 0.3.2 - 2026-06-14

Local playlist control, DSP selection, and SideBar touch polish.

- Renamed the release notes file from `WHATSNEW.md` to `CHANGELOG.md`.
- Added the built-in non-deletable `Local favorites` playlist. Liking a local
  file adds it there; unliking or disliking a local file removes it from there.
- Added local playlist deletion and local favorites clearing with confirmation.
- Added Yandex Music account playlist deletion with confirmation and library
  refresh after successful API deletion.
- Local playlist playback now skips missing or inaccessible files, such as
  tracks from a removed USB drive. If no file in the queue is accessible,
  playback stops instead of retrying forever.
- Reworked the EQ/DSP button to open a chooser of detected DSP/EQ apps instead
  of falling through to Android sound settings.
- Adjusted the built-in SideBar: power tap now sends the same TS18 power key
  broadcast as the standalone SideBar, the edge hotspot is 5 physical pixels,
  and the collapsed handle opens only by swipe.

## 0.3.1 - 2026-06-14

Local artwork enrichment.

- Added best-effort embedded artwork enrichment for imported local files.
- If a local file has no embedded cover, YMPlayer searches public metadata
  sources and tries to write the found artwork into the file tags.
- The process is silent when no artwork is found, there is no internet, the
  provider is read-only, or the format cannot be tagged.
- Local embedded artwork is now also used in YMPlayer's MediaSession metadata
  and notification artwork when available.

## 0.3.0 - 2026-06-14

Playlist editing and local playlists.

- Added a main-player action to add the current Yandex Music track to an
  existing account playlist.
- Added creation of a new Yandex Music account playlist from YMPlayer and
  immediate insertion of the current track.
- Added a separate Local playlists section in Library.
- Added app-only local playlists that can import audio files or scanned folders
  through Android's system picker, including USB/storage providers that grant
  persistent read access.
- Added local playlist playback through the same transport controls, with
  shuffle/repeat available because local lists are static queues.

## 0.2.3 - 2026-06-14

Library cleanup.

- Removed the local "Recommendations" and "Favorites" shortcut blocks from the
  Library screen.
- Left the Library screen focused on real Yandex Music account playlists from
  the user's collection.
- Removed the unfinished My Wave mood/activity filter button from the player
  screen until a stable Yandex Music endpoint is validated.

## 0.2.2 - 2026-06-14

Car UI, SideBar, and responsiveness polish.

- Added bitmap album art into MediaSession metadata and notification large icon
  so car launchers have a better chance to receive cover art.
- Added background prefetch of the next online track for My Wave and playlists.
- Moved expensive cache status calculation off the main UI thread.
- Improved status text contrast on dark surfaces.
- Added an EQ/DSP button; on wide landscape screens it sits in the main
  playback control row.
- Added optional equalizer package setting plus standard Android audio-effects
  panel fallback.
- Added SideBar power-button long tap to request the system power menu when the
  firmware allows it.

## 0.2.1 - 2026-06-14

Library and playlist source foundation.

- Added a passive playlist source button on the main player screen.
- Added playback service support for user playlist queues.
- Added a second Library screen reachable by horizontal swipe.
- Added Library sections for recommendations, favorites, and user playlists.
- Added on-demand loading of user playlists from the Yandex Music account.
- Added playlist selection without automatic playback; Play starts the selected
  playlist when the player is stopped.
- Added a My Wave filter button as a UI entry point for later validated filters.
- Added a Search entry point for the next 0.2.x search implementation.

## 0.2.0 - 2026-06-14

Baseline standalone YMPlayer release.

- Promoted the project from the Poweramp bridge experiment to a standalone
  Android 10+ Yandex Music player.
- Added the main player screen with cover art, track title, artist, album,
  like/dislike controls, large transport controls, and highlighted source
  selection.
- Added My Wave playback through the Yandex Music rotor API with session
  feedback and load-more/prefetch behavior.
- Added offline playback from YMPlayer's own permanent liked-track cache.
- Kept permanent offline storage limited to global Yandex Music liked tracks.
- Added a separate temporary playback cache for non-liked tracks.
- Added cache sync, cache cleanup, diagnostics, Yandex login/token settings,
  and automatic cache of newly liked tracks.
- Added a built-in optional SideBar overlay for TS18-style head units.
- Added Android MediaSession/MediaBrowser playback service support for car
  launchers such as CarWebGuru.
- Added buttons for Android battery and autostart settings.

## Versioning Policy

- Patch versions are for fixes and incremental improvements on the current
  standalone player foundation.
- Minor versions are for significant working additions, such as library/search
  workflows, playlist playback, or validated My Wave filters.
- Archive only working APKs that are worth returning to during real-device
  testing.
- Keep APK file names informative: `YMPlayer-v<version>-<variant>-b<code>.apk`.
