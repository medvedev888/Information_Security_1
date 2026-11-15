# Secure Notes API

Защищённый REST API личных заметок (Java 21, Spring Boot 4.1.2, Spring Security, PostgreSQL 17). Лабораторная работа № 1.

## Запуск

Требуются Java 21, Maven и Docker Compose.

```bash
sh scripts/init-env.sh
sh scripts/start.sh
```

API и PostgreSQL запускаются в Docker. Локальные учётные данные находятся в `.env`, который исключён из Git. Пользователь создаётся при первом запуске. Изменение `DEMO_PASSWORD` в `.env` не меняет пароль существующего пользователя в БД.

## API

Базовый URL: `http://localhost:8080`. Тела запросов и ответов — JSON.

### 1. `POST /auth/login`

Аутентификация пользователя. Логин и пароль берутся из `DEMO_USERNAME` и `DEMO_PASSWORD` в локальном `.env`.

```bash
set -a
. ./.env
set +a
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$DEMO_USERNAME\",\"password\":\"$DEMO_PASSWORD\"}"
```

Ответ `200`:

```json
{"accessToken":"<JWT>","tokenType":"Bearer","expiresIn":900}
```

Неверный логин или пароль — `401 Unauthorized`.

### 2. `GET /api/data`

Последние 100 заметок текущего пользователя. Доступ только с валидным JWT.

```bash
curl http://localhost:8080/api/data \
  -H 'Authorization: Bearer <JWT>'
```

Пример ответа `200`:

```json
[{"id":1,"title":"Первая заметка","content":"Текст заметки","createdAt":"2026-11-15T10:00:00Z"}]
```

Без токена или с невалидным токеном — `401 Unauthorized`. Если заметок нет — `[]`.

### 3. `POST /api/data`

Создание собственной заметки. Заголовок — до 120 символов, текст — до 2000; пустые и пробельные значения запрещены. Неизвестные поля отклоняются.

```bash
curl -X POST http://localhost:8080/api/data \
  -H 'Authorization: Bearer <JWT>' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Первая заметка","content":"Текст заметки"}'
```

Ответ `201` содержит созданную заметку с полями `id`, `title`, `content`, `createdAt`. Некорректные данные — `400 Bad Request`. Владелец определяется сервером из JWT.

## Меры защиты

### SQL Injection

`JdbcTemplate` использует параметризованные запросы с placeholders `?` и Prepared Statements. Пользовательский ввод не конкатенируется с SQL.

### XSS

Поля `title` и `content` экранируются через `HtmlUtils.htmlEscape` перед возвратом клиенту. API возвращает JSON и устанавливает `nosniff` и CSP. Например, `<script>` возвращается как `&lt;script&gt;`. Клиент должен выводить эти данные как текст.

### Аутентификация (Broken Authentication)

- Пароли хранятся как bcrypt-хэши с cost 12 и случайной солью.
- После успешного входа выдаётся JWT с подписью HS256 на 15 минут.
- Bearer-фильтр Spring Security проверяет подпись, срок, issuer, audience и обязательные claims.
- Доступ к заметкам ограничен владельцем из проверенного JWT. Cookies и серверные сессии для аутентификации не используются.

Исключения SAST документированы в `spotbugs-exclude.xml`: информационный список endpoints и отключённый CSRF в stateless Bearer API. При добавлении cookie-аутентификации настройку CSRF нужно пересмотреть.

## CI/CD (SAST / SCA)

При push/PR в GitHub Actions запускаются:

- **Тесты:** 6 интеграционных тестов на PostgreSQL.
- **SAST:** SpotBugs + Find Security Bugs → `spotbugsSarif.json`, `spotbugsXml.xml`, `spotbugs.html`.
- **SCA:** OSV Scanner 2.7.0 проверяет прямые и транзитивные зависимости из Maven CycloneDX SBOM → `sca-report.json`, `sca-report.html`.

Отчёты сохраняются как artifacts `tests-and-sast` и `sca-report`. Найденные OSV уязвимости и ошибки сканера блокируют сборку. Автоматический деплой не настроен.

Локальные проверки:

```bash
sh scripts/test.sh
bash scripts/sca.sh
```

Дополнительный Maven-профиль `sca` содержит OWASP Dependency-Check. Для него нужен `NVD_API_KEY` в окружении и доступный сервис NVD; основной OSV pipeline не зависит от NVD.

### Результаты проверок из Actions

[Workflow и история запусков](https://github.com/medvedev888/Information_Security_1/actions/workflows/ci.yml).

[Успешный запуск тестов, SAST и SCA после обновления зависимостей](https://github.com/medvedev888/Information_Security_1/actions/runs/43705877307).

Скриншоты SAST/SCA дополняют HTML- и JSON-отчёты в artifacts соответствующего запуска.
