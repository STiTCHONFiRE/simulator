# ASIC Simulator

Симулятор ASIC-майнера для проверки сканирования и управления через Antminer CGI и Cgminer TCP API.

Сейчас поддерживаемое поведение сфокусировано на двух протоколах:

- Antminer HTTP CGI с Digest auth.
- Cgminer TCP API на настраиваемых портах.

Другие типы устройств и протоколы в симуляторе не моделируются.

## Быстрый старт

Локальный запуск:

```powershell
.\mvnw.cmd spring-boot:run
```

Тесты:

```powershell
.\mvnw.cmd test
```

Запуск через Docker Compose:

```powershell
docker compose up -d
```

По умолчанию HTTP доступен на порту `80`, Cgminer TCP - на портах `4028` и `4029`.

## Как задавать настройки

Базовые значения лежат в `src/main/resources/application.yaml`.

Переопределять параметры можно стандартными способами Spring Boot:

- через переменные окружения;
- через аргументы запуска, например `--sim.model="Antminer S19j Pro"`;
- через внешний `application.yaml`.

Пример для PowerShell:

```powershell
$env:SIM_MODEL = "Antminer S21"
$env:SIM_MODE_OPTIONS = "normal,sleep"
$env:SIM_DEFAULT_WORK_MODE = "normal"
.\mvnw.cmd spring-boot:run
```

Пример для Docker Compose:

```yaml
services:
  asic:
    image: ghcr.io/stitchonfire/simulator:latest
    environment:
      SIM_MODEL: "Antminer S19j Pro"
      SIM_SERIAL_PREFIX: "SIM01"
      SIM_IDENTITY_SEED: "asic-01"
      SIM_MODE_OPTIONS: "0:Normal,1:Sleep,3:High"
      SIM_DEFAULT_WORK_MODE: "normal"
      SIM_POOL_URL: "stratum+tcp://pool.example.com:3333"
      SIM_CGMINER_PORTS: "4028,4029"
    ports:
      - "80:80"
      - "4028:4028"
      - "4029:4029"
```

## Все параметры

