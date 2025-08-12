# Zoomos v4

Приложение демонстрирует импорт и экспорт данных с использованием Spring Boot.

## Асинхронный экспорт

За выполнение экспорта отвечает `ExportAsyncConfig`, который создает пул потоков `exportTaskExecutor`.
Параметры пула задаются в `application.properties` через префикс `export.async.*`:

- `export.async.core-pool-size`
- `export.async.max-pool-size`
- `export.async.queue-capacity`
- `export.async.thread-name-prefix`

Сервис `AsyncExportService` использует этот пул через аннотацию `@Async("exportTaskExecutor")`.