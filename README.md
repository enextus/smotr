# RandomFetcher / QRNG Demo

## О проекте (Русский)

`RandomFetcher`— это демонстрационное настольное Java‑приложение, показывающее

* как получить криптографически случайные байты из квантового генератора (QRNG),
* как выполнить быстрый статистический анализ последовательностей с помощью собственного класса **RandomnessTester**,
* как построить простый, но приятный UI на Swing.

### Основные возможности

* Загрузка до 999 случайных байтов одним запросом (`Get QRNG`).
* Подробный лог запросов и ошибок (`Show Logs`).
* **Analyse** — окно с результатами статистических тестов (KS, χ², Runs‑test, CRC‑32, автокорреляция).
* Клёненький контекстный popup в текстовом поле (Copy / Paste / Cut).

### Сборка и запуск

Требуется **Java 21+** и **Maven 3.9+**.

```bash
mvn clean package       # сборка JAR‑файла
java -jar target/RandomFetcher-<version>.jar
```

> В IntelliJ IDEA достаточно запустить класс `org.randomfetcher.RandomFetcherUI`.

### Структура кода

| Пакет / класс      | Роль                                       |
|--------------------|--------------------------------------------|
| `RandomFetcherUI`  | графический интерфейс Swing                |
| `RandomnessTester` | статистические тесты и инженерные проверки |
| `RandomFetcher`    | утилита получения байтов из QRNG (HTTP)    |
| `LogManager`       | окно логов и хранение событий              |

### Зависимости

* **Apache Commons Math 3** — статистические распределения + K‑S тест
* **Guava** — утилиты общей направленности

---

## About the project (English)

`RandomFetcher` is a small desktop Java application that demonstrates how to

* obtain cryptographically secure random bytes from a Quantum RNG service,
* perform quick randomness checks using the in‑house **RandomnessTester** class,
* build a clean Swing UI with minimal code.

### Key features

* Fetch up to 999 random bytes with a single click (`Get QRNG`).
* Scrollable log window for requests / errors (`Show Logs`).
* **Analyse** window – runs Kolmogorov‑Smirnov, Chi‑Square, Runs‑test, CRC‑32 and autocorrelation on the latest
  sequence.
* Handy text‑field popup (Copy / Paste / Cut).

### Build & Run

Requires **Java 21+** and **Maven 3.9+**.

```bash
mvn clean package       # build fat JAR
java -jar target/RandomFetcher-<version>.jar
```

> Inside IntelliJ IDEA simply run `org.randomfetcher.RandomFetcherUI`.

### Code overview

| Package / Class    | Purpose                                    |
|--------------------|--------------------------------------------|
| `RandomFetcherUI`  | Swing GUI                                  |
| `RandomnessTester` | Statistical & engineering randomness tests |
| `RandomFetcher`    | HTTP util for fetching bytes from QRNG     |
| `LogManager`       | Log window + event storage                 |

### Dependencies

* **Apache Commons Math 3** — statistical distributions & K‑S test
* **Guava** — general‑purpose helpers

---
### 🔐  Настройка API-ключа

1.  Скопируйте файл `.env.example` → `.env`.
2.  Пропишите свой `OPENAI_API_KEY=…` в `.env`.
3.  Перезапустите `mvn test` / IDE.

> Файл `.env` исключён из Git (`.gitignore`).


© 2025 RandomFetcher Team — MIT License