| Параметр Spring | Переменная окружения | Значение по умолчанию | Описание |
| --- | --- | --- | --- |
| `spring.application.name` | `SPRING_APPLICATION_NAME` | `simulator` | Имя Spring Boot приложения. |
| `spring.threads.virtual.enabled` | `SPRING_THREADS_VIRTUAL_ENABLED` | `true` | Включает virtual threads для обработки запросов. |
| `server.port` | `SERVER_PORT` | `80` | HTTP-порт Antminer CGI и служебных endpoint'ов. |
| `sim.vendor` | `SIM_VENDOR` | `Bitmain` | Производитель, отдаётся в HTML, system info и телеметрии. |
| `sim.model` | `SIM_MODEL` | `Antminer S19j Pro` | Модель устройства. Влияет на ответы Antminer и Cgminer. |
| `sim.firmware` | `SIM_FIRMWARE` | `2025.11` | Версия firmware в system info, summary, stats и статусах. |
| `sim.systemFilesystemVersion` | `SIM_SYSTEM_FILESYSTEM_VERSION` | `2025-11-01` | Версия файловой системы Antminer. |
| `sim.subtype` | `SIM_SUBTYPE` | `AMLCtrl_BHB42XXX` | Hardware subtype из `/cgi-bin/miner_type.cgi`, нужен BMU firmware flow актуального агента. |
| `sim.serialPrefix` | `SIM_SERIAL_PREFIX` | `SIM` | Префикс серийного номера устройства. |
| `sim.identitySeed` | `SIM_IDENTITY_SEED` | `HOSTNAME`, затем `COMPUTERNAME`, затем `simulator` | Seed для стабильного MAC, device id и серийных данных. Для нескольких симуляторов должен быть разным. |
| `sim.defaultWorkMode` | `SIM_DEFAULT_WORK_MODE` | `normal` | Стартовый режим работы. Может быть raw value или имя режима из `sim.modeOptions`. |
| `sim.modeOptions` | `SIM_MODE_OPTIONS` | `0:Normal,1:Sleep,3:High` | Список режимов, которые видит агент и UI-парсер Antminer. Форматы описаны ниже. |
| `sim.modeDsl.rulesPath` | `SIM_MODE_DSL_RULES_PATH` | `classpath:mode-dsl` | Каталог или JSON-файл с mode DSL. Поддерживаются `classpath:`, обычный путь и `file:` URI. |
| `sim.modeDsl.ruleKey` | `SIM_MODE_DSL_RULE_KEY` | пусто | Ключ выбранного DSL-правила, например `bitmain.low-power3`. Если задан, DSL имеет приоритет над `sim.modeOptions`. |
| `sim.powerW` | `SIM_POWER_W` | `3050` | Базовая активная мощность в ваттах для normal mode. |
| `sim.hashrateThs` | `SIM_HASHRATE_THS` | `104` | Базовый активный хешрейт в TH/s для normal mode. |
| `sim.hashrateKsol` | `SIM_HASHRATE_KSOL` | `840` | Базовый активный хешрейт в KSol/s для Antminer Z-серии. Используется автоматически, если `sim.model` соответствует `Antminer Z<номер>`. |
| `sim.temperatureC` | `SIM_TEMPERATURE_C` | `67` | Базовая активная температура в градусах C для normal mode. |
| `sim.telemetryJitterPercent` | `SIM_TELEMETRY_JITTER_PERCENT` | `2.0` | Случайный сдвиг телеметрии на каждый ответ. Значение `2.0` даёт диапазон примерно `-2%..+2%`. |
| `sim.idleTemperatureC` | `SIM_IDLE_TEMPERATURE_C` | `30.0` | Центр температуры в idle-состоянии. |
| `sim.idleTemperatureDeltaC` | `SIM_IDLE_TEMPERATURE_DELTA_C` | `15.0` | Разброс idle-температуры вокруг `sim.idleTemperatureC`. При дефолтах температура держится около `15..45 C`. |
| `sim.rebootDowntime` | `SIM_REBOOT_DOWNTIME` | `30s` в `application.yaml`, `15s` в кодовом default | Длительность имитации reboot. Пока устройство rebooting, HTTP отвечает `503`, Cgminer TCP закрывает соединение без payload. |
| `sim.poolUrl` | `SIM_POOL_URL` | `stratum+tcp://pool.example.com:3333` | Начальный URL для всех трёх пулов. Если все URL пустые, устройство считается idle. |
| `sim.auth.username` | `SIM_AUTH_USERNAME` | `root` | Пользователь Digest auth для `/cgi-bin/**` и остальных защищённых endpoint'ов. |
| `sim.auth.password` | `SIM_AUTH_PASSWORD` | `root` | Пароль Digest auth. |
| `sim.cgminer.portsCsv` | `SIM_CGMINER_PORTS`, `SIM_CGMINER_PORTS_CSV` | `4028,4029` | CSV-список TCP-портов Cgminer API. |
| `sim.cgminer.socketReadTimeoutMs` | `SIM_CGMINER_SOCKET_READ_TIMEOUT_MS` | `1000` | Таймаут чтения Cgminer TCP-запроса в миллисекундах. |

## Режимы работы

Режимы настраиваются через совместимый с monitoring-backend DSL или, для обратной совместимости, через `sim.modeOptions`.

### DSL-конфиги monitoring-backend

В образ встроены актуальные Antminer-правила `bitmain.*` из `RRMonitoring.Equipment.Api/mode-dsl`. Для запуска конкретного профиля достаточно указать его ключ:

```yaml
environment:
  SIM_MODE_DSL_RULE_KEY: "bitmain.low-power3"
  SIM_DEFAULT_WORK_MODE: "Low"
```

Поддерживаются правила схемы monitoring-backend с полями `key`, `priority`, `when.allOptionsExact` и `then.supportedModes`. Пары `name/value` отдаются без нормализации одновременно в `/js/miner.js` и `/cgi-bin/get_multi_option.cgi`, поэтому server-side exact matcher получает ровно тот набор, который описан в DSL.

Чтобы использовать конфиги прямо из внешнего каталога без копирования в образ:

```yaml
services:
  asic:
    volumes:
      - ./mode-dsl:/config/mode-dsl:ro
    environment:
      SIM_MODE_DSL_RULES_PATH: "/config/mode-dsl"
      SIM_MODE_DSL_RULE_KEY: "bitmain.hem2-dry5"
```

Если `SIM_MODE_DSL_RULE_KEY` пуст, используется прежний `sim.modeOptions`.

