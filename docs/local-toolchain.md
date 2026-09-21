# Локальный toolset проекта (`.tools`)

Эта инструкция описывает локальное окружение DayBricks для Windows и
PowerShell. Все SDK, кэши и эмулятор можно хранить в `.tools` внутри корня
проекта, не устанавливая JDK, Go, Gradle или Android SDK глобально.

Каталог `.tools/` исключён из Git. Это правильно для больших бинарных файлов,
кэшей, логов, AVD и локального debug-ключа, но означает, что он не появится
после обычного `git clone` и не должен считаться резервной копией. Поэтому эта
инструкция находится в `docs/`, за пределами `.tools`.

Связанные документы:

- [README проекта](../README.md) — назначение, основные возможности и быстрый
  старт;
- [архитектура](architecture.md) — устройство Android-приложения и сервера;
- [поведение интерфейса](ui-behavior.md) — правила календаря и адаптивного UI;
- [протокол синхронизации](sync-protocol.md) — контракт приложения с сервером;
- [журнал проверок](verification.md) — фактически выполненные проверки и их
  ограничения.

Команды ниже рассчитаны на запуск из корня репозитория:

```powershell
Set-Location C:\path\to\DayBricks
$ProjectRoot = (Get-Location).Path
$ToolsRoot = Join-Path $ProjectRoot '.tools'
```

## Что находится в существующей `.tools`

Обязательные или используемые части:

| Путь | Назначение |
|---|---|
| `jdk-21.0.12.1+1/` | Microsoft Build of OpenJDK 21 для Gradle, Kotlin и Android SDK tools |
| `android-sdk/` | Android SDK, Build Tools, `adb` и эмулятор |
| `gradle-home/` | Локальный `GRADLE_USER_HOME`: Gradle 9.3.1, зависимости и рабочие кэши |
| `go127/go/` | Go 1.27.1 для sync-сервера |
| `go-cache/`, `go-mod-cache/`, `go-tmp/` | Локальные кэши и временные файлы Go |
| `android-user/` | Локальный Android user home и debug keystore |
| `avd/` | AVD `DayBricks` и его виртуальный диск |

В текущем Android SDK установлены:

- Command-line Tools 19.0;
- Platform Tools 37.0.1;
- Platform 37.0, необходимая для `compileSdk = 37`;
- Build Tools 36.0.0;
- Emulator 37.1.11;
- образ `system-images;android-36;google_apis;x86_64` для тестового AVD.

## Активация существующего toolset

Переменные ниже действуют только в текущем окне PowerShell. Они не изменяют
системный `PATH` и не влияют на другие проекты.

```powershell
$ProjectRoot = (Get-Location).Path
$ToolsRoot = Join-Path $ProjectRoot '.tools'

$env:JAVA_HOME = (Resolve-Path "$ToolsRoot/jdk-21.0.12.1+1").Path
$env:ANDROID_HOME = (Resolve-Path "$ToolsRoot/android-sdk").Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = "$ToolsRoot/android-user"
$env:ANDROID_AVD_HOME = "$ToolsRoot/avd"
$env:GRADLE_USER_HOME = "$ToolsRoot/gradle-home"

$env:GOROOT = (Resolve-Path "$ToolsRoot/go127/go").Path
$env:GOCACHE = "$ToolsRoot/go-cache"
$env:GOMODCACHE = "$ToolsRoot/go-mod-cache"
$env:GOTMPDIR = "$ToolsRoot/go-tmp"
$env:GOTOOLCHAIN = 'local'

$env:Path = @(
    "$env:JAVA_HOME/bin"
    "$env:ANDROID_HOME/platform-tools"
    "$env:ANDROID_HOME/emulator"
    "$env:ANDROID_HOME/cmdline-tools/latest/bin"
    "$env:GOROOT/bin"
    $env:Path
) -join ';'

New-Item -ItemType Directory -Force `
    $env:ANDROID_USER_HOME, $env:ANDROID_AVD_HOME, $env:GRADLE_USER_HOME, `
    $env:GOCACHE, $env:GOMODCACHE, $env:GOTMPDIR | Out-Null
```

