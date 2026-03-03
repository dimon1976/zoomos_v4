# Redmine Integration Plan — Zoomos Check Results

## Цель

Добавить кнопку создания задачи Redmine прямо в строке проблемного сайта на странице
`/zoomos/check/results/{runId}`. Данные о созданных задачах хранятся в БД и обновляются
при каждом открытии страницы.

## Поведение

- **Задачи нет** → красная кнопка `bug` в строке issue
- **Кнопка нажата** → modal: существующие задачи из Redmine API + превью новой задачи
- **Задача создана** → сохраняется в `zoomos_redmine_issues`, кнопка заменяется на `#NNN`
- **Обновление страницы** → статусы задач из Redmine API актуализируются в БД

## Что создаётся

### Новые файлы
| Файл | Назначение |
|------|-----------|
| `db/migration/V33__add_redmine_issues.sql` | Таблица `zoomos_redmine_issues` |
| `config/RedmineConfig.java` | @ConfigurationProperties `redmine.*` |
| `dto/RedmineIssueDto.java` | DTO для передачи данных задачи |
| `model/entity/ZoomosRedmineIssue.java` | JPA entity |
| `repository/ZoomosRedmineIssueRepository.java` | JPA repo |
| `service/RedmineService.java` | HTTP-клиент Redmine API |
| `controller/ZoomosRedmineController.java` | REST-эндпоинты `/zoomos/redmine/*` |

### Изменяемые файлы
| Файл | Изменение |
|------|-----------|
| `application.properties` | Настройки `redmine.*` |
| `ZoomosAnalysisController.java` | `+redmineIssues` в model для checkResults |
| `check-results.html` | Кнопка/ссылка RM + Bootstrap modal + JS |

## API эндпоинты

```
GET  /zoomos/redmine/config              → {enabled, trackerName, statusName, ...}
GET  /zoomos/redmine/check?site=site.ru  → {existing: [...], preview: {...}}
POST /zoomos/redmine/create              → {success, issue: {id, status, url}}
```

## БД схема

```sql
CREATE TABLE zoomos_redmine_issues (
    id           BIGSERIAL PRIMARY KEY,
    site_name    VARCHAR(255) NOT NULL UNIQUE,
    issue_id     INTEGER NOT NULL,
    issue_status VARCHAR(100),
    issue_url    VARCHAR(500),
    created_at   TIMESTAMP DEFAULT NOW(),
    updated_at   TIMESTAMP DEFAULT NOW()
);
```

## Поля задачи Redmine

| Поле | Значение |
|------|---------|
| subject | Домен сайта (напр. `site1.ru`) |
| tracker_id | Из конфига |
| status_id | Из конфига |
| priority_id | Из конфига |
| assigned_to_id | Из конфига |
| CF: В чем ошибка | `issue.message` |
| CF: Способ выкачки | `issue.checkType` (API/ITEM) |
| description | Сайт, город, проблема + ссылки на историю и матчинг |

## Конфигурация (`application.properties`)

```properties
redmine.base-url=
redmine.api-key=
redmine.project-id=1
redmine.tracker-id=1
redmine.tracker-name=Ошибка
redmine.status-id=1
redmine.status-name=Новая
redmine.priority-id=2
redmine.priority-name=Нормальный
redmine.assigned-to-id=
redmine.cf-error-id=
redmine.cf-parsing-method-id=
```

При пустом `redmine.base-url` или `redmine.api-key` — интеграция отключена, кнопки не отображаются.