### Ручная карта режимов

Дефолтная карта режимов:

```yaml
sim:
  modeOptions: "0:Normal,1:Sleep,3:High"
```

Короткий формат:

```yaml
sim:
  modeOptions: "normal,sleep,high"
```

В этом формате известные имена нормализуются в Antminer raw values:

- `normal` -> `0`
- `sleep` -> `1`
- `high` -> `3`

Можно оставить только часть режимов:

```yaml
sim:
  modeOptions: "sleep,normal"
  defaultWorkMode: "normal"
```

Формат `raw:name` или `raw=name`:

```yaml
sim:
  modeOptions: "0:Normal,1:Sleep,3:High"
```

```yaml
sim:
  modeOptions: "eco:Eco,standard:Standard,turbo:Turbo"
  defaultWorkMode: "standard"
```

Поведение нормализации:

- `defaultWorkMode` может быть raw value (`0`) или именем (`normal`, `Normal`, `standard`).
- Если `defaultWorkMode` не найден среди настроенных режимов, выбирается normal-режим, если он есть, иначе первый режим из списка.
- При переключении режима через Antminer config принимаются поля `_ant_work_mode`, `bitmain-work-mode`, `WorkModeValue`, `work_mode`, `work-mode`, `miner-mode`, `mode`.
- Значения `sleep`, `standby`, `1`, `254` считаются sleep-like.
- Значения `low`, `lowPower`, `eco`, `lpm` считаются low-power-like: майнинг продолжается со сниженным хешрейтом и энергопотреблением.
- Значения `normal`, `standard`, `balance`, `balanced`, `0` считаются normal-like.
- Значения `high`, `turbo`, `performance`, `boost`, `hem`, `2`, `3` считаются high-like.

Режимы отдаются сразу в двух местах, потому что агент проверяет capabilities разными способами:

- `/miner.html` подключает `/js/miner.js`, где есть `modeList` для stock UI parser.
- `/cgi-bin/get_multi_option.cgi` отдаёт карту режимов для fallback parser.

В дефолтном `/js/miner.js` mode list отдаётся в stock UI формате:

```javascript
modeList: [{id:0},{id:1},{id:3}]
this.modeList[0].text = $.i18n.prop("modeNormal");
this.modeList[1].text = $.i18n.prop("modeSleep");
this.modeList[2].text = $.i18n.prop("modeHEM");
```

То есть для rules вида `allOptionsExact` агент видит `modeNormal -> 0`, `modeSleep -> 1`, `modeHEM -> 3`.

Это важно для `DeviceModeCapabilitiesObservedEvent`: агент сначала пытается разобрать Antminer stock UI JavaScript, затем fallback `/cgi-bin/get_multi_option.cgi`. Симулятор отдаёт режимы в обоих форматах. Если raw values числовые, stock UI parser может получить capabilities из `/js/miner.js`; если raw values строковые, fallback CGI всё равно возвращает полный список режимов.

## Телеметрия

Для моделей вида `Antminer Z<номер>` симулятор автоматически включает профиль Equihash:

- `rate_5s`, `rate_avg` и `rate_30m` отдаются в `KSol/s`;
- Cgminer stats содержит маркеры `Type: Antminer Z...` и `ID: ZCASH0`;
- базовый хешрейт берётся из `sim.hashrateKsol`, а `sim.hashrateThs` для Z-серии не используется;
- DSL-режимы работают так же, как для SHA-256 Antminer: sleep обнуляет KSol, low/high изменяют базовое значение.

Готовый пример находится в `docker-compose.antminer-z-series.yaml`:

```powershell
docker compose -f docker-compose.antminer-z-series.yaml up -d
```

В примере запускается `Antminer Z15 Pro` на `127.0.0.20` с номиналом `840 KSol/s` и встроенным DSL-профилем `bitmain.normal-sleep-only`.

В активном режиме и при наличии хотя бы одного пула симулятор каждый раз немного меняет значения:

- хешрейт;
- температуру;
- мощность;
- производные температуры плат, чипов и fan/power fields.

Размер случайного сдвига задаётся `sim.telemetryJitterPercent`.

Для high-like режима базовые значения увеличиваются:

