# Project Overview: Zoomos v4

This is a **Java Spring Boot** web application built with **Maven**. Its primary purpose is to process and manage large data files (Excel, CSV) for different clients. The application provides a web-based user interface for all its features.

## Key Technologies

*   **Backend:**
    *   Java 17
    *   Spring Boot 3.2.12 (Web, Data JPA, WebSocket, Actuator)
    *   Lombok
*   **Frontend:**
    *   Thymeleaf (Server-side templating)
    *   Bootstrap 5
    *   Vanilla JavaScript
*   **Database:**
    *   PostgreSQL (Production)
    *   H2 (Testing)
    *   Flyway (for database schema migrations)
*   **File Processing:**
    *   Apache POI (for `.xls`, `.xlsx` files)
    *   OpenCSV (for `.csv` files)
*   **Build & Dependencies:**
    *   Maven
    *   Selenium (for browser automation/testing tasks)

## Core Functionality

*   **Client Management:** CRUD operations for clients.
*   **Asynchronous File Processing:** Handles long-running import and export tasks in the background using dedicated thread pools.
*   **Import/Export:**
    *   Upload Excel/CSV files for a specific client.
    *   Analyze file structure and map columns to database fields.
    *   Manage import/export templates.
    *   Export data into various formats.
*   **Real-time Progress:** Uses WebSockets to provide real-time feedback on the status of ongoing operations.
*   **System Maintenance:** Includes scheduled tasks for cleaning up old files and database records.
*   **Data Utilities:** Provides tools for data cleaning, barcode matching, and URL extraction.

## Project Structure

The project follows a standard Spring Boot application structure:

*   `src/main/java/com/java/`: Root package for all Java source code.
    *   `config/`: Spring configuration classes (Async, Web, WebSocket).
    *   `controller/`: Spring MVC controllers that handle HTTP requests.
    *   `service/`: Contains the core business logic.
    *   `repository/`: Spring Data JPA repositories for database access.
    *   `model/`: JPA entity classes representing the database schema.
    *   `dto/`: Data Transfer Objects used for communication between layers.
    *   `util/`: Utility classes.
*   `src/main/resources/`:
    *   `application.properties`: Main configuration file, with profiles for `dev`, `prod`, and `silent`.
    *   `db/migration/`: Flyway SQL scripts for database schema evolution.
    *   `static/`: Static web assets (CSS, JavaScript, images).
    *   `templates/`: Thymeleaf HTML templates for the user interface.
*   `pom.xml`: The Maven project file, defining dependencies, plugins, and build settings.

## Building and Running

### Prerequisites
*   JDK 17 or newer
*   Maven 3.x
*   A running PostgreSQL instance (credentials configured in `application.properties`).

### Commands

1.  **Build the application:**
    ```shell
    mvn clean install
    ```

2.  **Run the application:**
    ```shell
    mvn spring-boot:run
    ```
    The application will be available at `http://localhost:8081`.

3.  **Run tests:**
    ```shell
    mvn test
    ```

## Development Conventions

*   **Configuration:** Application behavior is heavily configured through `src/main/resources/application.properties`. This includes database connections, async thread pool sizes, and file system paths.
*   **Database:** Schema changes are managed exclusively through Flyway migrations. `ddl-auto` is set to `none`.
*   **Logging:** The project uses SLF4J for logging.
*   **Frontend:** The UI is server-rendered using Thymeleaf. Client-side interactions are handled with vanilla JavaScript, often for enhancing UX (e.g., showing confirmation modals).
*   **Code Style:** The code uses Lombok extensively (`@Data`, `@Slf4j`, `@RequiredArgsConstructor`) to reduce boilerplate. It follows standard Java and Spring Boot conventions.

## AI Assistant Guidelines

* Разработка ведется на ОС Windows
* Ты должен общаться на русском языке
* Не редактируй .env файл - лишь говори какие переменные нужно туда добавить
* Используй Context7 для доступа к документациям библиотек
* Для релизации любых фич с использованием интеграций с внешними арі библиотеками изучай документации с помощью context7 инструмента
* Если есть изменения на фронтенде, то в конце проверь что фронт работает, открыв его через рlaywrigh
* Это мой pet проект, не нужно стремится усложнять и использовать какие-то сложные паттерны проектирования.
* Если чего-то не знаешь, не придумывай, так и говори.
* Обязательно закрывать запущенный сервер после тестирования
* Проектируем код по принципам KISS, YAGNI, MVP, Fail Fast, итеративная разработка.