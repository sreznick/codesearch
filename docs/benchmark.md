# Бенчмарк codesearch

Замеры сделаны командой `codesearch bench <path> --repeat 20` на трёх open-source проектах.

Окружение: AMD Ryzen 5 4600H (4 ядра в WSL2), OpenJDK 21.

| Проект | Язык | Коммит | Файлов | Сущностей | Индексация | Индекс |
|---|---|---|---:|---:|---:|---:|
| [google/gson](https://github.com/google/gson) | Java | `216c41b` | 264 | 47 133 | 7.4 с | 1.9 МБ |
| [gin-gonic/gin](https://github.com/gin-gonic/gin) | Go | `43fe48e` | 99 | 15 841 | 10.1 с | 0.5 МБ |
| [psf/requests](https://github.com/psf/requests) | Python | `611c616` | 37 | 9 292 | 5.9 с | 0.5 МБ |

Время индексации включает запуск JVM и «холодный» старт ANTLR: при первом разборе парсер строит
DFA-кэш грамматики. Повторный разбор тех же файлов Go в одном процессе быстрее в 4–15 раз, поэтому
постоянный индекс (`codesearch index`) окупается сразу. На стандартной библиотеке Python 3.12
(568 файлов, 129 тыс. сущностей) индексация занимает около 37 с.

## Задержка запросов по готовому индексу

Медиана и p95 по 20 повторам, JVM уже прогрета.

| Сценарий | gson (Java) | gin (Go) | requests (Python) |
|---|---|---|---|
| Тип по имени (точно) | `class Computer`: 11.5 мс | `struct yamlBinding`: 12.8 мс | `class Response`: 7.3 мс |
| Метод/функция по подстроке | `method test`, 1575 найдено: 29.6 мс | `function Test`, 442: 21.8 мс | `method test`, 291: 14.7 мс |
| Имя с опечаткой | `class Computr --fuzzy`: 9.5 мс | `struct yamlBindig --fuzzy`: 8.3 мс | `class Respone --fuzzy`: 8.8 мс |
| Места вызова | `calls get`, 401: 11.2 мс | `calls ServeHTTP`, 44: 4.6 мс | `calls json`, 20: 6.3 мс |
| Наследники/реализации | `subtypes-of TypeAdapterFactory`, 29: 8.1 мс | `subtypes-of Binding`, 15: 5.6 мс | `subtypes-of Style`, 1: 4.9 мс |
| Совместимые с типом | `variable-assignable-to Gson`, 446: 10.8 мс | `variable-assignable-to Accounts`, 5: 4.7 мс | `variable-assignable-to Response`, 15: 4.4 мс |

## Сравнение с grep

Один и тот же вопрос к коду: сколько ответов даёт `codesearch` и сколько строк выдаёт grep.

| Вопрос | codesearch | grep | Почему результаты расходятся |
|---|---:|---:|---|
| gson: где объявлен класс `Gson` | 1 | 1 (`class Gson`) | простой случай, совпадает |
| gson: реализации `TypeAdapterFactory` | 29 | 30 (`implements TypeAdapterFactory`) | 3 строки grep — примеры в Javadoc; codesearch находит и косвенные реализации |
| gson: наследники `TypeAdapter` | 54 | 50 (`extends TypeAdapter<`) | grep не видит транзитивное наследование и переносы строк |
| gson: вызовы `toJson` | 763 | 806 (`toJson(`) | grep считает объявления методов и упоминания в комментариях |
| gin: реализации интерфейса `Binding` | 15 | — | в Go реализация неявная, grep её не выражает (35 строк просто упоминают `Binding`) |
| gin: вызовы `ServeHTTP` | 44 | 49 (`ServeHTTP(`) | grep считает и объявления методов |
| requests: подклассы `RequestException` | 21 | 9 (`(RequestException)`) | grep видит только прямых наследников |
| requests: переменные типа `Response` | 15 | 133 (`Response`) | codesearch учитывает аннотации и вывод типа, grep выдаёт все упоминания |

Как воспроизвести:

```bash
codesearch bench path/to/project --repeat 20
codesearch index path/to/project
codesearch search java subtypes-of TypeAdapterFactory
grep -rn "implements TypeAdapterFactory" path/to/project --include=*.java
```
