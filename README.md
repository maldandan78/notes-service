# Notes Service

REST API сервиса «Управление заметками» по тестовому заданию.

Java 17, Spring Boot 3.5.16, Maven. Хранение — потокобезопасный `ConcurrentHashMap`, без внешней базы данных. Данные теряются при перезапуске приложения.

## Сборка и запуск

Требуются JDK 17 и Maven 3.6.3+:

```sh
mvn clean verify
java -jar target/notes-service-1.0.0.jar
```

Либо для разработки:

```sh
mvn spring-boot:run
```

По умолчанию приложение слушает `http://localhost:8080`. Другой порт: `java -jar target/notes-service-1.0.0.jar --server.port=8081`.

## Docker

Dockerfile выполняет сборку и тесты в Maven, затем копирует JAR в требуемый заданием `eclipse-temurin:17-jre-alpine`. Приложение работает от непривилегированного пользователя.

```sh
docker build -t notes-service .
docker run --rm -p 8080:8080 notes-service
```

Если образ `eclipse-temurin:17-jre-alpine` недоступен для архитектуры ARM, используйте эмуляцию AMD64 (Docker Desktop):

```sh
docker build --platform linux/amd64 -t notes-service .
docker run --rm --platform linux/amd64 -p 8080:8080 notes-service
```

## API

| Метод | URL | Успех | Ошибки |
| --- | --- | --- | --- |
| POST | `/notes` | 201, JSON заметки, заголовок `Location` | 400 |
| GET | `/notes` | 200, массив заметок | — |
| GET | `/notes?tag=work` | 200, заметки с точным тегом | 400 для пустого тега |
| GET | `/notes/{id}` | 200, JSON заметки | 400 для неверного UUID, 404 |
| PUT | `/notes/{id}` | 200, JSON обновлённой заметки | 400, 404 |
| DELETE | `/notes/{id}` | 204, без тела | 400 для неверного UUID, 404 |

Создание и полная замена редактируемых полей (`PUT`) принимают:

```json
{
  "title": "План",
  "content": "Написать тесты",
  "tags": ["work", "java"]
}
```

Допущения для деталей, не заданных в Excel:

- `title` обязателен и не может быть пустым или состоять из пробелов.
- `content` обязателен; пустая строка допустима.
- `tags` можно не передавать или передать `null` — это пустой набор. Повторы исключаются. Пустые, пробельные и `null`-теги запрещены.
- `id` — UUID, `createdAt` — время создания в UTC (ISO 8601); оба поля назначает сервер. `createdAt` сохраняется при обновлении.
- Неизвестные поля JSON, в том числе попытка передать `id` или `createdAt`, возвращают 400.
- Фильтрация по тегу учитывает регистр и ищет точное совпадение. Список отсортирован по `createdAt`, затем `id`; порядок тегов не определён.
- Обновление отсутствующей заметки возвращает 404. Повторное удаление возвращает 404.
- Некорректный JSON, отсутствующее тело и нарушения указанных правил возвращают 400 с JSON `{"message":"..."}`.

## Примеры

```sh
curl -i -X POST http://localhost:8080/notes \
  -H 'Content-Type: application/json' \
  -d '{"title":"Plan","content":"Write tests","tags":["work"]}'

curl 'http://localhost:8080/notes?tag=work'
```

Подставьте `id` из ответа создания:

```sh
curl http://localhost:8080/notes/UUID

curl -i -X PUT http://localhost:8080/notes/UUID \
  -H 'Content-Type: application/json' \
  -d '{"title":"Done","content":"Tests passed","tags":["work"]}'

curl -i -X DELETE http://localhost:8080/notes/UUID
```

## Архитектура и проверка требований

| Требование задания | Реализация |
| --- | --- |
| Maven, `spring-boot-starter-web`, `spring-boot-starter-test` | `pom.xml`, Java 17 |
| CRUD: title, content, createdAt, tags | `NoteController`, `NoteService`, неизменяемый `Note` |
| GET /notes?tag=work | `NoteService.list` |
| In-memory Map или H2 | `InMemoryNoteRepository` на `ConcurrentHashMap` |
| HTTP-методы, 201, 404, 400 | `NoteController`, `ApiExceptionHandler`, `NotesApiTest` |
| SRP, DIP через интерфейс репозитория | Контроллер отвечает за HTTP, сервис — за правила, `NoteRepository` — абстракция хранения |
| Не менее 3 unit-тестов, JUnit 5 + Mockito | `NoteServiceTest`: проверки с mock-репозиторием и фиксированными часами |
| Multi-stage Dockerfile: Maven → eclipse-temurin:17-jre-alpine | `Dockerfile` |

`NotesApiTest` дополнительно проверяет реальную связку контроллер → сервис → in-memory репозиторий через Spring Boot + MockMvc. Проверяются CRUD, фильтрация, HTTP-коды, валидация и сохранение исходной заметки после ошибочного обновления.

## Результаты проверки — 6 октября 2026

- `mvn clean verify`: успешная сборка, 35 тестов, 0 ошибок, 0 пропусков (20 запусков unit-тестов сервиса, 15 запусков интеграционных тестов API).
- Собранный JAR запущен на Java 17. Проверен HTTP-сценарий: создание, чтение, списки с фильтром, обновление, удаление; ответы 201/200/204/400/404 и неизменность `createdAt`.
- `docker build --platform linux/amd64 -t notes-service:1.0.0 .`: успешная сборка, те же 35 тестов прошли внутри Maven-стадии.
- Контейнер на `eclipse-temurin:17-jre-alpine` запущен и проверен тем же HTTP-сценарием. Пользователь контейнера — `app`, не root.

После проверки приложение остановлено, Docker очищен. Для повторного запуска соберите образ и запустите контейнер:

```sh
docker build --platform linux/amd64 -t notes-service:1.0.0 .
docker run -d --rm --platform linux/amd64 --name notes-service-demo \
  -p 127.0.0.1:8080:8080 notes-service:1.0.0
```

Остановить приложение: `docker stop notes-service-demo`.