- хешрейт примерно `sim.hashrateThs * 1.12` для SHA-256 или `sim.hashrateKsol * 1.12` для Z-серии;
- мощность примерно `sim.powerW * 1.12`;
- температура примерно `sim.temperatureC + 5`.

Для sleep-like режима:

- хешрейт всегда `0`;
- мощность и fan speed снижаются до idle-значений;
- температуры фиксируются в диапазоне `sim.idleTemperatureC +/- sim.idleTemperatureDeltaC`.

Такое же idle-поведение включается, если у устройства нет ни одного пула.

## Пулы

На старте симулятор создаёт три пула:

- pool 1: `sim.poolUrl`, user `worker1`, password `x`;
- pool 2: `sim.poolUrl`, user `worker2`, password `x`;
- pool 3: `sim.poolUrl`, user `worker3`, password `x`.

Пулы можно изменить через Antminer endpoint:

```http
POST /cgi-bin/set_miner_conf.cgi
Content-Type: application/x-www-form-urlencoded
Authorization: Digest ...

_ant_pool1url=stratum+tcp://pool-a.example.com:3333
&_ant_pool1user=worker-a
&_ant_pool1pw=x
&_ant_pool2url=
&_ant_pool2user=
&_ant_pool2pw=
&_ant_pool3url=
&_ant_pool3user=
&_ant_pool3pw=
```

Если все `_ant_poolNurl` пустые, хешрейт становится `0`, а температуры переходят в idle-диапазон. То же самое можно получить с запуска:

```yaml
environment:
  SIM_POOL_URL: ""
```

## Прошивка

Симулятор поддерживает Antminer legacy CGI flow, который использует агент:

1. Агент определяет протокол через `/cgi-bin/get_system_info.cgi`.
2. Загружает файл на `/cgi-bin/upgrade.cgi` multipart-полем `datafile`.
3. Вызывает `/cgi-bin/reboot.cgi`.
4. Ждёт, пока `/cgi-bin/get_system_info.cgi` снова начнёт отвечать после reboot.

Файл прошивки физически не устанавливается и не сохраняется на диск. Симулятор принимает upload, запоминает имя файла как pending firmware и применяет его после следующего reboot.

Пример upload:

```powershell
curl.exe --digest -u root:root `
  -F "datafile=@Antminer-S19j-Pro-2026.05.tar.gz;type=application/x-gzip" `
  http://127.0.0.1/cgi-bin/upgrade.cgi
```

Успешный ответ совместим с парсером агента:

```json
{
  "stats": "success",
  "msg": "Firmware upload accepted. Rebooting System",
  "success": true,
  "status": "uploaded",
  "file": "Antminer-S19j-Pro-2026.05.tar.gz",
  "targetFirmware": "Antminer-S19j-Pro-2026.05"
}
```

После upload текущие `sim.firmware` и `sim.systemFilesystemVersion` ещё не меняются. После `/cgi-bin/reboot.cgi` и окончания `sim.rebootDowntime` оба значения становятся равны inferred версии из имени файла. Для `Antminer-S19j-Pro-2026.05.tar.gz` это будет `Antminer-S19j-Pro-2026.05`.

Текущее состояние симуляции можно посмотреть через `/cgi-bin/upgrade_status.cgi`.

## Antminer HTTP API

Защищённые endpoint'ы требуют Digest auth с `sim.auth.username` и `sim.auth.password`.