После перемещения репозитория нужно обновить `android/local.properties`,
поскольку этот файл содержит абсолютный путь и исключён из Git:

```powershell
$SdkForGradle = $env:ANDROID_HOME.Replace('\', '/').Replace(':', '\:')
Set-Content -Encoding ASCII android/local.properties "sdk.dir=$SdkForGradle"
```

Быстрая проверка окружения:

```powershell
java -version
go version
adb version
emulator -version
.\android\gradlew.bat -p android --version
```

Ожидаются JDK 21, Go 1.27.x, доступный Android SDK и Gradle 9.3.1. Отдельно
устанавливать Gradle не надо: `android/gradlew.bat` скачивает закреплённую
версию в `.tools/gradle-home`, а SHA-256 дистрибутива уже записан в
`android/gradle/wrapper/gradle-wrapper.properties`.

При желании блок активации можно сохранить локально как
`.tools/activate.ps1` и запускать так:

```powershell
. .\.tools\activate.ps1
```

Начальная точка важна: точка и пробел перед путём выполняют скрипт в текущей
PowerShell-сессии, поэтому заданные им переменные сохраняются.

## Восстановление `.tools` с нуля

Ниже приведён вариант без глобальной установки. Требуются Windows x64,
PowerShell, доступ в интернет и несколько гигабайт свободного места.
Аппаратная виртуализация нужна только для эмулятора; физический телефон можно
использовать без AVD.

### 1. Создать каталоги

```powershell
$ProjectRoot = (Get-Location).Path
$ToolsRoot = Join-Path $ProjectRoot '.tools'

New-Item -ItemType Directory -Force `
    $ToolsRoot, `
    "$ToolsRoot/android-sdk/cmdline-tools", `
    "$ToolsRoot/android-user", `
    "$ToolsRoot/avd", `
    "$ToolsRoot/gradle-home", `
    "$ToolsRoot/go-cache", `
    "$ToolsRoot/go-mod-cache", `
    "$ToolsRoot/go-tmp" | Out-Null
```

### 2. Скачать и распаковать JDK 21

Текущий проверенный архив — Microsoft OpenJDK 21.0.12.1 для Windows x64.
Официальная страница загрузки также публикует checksum.

```powershell
$JdkArchive = "$ToolsRoot/jdk.zip"
Invoke-WebRequest `
    -Uri 'https://aka.ms/download-jdk/microsoft-jdk-21.0.12.1-windows-x64.zip' `
    -OutFile $JdkArchive

$ExpectedJdkSha256 = '192441A9D27DA813BADA974BB88B4CF64D37A9589ED37F204374D411CA5CE07F'
if ((Get-FileHash $JdkArchive -Algorithm SHA256).Hash -ne $ExpectedJdkSha256) {
    throw 'JDK archive checksum mismatch'
}

Expand-Archive -LiteralPath $JdkArchive -DestinationPath $ToolsRoot
$env:JAVA_HOME = (Resolve-Path "$ToolsRoot/jdk-21.0.12.1+1").Path
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
java -version
```

Если Microsoft обновил архив и ссылка уже указывает на другую сборку,
возьмите актуальные ссылку и SHA-256 с официальной страницы, распакуйте JDK в
`.tools`, затем измените путь в блоке активации. Не отключайте проверку hash.

### 3. Установить Android SDK локально

Скачайте Windows ZIP из секции **Command line tools only** официальной
страницы Android Studio. Имя и hash архива меняются при выпуске новой версии.
Текущий стабильный архив на момент написания —
`commandlinetools-win-15859902_latest.zip`, SHA-256
`90AE805D20434428BFFCB699C290860F19BB5F66A67E6B330067E3DE801FB04A`.

```powershell
$AndroidCliArchive = "$ToolsRoot/android-cli.zip"
$AndroidCliTemp = "$ToolsRoot/android-cli-extracted"

Invoke-WebRequest `
    -Uri 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' `
    -OutFile $AndroidCliArchive

