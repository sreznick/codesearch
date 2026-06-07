# Демонстрационный сценарий

Этот файл нужен, чтобы быстро проверить проект после клонирования или перед показом преподавателю.

Сейчас основной сценарий такой: берём Java-код, индексируем его, потом ищем не просто текст, а конкретные сущности языка.

## Перед запуском

Нужен JDK 21.

Сначала лучше проверить тесты:

```bash
./gradlew test
```

Если тесты прошли, можно запускать CLI.

## Шаг 1. Индексация Java-кода

Для быстрой проверки можно использовать тестовые файлы из проекта:

```bash
./gradlew run --args="index java src/test/resources"
```

Ожидаемый смысл результата:

```text
Готово  Индексация завершена
Язык: java
Путь:  src/test/resources
```

После этого рядом с приложением появится локальная директория индекса. Её не нужно коммитить в git.

## Шаг 2. Поиск класса

```bash
./gradlew run --args="search java class TestClass"
```

Что проверяем:
- CLI читает уже созданный индекс
- ищет именно Java-класс
- показывает файл и строку

Пример результата:

```text
Найдено совпадений: 1
1. Class TestClass  src/test/resources/TestClass.java:6
```

## Шаг 3. Поиск поля по имени

```bash
./gradlew run --args="search java field testField"
```

Здесь важно, что результат содержит не только имя поля, но и его declared type:

```text
Найдено совпадений: 1
1. Field testField  [String]  src/test/resources/TestClass.java:7
```

Обычный `grep` нашёл бы строку с `testField`, но не понимал бы, что это именно поле Java-класса и что его тип `String`.

## Шаг 4. Поиск по типу

```bash
./gradlew run --args="search java field-type String"
```

Это уже более показательный сценарий. Мы ищем не имя поля, а поля, объявленные как `String`.

Пример результата:

```text
Найдено совпадений: 2
1. Field testField  [String]  src/test/resources/TestClass.java:7
2. Field testFieldDuplicate  [String]  src/test/resources/TestClass.java:12
```

Для локальных переменных работает похожий запрос:

```bash
./gradlew run --args="search java local-variable-type String"
```

Для методов можно искать по возвращаемому типу:

```bash
./gradlew run --args="search java method-return-type String"
```

Пример результата:

```text
Найдено совпадений: 1
1. Method getTestField  [String]  src/test/resources/TestClass.java:30
```

Это хороший пример отличия от обычного текстового поиска: запрос ищет не слово `String` в файле, а методы, у которых `String` является return type.

## Шаг 5. Ограничение выдачи

Если совпадений много, можно показать только первые результаты:

```bash
./gradlew run --args="search java field-type String --limit 1"
```

Счётчик всё равно показывает полное число совпадений, но в списке будет только один результат.

## Шаг 6. Фильтр по пути

Можно искать только в файлах, путь которых содержит нужную часть:

```bash
./gradlew run --args="search java class TestClass --path src/test/resources"
```

Если указать путь, который не подходит ни одному результату, CLI должен спокойно вернуть пустую выдачу:

```bash
./gradlew run --args="search java class TestClass --path missing/path"
```

Ожидаемый смысл:

```text
Найдено совпадений: 0
Совпадений нет.
```

## Если поиск запустить до индексации

Команда:

```bash
./gradlew run --args="search java class TestClass"
```

до создания индекса должна завершиться ошибкой и подсказать, что сначала нужно выполнить:

```text
index java <path>
```

Это нормально. Поиск работает по Lucene-индексу, поэтому сначала нужно один раз выполнить индексацию.

## Что показывать на защите

Минимальный набор команд:

```bash
./gradlew test
./gradlew run --args="index java src/test/resources"
./gradlew run --args="search java class TestClass"
./gradlew run --args="search java field testField"
./gradlew run --args="search java field-type String"
./gradlew run --args="search java method-return-type String"
./gradlew run --args="search java local-variable-type String --limit 1"
```

Этого достаточно, чтобы показать главную идею: проект ищет не просто текстовые совпадения, а сущности Java-кода с учётом структуры, ролей и типов.

## Про предупреждения JVM и Lucene

При запуске могут появляться предупреждения вроде `restricted method` или `Java vector incubator module is not readable`.

Это не ошибки CLI. Они приходят от JVM/Lucene и не мешают индексации или поиску.