| Endpoint | Метод | Назначение |
| --- | --- | --- |
| `/cgi-bin/get_miner_conf.cgi` | `GET` | Текущие пулы и текущий work mode в Antminer-совместимых полях. |
| `/cgi-bin/set_miner_conf.cgi` | `POST` | Настройка пулов и переключение режима. Принимает и stock form-urlencoded, и современный JSON с `pools[]`. |
| `/cgi-bin/get_system_info.cgi` | `GET` | Модель, firmware, filesystem version, MAC и идентификаторы устройства. |
| `/cgi-bin/miner_type.cgi` | `GET` | `miner_type`, `subtype`, `fw_version` для BMU firmware preprocessing. |
| `/cgi-bin/get_multi_option.cgi` | `GET` | Карта доступных режимов для discovery capabilities. |
| `/cgi-bin/get_miner_status.cgi` | `GET` | Antminer status с хешрейтом, температурами, вентиляторами, power и mode. |
| `/cgi-bin/minerStatus.cgi` | `GET` | Alias для miner status. |
| `/cgi-bin/summary.cgi` | `GET` | Antminer summary. |
| `/cgi-bin/stats.cgi` | `GET` | Antminer stats. |
| `/cgi-bin/chart.cgi` | `GET` | Текущая точка chart-telemetry. |
| `/cgi-bin/get_blink_status.cgi` | `GET` | Текущее состояние locate/fault LED. |
| `/cgi-bin/blink.cgi` | `POST` | Управление LED: современный JSON `{\"blink\":true}` и legacy form action. |
| `/cgi-bin/reboot.cgi` | `GET` | Имитация reboot на `sim.rebootDowntime`. |
| `/cgi-bin/upgrade.cgi` | `POST` | Multipart upload прошивки. Поле файла должно называться `datafile`. |
| `/cgi-bin/upgrade_status.cgi` | `GET` | Текущее состояние симуляции прошивки: `ready`, `uploaded`, `installing`, `installed`. |
| `/miner.html` | `GET` | Минимальная stock UI страница для discovery. |
| `/js/miner.js` | `GET` | JavaScript с `modeList` для Antminer stock UI parser. |
| `/test`, `/test.html` | `GET` | Простая тестовая HTML-страница без авторизации. |

## Cgminer TCP API

Cgminer сервер слушает порты из `sim.cgminer.portsCsv`.

Поддерживаемые команды определяются по тексту запроса:

- `pools` -> список пулов;
- `stats` -> stats payload;
- `devdetails` или `devs` -> dev details payload;
- всё остальное -> summary payload.

Пример проверки из PowerShell:

```powershell
$client = [System.Net.Sockets.TcpClient]::new("127.0.0.1", 4028)
$stream = $client.GetStream()
$bytes = [Text.Encoding]::ASCII.GetBytes('{"command":"summary"}')
$stream.Write($bytes, 0, $bytes.Length)
$buffer = New-Object byte[] 8192
$read = $stream.Read($buffer, 0, $buffer.Length)
[Text.Encoding]::UTF8.GetString($buffer, 0, $read)
$client.Close()
```

## Примеры конфигураций

Только sleep и normal:

```yaml
environment:
  SIM_MODE_OPTIONS: "normal,sleep"
  SIM_DEFAULT_WORK_MODE: "normal"
```

Строковые raw values:

```yaml
environment:
  SIM_MODE_OPTIONS: "eco:Eco,standard:Standard,turbo:Turbo"
  SIM_DEFAULT_WORK_MODE: "standard"
```

Устройство сразу в sleep:

```yaml
environment:
  SIM_DEFAULT_WORK_MODE: "sleep"
```

Устройство без пулов, с нулевым хешрейтом и idle-температурами:

```yaml
environment:
  SIM_POOL_URL: ""
  SIM_IDLE_TEMPERATURE_C: "30"
  SIM_IDLE_TEMPERATURE_DELTA_C: "15"
```

Несколько симуляторов на одном хосте:

```yaml
services:
  asic1:
    image: ghcr.io/stitchonfire/simulator:latest
    environment:
      SIM_SERIAL_PREFIX: "SIM01"
      SIM_IDENTITY_SEED: "asic-01"
    ports:
      - "127.0.0.2:80:80"
      - "127.0.0.2:4028:4028"
      - "127.0.0.2:4029:4029"

  asic2:
    image: ghcr.io/stitchonfire/simulator:latest
    environment:
      SIM_SERIAL_PREFIX: "SIM02"
      SIM_IDENTITY_SEED: "asic-02"
    ports:
      - "127.0.0.3:80:80"
      - "127.0.0.3:4028:4028"
      - "127.0.0.3:4029:4029"
```

## Поведение reboot

После запроса `/cgi-bin/reboot.cgi` устройство входит в rebooting state на `sim.rebootDowntime`.

Во время reboot:

- HTTP endpoint'ы, кроме `/test` и `/test.html`, отвечают `503` и JSON `{"success":false,"error":"rebooting"}`;
- Cgminer TCP соединение принимается, но payload не отдаётся.

После истечения времени симулятор автоматически возвращается к текущим пулам и текущему режиму.

Если перед reboot был успешный upload прошивки, pending firmware применяется в момент возврата устройства из rebooting state.