$ExpectedAndroidCliSha256 = '90AE805D20434428BFFCB699C290860F19BB5F66A67E6B330067E3DE801FB04A'
if ((Get-FileHash $AndroidCliArchive -Algorithm SHA256).Hash -ne $ExpectedAndroidCliSha256) {
    throw 'Android command-line tools checksum mismatch'
}

Expand-Archive -LiteralPath $AndroidCliArchive -DestinationPath $AndroidCliTemp
New-Item -ItemType Directory -Force "$ToolsRoot/android-sdk/cmdline-tools/latest" | Out-Null
Move-Item -Path "$AndroidCliTemp/cmdline-tools/*" `
    -Destination "$ToolsRoot/android-sdk/cmdline-tools/latest"

$env:ANDROID_HOME = (Resolve-Path "$ToolsRoot/android-sdk").Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$SdkManager = "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat"
```

Установите минимальный SDK для сборки и работы с устройством:

```powershell
& $SdkManager --sdk_root=$env:ANDROID_HOME `
    'platform-tools' `
    'platforms;android-37.0' `
    'build-tools;36.0.0'

& $SdkManager --sdk_root=$env:ANDROID_HOME --licenses
```

Лицензии нужно прочитать и принять интерактивно. Для текущего проекта нужны
именно Platform 37.0 и Build Tools 36.0.0; повышение версий следует делать
осознанно вместе с проверкой AGP, lockfiles и CI.

Создайте `android/local.properties`:

```powershell
$SdkForGradle = $env:ANDROID_HOME.Replace('\', '/').Replace(':', '\:')
Set-Content -Encoding ASCII android/local.properties "sdk.dir=$SdkForGradle"
```

### 4. Установить Go локально

Sync-сервер объявляет `go 1.27.0`; текущий toolset использует Go 1.27.1.

```powershell
$GoArchive = "$ToolsRoot/go127.zip"
Invoke-WebRequest `
    -Uri 'https://go.dev/dl/go1.27.1.windows-amd64.zip' `
    -OutFile $GoArchive

$ExpectedGoSha256 = 'A3911B5E0E1B1053F25ED0675F4C1C6AAD1E2BFCF253DF2B9BE4CAABD2EDD95D'
if ((Get-FileHash $GoArchive -Algorithm SHA256).Hash -ne $ExpectedGoSha256) {
    throw 'Go archive checksum mismatch'
}

New-Item -ItemType Directory -Force "$ToolsRoot/go127" | Out-Null
Expand-Archive -LiteralPath $GoArchive -DestinationPath "$ToolsRoot/go127"

$env:GOROOT = (Resolve-Path "$ToolsRoot/go127/go").Path
$env:GOCACHE = "$ToolsRoot/go-cache"
$env:GOMODCACHE = "$ToolsRoot/go-mod-cache"
$env:GOTMPDIR = "$ToolsRoot/go-tmp"
$env:GOTOOLCHAIN = 'local'
$env:Path = "$env:GOROOT/bin;$env:Path"
go version
```

### 5. Подготовить Gradle

Gradle уже предоставлен репозиторием как Wrapper. После задания
`JAVA_HOME` и `GRADLE_USER_HOME` достаточно выполнить:

```powershell
$env:GRADLE_USER_HOME = "$ToolsRoot/gradle-home"
.\android\gradlew.bat -p android --version
```

При первом вызове Wrapper загрузит Gradle 9.3.1 и проверит закреплённый hash.
Следующие сборки используют локальную копию из `.tools/gradle-home`.

### 6. Опционально создать эмулятор

Для тестов на физическом устройстве этот шаг не нужен.

```powershell
& $SdkManager --sdk_root=$env:ANDROID_HOME `
    'emulator' `
    'platforms;android-36' `
    'system-images;android-36;google_apis;x86_64'

$env:ANDROID_USER_HOME = "$ToolsRoot/android-user"
$env:ANDROID_AVD_HOME = "$ToolsRoot/avd"
$AvdManager = "$env:ANDROID_HOME/cmdline-tools/latest/bin/avdmanager.bat"

'no' | & $AvdManager create avd `
    --force `
    --name 'DayBricks' `
    --package 'system-images;android-36;google_apis;x86_64' `
    --device 'pixel_5'
```

Текущий AVD использует Pixel 5, x86_64 Google APIs, 2 ГБ RAM и диск 2 ГБ.
Эти значения можно при необходимости настроить в
`.tools/avd/DayBricks.avd/config.ini` при выключенном эмуляторе.

Файлы `.tools/avd/*.ini` содержат абсолютные пути. После перемещения проекта
проще пересоздать AVD этой командой, чем переносить старый виртуальный диск.

Для аппаратного ускорения на Windows должна быть включена виртуализация в
UEFI/BIOS и подходящий системный hypervisor. Это единственная часть окружения,
которую невозможно полностью изолировать внутри проекта. Альтернатива —
подключённый Android-телефон.

## Ежедневная разработка Android

Перед работой активируйте toolset в новом PowerShell-окне. Затем используйте
Gradle Wrapper из корня репозитория.

Быстрая проверка ресурсов после изменения XML:

```powershell
.\android\gradlew.bat -p android :app:processDebugResources --console=plain
```

Компиляция Kotlin без сборки APK:

```powershell
.\android\gradlew.bat -p android :app:compileDebugKotlin --console=plain
```

Debug APK:

```powershell
.\android\gradlew.bat -p android :app:assembleDebug --console=plain
```

Результат:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Для проекта всегда используется `android/gradlew.bat`.

## Запуск эмулятора

После активации toolset запустите AVD в отдельном PowerShell-окне:

```powershell
emulator -avd DayBricks -no-boot-anim
```

Либо запустите его отдельным процессом:

```powershell
Start-Process `
    -FilePath "$env:ANDROID_HOME/emulator/emulator.exe" `
    -ArgumentList @('-avd', 'DayBricks', '-no-boot-anim')
```

Проверка готовности:

```powershell
adb wait-for-device
do {
    Start-Sleep -Seconds 2
    $BootCompleted = (& adb shell getprop sys.boot_completed).Trim()
} until ($BootCompleted -eq '1')

adb devices -l
```

Корректно закрывать эмулятор следует так:

```powershell
adb emu kill
```

## Запуск на физическом телефоне

1. Включите Developer options и USB debugging.
2. Подключите телефон и подтвердите RSA-запрос на его экране.
3. Выполните `adb devices -l`; состояние должно быть `device`, а не
   `unauthorized` или `offline`.
4. Если подключено несколько устройств, используйте `adb -s SERIAL ...` или
   задайте `$env:ANDROID_SERIAL = 'SERIAL'` в текущей сессии.

Windows-драйвер конкретного производителя может потребовать системной
установки. Он не является частью `.tools`.

## Установка и запуск приложения

```powershell
.\android\gradlew.bat -p android :app:assembleDebug --console=plain
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.daybricks.planner/.app.MainActivity
```

`-r` обновляет установленную debug-сборку с сохранением её данных. Если
изменился ключ debug-подписи, старую сборку придётся удалить, что также удалит
её локальные настройки:

```powershell
adb uninstall app.daybricks.planner
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

Календарные разрешения можно выдать через интерфейс приложения либо командой:

```powershell
adb shell pm grant app.daybricks.planner android.permission.READ_CALENDAR
adb shell pm grant app.daybricks.planner android.permission.WRITE_CALENDAR
```

## Отладка

Основной лог приложения:

```powershell
adb logcat -c
adb logcat
```

Фильтрация по запущенному процессу:

```powershell
$AppProcessId = (& adb shell pidof -s app.daybricks.planner).Trim()
adb logcat --pid=$AppProcessId
```

Полезные диагностические команды:

```powershell
adb shell dumpsys package app.daybricks.planner
adb shell dumpsys activity activities
adb shell dumpsys alarm
adb shell content query --uri content://com.android.calendar/calendars
```

Последняя команда читает Calendar Provider устройства и может показать
личные данные календарей. Не прикладывайте её необработанный вывод к публичным
issue или логам.

Для breakpoints, Compose Layout Inspector и профилировщиков удобнее открыть
каталог `android/` в Android Studio. В настройках проекта укажите:

- Gradle JDK: `.tools/jdk-21.0.12.1+1`;
- Android SDK: `.tools/android-sdk`.

Чтобы Android Studio увидела локальный AVD и остальные переменные, запускайте
IDE из уже активированной PowerShell-сессии либо задайте те же пути в IDE.
Сама Android Studio в текущую `.tools` не входит.

## Тестирование Android

JVM unit-тесты:

```powershell
.\android\gradlew.bat -p android :app:testDebugUnitTest `
    --dependency-verification=strict --console=plain
```

Один класс или один тест:

```powershell
.\android\gradlew.bat -p android :app:testDebugUnitTest `
    --tests 'app.daybricks.planner.SomeTest' --console=plain
```

Lint:

```powershell
.\android\gradlew.bat -p android :app:lintDebug `
    --dependency-verification=strict --console=plain
```

Сборка APK инструментальных тестов без запуска:

```powershell
.\android\gradlew.bat -p android :app:assembleDebugAndroidTest `
    --dependency-verification=strict --console=plain
```

Instrumentation/Compose UI-тесты на запущенном эмуляторе или телефоне:

```powershell
.\android\gradlew.bat -p android :app:connectedDebugAndroidTest `
    --dependency-verification=strict --console=plain
```

Один instrumentation-класс:

```powershell
.\android\gradlew.bat -p android :app:connectedDebugAndroidTest `
    '-Pandroid.testInstrumentationRunnerArguments.class=app.daybricks.planner.PlannerUiTest' `
    --console=plain
```

Отчёты появляются в:

```text
android/app/build/reports/tests/
android/app/build/reports/lint-results-debug.html
android/app/build/reports/androidTests/connected/
```

## Общая проверка проекта

Репозиторий содержит агрегирующий скрипт:

```powershell
.\scripts\check.ps1
```

Он выполняет:

- строгую проверку Gradle dependencies;
- JVM unit-тесты Android;
- Android lint;
- сборку debug APK и test APK;
- `gofmt -l`, `go vet` и `go test` для сервера.

При запущенном устройстве можно добавить instrumentation:

```powershell
.\scripts\check.ps1 -Connected
```

Этот полный прогон нужен перед релизом или после широких изменений. Во время
разработки запускайте только задачи, относящиеся к изменённому коду.

## Локальная разработка sync-сервера

После активации Go toolset:

```powershell
Set-Location server
gofmt -w .
go vet ./...
go test ./...
go test -race ./...
go build -trimpath -o daybricks-server.exe ./cmd/daybricks-server
```

Для запуска без сохранения токена в файле:

```powershell
$env:DAYBRICKS_TOKEN = [Convert]::ToHexString(
    [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
).ToLowerInvariant()
$env:DAYBRICKS_DATA_DIR = Join-Path $ToolsRoot 'server-data'
$env:DAYBRICKS_LISTEN = '127.0.0.1:8080'

go run ./cmd/daybricks-server
```

Debug-приложение в Android Emulator обращается к хосту по адресу
`http://10.0.2.2:8080`. На физическом телефоне нужен доступный телефону адрес
компьютера в локальной сети. Release-сборка принимает только HTTPS.

Возврат в корень проекта:

```powershell
Set-Location $ProjectRoot
```

## Release APK и App Bundle

Release-подпись не хранится в `.tools`. Для обновления уже опубликованного
приложения необходимо восстановить существующий каталог `.signing/` из
защищённой резервной копии. Создание нового ключа даст другую подпись, и такой
APK нельзя будет установить как обновление существующего приложения.

Ожидаемая структура:

```text
.signing/
  keystore.properties
  <release-keystore>.jks
```

`keystore.properties` содержит четыре поля и не должен попадать в Git:

```properties
storeFile=<release-keystore>.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Сборка подписанного APK:

```powershell
.\android\gradlew.bat -p android :app:assembleRelease `
    --dependency-verification=strict --console=plain
```

Сборка Android App Bundle:

```powershell
.\android\gradlew.bat -p android :app:bundleRelease `
    --dependency-verification=strict --console=plain
```

Результаты:

```text
android/app/build/outputs/apk/release/app-release.apk
android/app/build/outputs/bundle/release/app-release.aab
```

Проверка подписи APK:

```powershell
& "$env:ANDROID_HOME/build-tools/36.0.0/apksigner.bat" `
    verify --verbose --print-certs `
    android/app/build/outputs/apk/release/app-release.apk
```

Перед распространением release-сборки также выполните unit-тесты, lint и
нужные instrumentation-тесты. Каталог `artifacts/` предназначен для явно
отобранных финальных файлов, а не для всех промежуточных сборок.

## Очистка и восстановление

Очистить только результаты Android-сборки:

```powershell
.\android\gradlew.bat -p android clean
```

Остановить Gradle daemons перед переносом или удалением кэша:

```powershell
.\android\gradlew.bat -p android --stop
```

Можно безопасно пересоздать следующие каталоги: `gradle-home`, `go-cache`,
`go-mod-cache`, `go-tmp` и `kotlin-tmp`. Их удаление не затрагивает исходники,
но следующая сборка снова загрузит зависимости и займёт больше времени.

Удаление `android-user` создаст новый debug keystore. После этого ранее
установленную debug-сборку, подписанную старым ключом, придётся удалить с
устройства. Удаление `avd` уничтожит данные виртуального устройства.

Удаление `.tools` целиком не затрагивает исходники и release signing key, но
после него нужно повторить раздел «Восстановление `.tools` с нуля».

## Обновление toolset

- JDK: загрузить новый ZIP JDK 21, проверить официальный SHA-256, распаковать
  рядом и изменить `JAVA_HOME` в локальном activation script.
- Android SDK: устанавливать конкретные package IDs через `sdkmanager`; не
  выполнять бесконтрольное обновление перед релизом.
- Go: сверить требуемую версию в `server/go.mod`, загрузить официальный ZIP и
  обновить `GOROOT`.
- Gradle: не заменять содержимое `gradle-home` вручную. Версия меняется через
  Gradle Wrapper и `gradle-wrapper.properties` отдельным осознанным изменением.
- После обновления выполнить `scripts/check.ps1`, а при изменении SDK,
  эмулятора или Compose — нужные connected tests.

## Частые проблемы

**SDK location not found.** Активируйте `ANDROID_HOME` и пересоздайте
`android/local.properties` с текущим абсолютным путём.

**License for package ... not accepted.** Выполните
`sdkmanager.bat --sdk_root=$env:ANDROID_HOME --licenses`.

**JAVA_HOME is invalid.** Убедитесь, что существует
`$env:JAVA_HOME/bin/java.exe` и что путь указывает на корень распакованного JDK.

**Device unauthorized/offline.** Переподключите USB, подтвердите RSA-запрос,
затем выполните `adb kill-server` и `adb start-server`.

**Эмулятор не стартует.** Проверьте виртуализацию и hypervisor, убедитесь, что
`ANDROID_AVD_HOME` задан до запуска, и попробуйте `emulator -avd DayBricks
-no-snapshot-load`.

**AVD перестал находиться после перемещения проекта.** Пересоздайте его:
`.ini` содержит старый абсолютный путь.

**INSTALL_FAILED_UPDATE_INCOMPATIBLE.** Debug APK подписан другим debug key.
Удалите установленную debug-версию или восстановите прежний
`.tools/android-user/debug.keystore`.

**Release APK оказался unsigned.** Восстановите `.signing/keystore.properties`
и постоянный JKS. Не создавайте новый ключ для обновления существующей
установки.

**Первая сборка долго работает или требует сеть.** Wrapper загружает Gradle,
а Gradle и Go заполняют локальные кэши. После успешной первой сборки эти файлы
будут повторно использоваться из `.tools`.

## Официальные источники

- [Android command-line tools и SDK packages](https://developer.android.com/tools)
- [Загрузка Android command-line tools](https://developer.android.com/studio#command-tools)
- [`sdkmanager`](https://developer.android.com/tools/sdkmanager)
- [`avdmanager`](https://developer.android.com/tools/avdmanager)
- [Microsoft Build of OpenJDK](https://learn.microsoft.com/java/openjdk/download)
- [Go downloads](https://go.dev/dl/)
- [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
