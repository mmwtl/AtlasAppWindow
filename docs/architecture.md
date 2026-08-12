# Архитектура AtlasAppWindow

## Граница платформы

Точное требование «чужая Activity является дочерним View нашего приложения» выполняется на Android
11 только системным/привилегированным компонентом, например `ActivityView`/`CarActivityView`.
Обычный APK с `SYSTEM_ALERT_WINDOW` может размещать собственные View поверх экрана, но не может
поместить поверхность и input channel чужого UID внутрь такого View.

Текущий backend использует ближайшую корректную модель: самостоятельную freeform task Android WMS.
Целевая Activity получает конфигурацию своего ограниченного окна, а область HOME вне task остаётся
под управлением лаунчера.

## Варианты backend

| Вариант | Чужая Activity видит окно своим экраном | Touch внутри / снаружи | Обычный APK | Статус |
|---|---:|---:|---:|---|
| Freeform task + `ActivityOptions` | Да, в пределах task | Нативный WMS / HOME | Да, если разрешает OEM | Реализован |
| Freeform + loopback ADB | То же | То же | Нужен локальный `adbd` | Реализован опционально |
| `ActivityView` / `CarActivityView` | Да, настоящий embedded task | Нативная маршрутизация | Нет, нужны platform privileges | Точка расширения |
| `VirtualDisplay` + чужая Activity | Частично | Input и private-display policy проблемны | Не универсально | Не используется |
| MediaProjection/скриншот | Нет | Нет корректного input | Да с согласием пользователя | Не подходит |

## Поток запуска

1. Exported `CommandActivity` принимает только `presetId` и проверяет его allowlist-формат.
2. `OverlayService` разрешает ID в локально сохранённый точный `ComponentName`.
3. `DirectFreeformLauncher` одним options bundle передаёт bounds и `windowingMode=5`.
4. При доступном loopback ADB backend ищет новую задачу по точному component и проверяет её режим.
5. Только принятый task ID разрешено resize/remove. Package-wide `force-stop` запрещён.
6. `ChromeController` создаёт примыкающую серую шапку или компактную плавающую группу кнопок.
   Нетактильная маска закрывает прямоугольные углы чужой task цветом HOME и создаёт визуальное
   скругление; сама task при этом остаётся прямоугольной. Полноэкранного touch-слоя нет, поэтому
   область вокруг task не блокируется Atlas.

## Почему не копируется реализация GSplit

Полезная идея GSplit — прямой запуск freeform через `ActivityOptions`. Однако проверенная версия:

- не является Activity embedding — она также запускает обычные freeform tasks;
- не проверяет фактические mode/bounds после `startActivity()`;
- при сбое допускает обычный fullscreen fallback;
- сопоставляет task в основном по package;
- передаёт task ID в `am stack remove`, хотя команда ожидает stack ID;
- использует accessibility/координатные жесты и иногда `force-stop`;
- не содержит лицензии на проект.

Atlas независимо реализует только наблюдаемую архитектурную идею, со строгим preset API,
компонентным сопоставлением и отказом от package-wide действий.

## Будущий privileged backend

Если приложение будет встроено в системный образ и подписано platform key, реализация
`WindowBackend` может использовать `ActivityView`/OEM Car API. Для Android 11 это потребует как
минимум OEM-проверки доступных hidden APIs и привилегий управления activity stack/input. Такой
вариант надо собирать отдельным product flavor: sideload APK не должен объявлять или симулировать
права, которые ему никогда не выдадут.

## Приёмка на реальном ГУ

- target Activity получает ожидаемые размеры/configuration;
- touch и IME корректны внутри task;
- HOME и его элементы доступны снаружи bounds;
- переключение не оставляет неконтролируемые дубликаты task;
- закрывается только принятая Atlas task;
- bounds восстанавливаются после сна/перезапуска;
- отсутствие ADB не приводит к force-stop или удалению чужой задачи;
- приложение с запретом resize завершает запуск ошибкой, а не захватывает весь экран молча.
