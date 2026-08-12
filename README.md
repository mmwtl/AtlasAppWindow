# Atlas App Window

[![Android CI](https://github.com/mmwtl/AtlasAppWindow/actions/workflows/android.yml/badge.svg)](https://github.com/mmwtl/AtlasAppWindow/actions/workflows/android.yml)

Atlas App Window запускает выбранное Android-приложение как отдельную freeform-задачу в заданном
прямоугольнике экрана автомобильного ГУ. Для запущенной Activity этот прямоугольник становится её
доступным окном, а HOME снаружи остаётся интерактивным. Поверх задачи Atlas рисует только тонкую
рамку и небольшую панель переключения.

Это практическая замена «приложению внутри виджета», а не настоящее межпроцессное встраивание
Activity. Обычный sideload APK на Android 11 не имеет системных прав для `ActivityView`. Поэтому
результат зависит от реализации freeform mode в прошивке ГУ.

> [!WARNING]
> Это экспериментальный инструмент для проверенного автомобильного ГУ Android 11 с экраном
> 1440×1920. Универсальная работа на телефонах, эмуляторах и других OEM-прошивках не заявляется.

## Что уже реализовано

- пресеты с точным launcher `ComponentName` и выбором из установленных приложений;
- настраиваемые `left / top / right / bottom` с живым preview;
- запуск и переключение приложения без полноэкранного overlay Atlas;
- рамка, перенос окна и кнопки «предыдущее / следующее / закрыть»;
- публичные команды `SHOW`, `SWITCH`, `HIDE` только по существующему локальному preset ID;
- pinned shortcuts для пресетов;
- автозапуск после загрузки;
- основной атомарный запуск через `ActivityOptions.setLaunchBounds()` и freeform mode `5`;
- опциональный loopback ADB для проверки task, точного resize и удаления только принятой задачи;
- интерфейс backend, позволяющий позже заменить freeform на привилегированный `ActivityView`;
- графитовая визуальная система Atlas Media Widget.

## Требования к ГУ

- Android 11 или новее;
- включённая поддержка freeform windows в прошивке;
- для рамки и панели — разрешение «поверх других приложений»;
- для показа панели только на HOME/целевом приложении — доступ к статистике использования;
- для точного контроля уже запущенной задачи — опциональный локальный ADB на `127.0.0.1`
  (по умолчанию порт `5555`).

Если прошивка не объявляет freeform feature, её обычно включают с внешнего ADB и перезагрузкой:

```sh
adb shell settings put global enable_freeform_support 1
adb shell settings put global force_resizable_activities 1
```

Atlas сам не меняет secure/global settings и не требует root. На части OEM-прошивок эти параметры
игнорируются либо freeform намеренно отключён.

## Первый запуск

1. Открыть Atlas App Window и выдать оба предложенных доступа.
2. Добавить приложение в секции пресетов.
3. Задать границы окна. Координаты — пиксели экрана, `right` и `bottom` не включаются.
4. Нажать «Проверить backend», затем «Показать» у нужного пресета.
5. При необходимости создать системный ярлык для этого пресета.

Успешный вызов `startActivity()` сам по себе не доказывает freeform. При доступном loopback ADB
Atlas дополнительно связывает запуск с конкретной задачей и проверяет её по `dumpsys`. Без ADB
статус является best-effort, а точное закрытие/resize может зависеть от OEM.

## Intent API

Экспортирована только прозрачная `CommandActivity`. Она не принимает произвольный package или
shell-аргументы — только ID уже сохранённого пресета.

Показать пресет:

```sh
adb shell am start \
  -n com.mmwtl.atlasappwindow/.CommandActivity \
  -a com.mmwtl.atlasappwindow.action.SHOW \
  --es com.mmwtl.atlasappwindow.extra.PRESET PRESET_ID
```

Сменить активное приложение:

```sh
adb shell am start \
  -n com.mmwtl.atlasappwindow/.CommandActivity \
  -a com.mmwtl.atlasappwindow.action.SWITCH \
  --es com.mmwtl.atlasappwindow.extra.PRESET PRESET_ID
```

Скрыть окно:

```sh
adb shell am start \
  -n com.mmwtl.atlasappwindow/.CommandActivity \
  -a com.mmwtl.atlasappwindow.action.HIDE
```

## Сборка

Нужны JDK 17 и Android SDK 36:

```sh
sh gradlew --offline clean check assembleRelease
```

Без локального signing-файла Gradle создаёт unsigned release APK в
`app/build/outputs/apk/release/`. Для установки на ГУ можно использовать debug APK из
`app/build/outputs/apk/debug/` либо настроить release-подпись по примеру
`app/secure.signing.gradle.example`. Keystore и пароли в Git не добавляются.

## Структура проекта

- `app/src/main/java/.../atlasappwindow` — приложение и адаптеры Android/OEM/ADB;
- `app/src/test` — JVM-тесты геометрии, команд, preset ID и task ownership;
- `docs/architecture.md` — границы платформы, backend-варианты и real-device acceptance;
- `.github/workflows/android.yml` — тесты и lint для pull request и ветки `main`, без сборки APK.

## Публикация релиза

CI не собирает и не публикует APK. Публичный релиз следует собрать и подписать локально через
игнорируемый `secure.signing.gradle`, проверить метаданные APK и приложить контрольную сумму
SHA-256. Имя архива формируется как `<versionName>[<versionCode>]AtlasAppWindow`.

## Технические ограничения

- Приложение с `singleTask`, запретом resize или собственным fullscreen-флагом может проигнорировать
  заданные границы даже при включённом freeform.
- OEM caption/decor чужого окна контролируется системой, не Atlas.
- Без системной подписи нельзя гарантировать настоящий embedded `ActivityView`, перенаправление
  touch input или скрытие системного decor произвольной Activity.
- MediaProjection даёт изображение, но не корректное интерактивное окно; поэтому она не используется.
- Atlas не делает `force-stop` чужих приложений и не посылает accessibility-жесты по координатам.

Подробная матрица вариантов находится в [docs/architecture.md](docs/architecture.md).
Лицензия ADB-библиотеки сохранена в [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) и внутри APK.

## GSplit

[GSplit](https://github.com/Salat39/GSplit) использован как публично доступный референс поведения: из него
подтверждена рабочая идея `setLaunchBounds + freeform windowingMode` и отдельных небольших overlay.
Код и ресурсы не копировались. В проверенной ревизии GSplit отсутствует лицензия, поэтому публичная
доступность репозитория сама по себе не даёт права переносить исходники.

Лицензия проекта: [MIT](LICENSE).
