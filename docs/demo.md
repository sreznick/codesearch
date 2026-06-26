# Демо codesearch

Этот файл показывает основной сценарий: как запустить проект, как искать в стиле `grep`, как создать постоянный индекс и как собрать архив для скачивания.

В примерах используется тестовый Java-файл:

```bash
app/src/test/resources/TestClass.java
```

## 1. Проверить проект

```bash
./gradlew test
```

Если тесты прошли, CLI можно запускать.

## 2. Быстрый поиск без установки

Через Gradle:

```bash
./gradlew run --args="TestClass app/src/test/resources"
```

Ожидаемый смысл результата:

```text
Найдено совпадений: 1
1. Class TestClass  app/src/test/resources/TestClass.java:6
```

Ограничить поиск только классами:

```bash
./gradlew run --args="class TestClass app/src/test/resources"
```

Ограничить поиск только полями:

```bash
./gradlew run --args="field testField app/src/test/resources"
```

Пример результата:

```text
Найдено совпадений: 2
1. Field testField  [String]  app/src/test/resources/TestClass.java:7
2. Field testFieldDuplicate  [String]  app/src/test/resources/TestClass.java:12
```

Это одноразовый режим. Он создаёт временный индекс, ищет и удаляет временный индекс после завершения.

## 3. Собрать локальную команду codesearch

```bash
./gradlew installDist
```

Проверить:

```bash
app/build/install/codesearch/bin/codesearch --help
```

Запустить поиск:

```bash
app/build/install/codesearch/bin/codesearch class TestClass app/src/test/resources
```

Для удобства можно добавить `bin` в `PATH`:

```bash
export PATH="$PATH:$PWD/app/build/install/codesearch/bin"
codesearch class TestClass app/src/test/resources
```

## 4. Постоянный индекс

Если нужно выполнять много запросов по одному проекту, лучше один раз создать индекс:

```bash
codesearch index app/src/test/resources
```

После этого запросы с `--cached` будут читать готовый индекс:

```bash
codesearch --cached class TestClass
codesearch --cached field testField
codesearch --cached method getTestField
```

`--cached` быстрее для повторных запросов, потому что не обходит и не индексирует файлы заново.

Если исходники изменились, индекс нужно пересоздать:

```bash
codesearch index app/src/test/resources
```

## 5. Поиск по типам

Найти поля типа `String`:

```bash
codesearch --cached field-type String
```

Пример результата:

```text
Найдено совпадений: 2
1. Field testField  [String]  app/src/test/resources/TestClass.java:7
2. Field testFieldDuplicate  [String]  app/src/test/resources/TestClass.java:12
```

Найти методы, которые возвращают `String`:

```bash
codesearch --cached method-return-type String
```

Найти локальные переменные типа `String`:

```bash
codesearch --cached local-variable-type String
```

Найти переменные, которые можно использовать как `Appendable`:

```bash
codesearch --cached variable-assignable-to Appendable
```

В тестовом файле есть пример:

```java
var inferredBuilder = new StringBuilder("hello");
```

Здесь тип переменной выводится как `StringBuilder`, а `StringBuilder` реализует `Appendable`, поэтому переменная находится по запросу выше.

Похожий пример со строкой:

```java
var inferredText = "hello";
```

Она находится по запросу:

```bash
codesearch --cached variable-assignable-to CharSequence
```

Запрос ищет не просто слово `String` или `Appendable` в тексте, а конкретные Java-сущности с учетом простого вывода типов и совместимости с интерфейсами.

## 6. Лимит и фильтр по пути

Показать только один результат:

```bash
codesearch --cached field-type String --limit 1
```

Искать только в файлах, путь которых содержит нужную часть:

```bash
codesearch --cached class TestClass --path app/src/test/resources
```

Если фильтр не подходит ни одному результату:

```bash
codesearch --cached class TestClass --path missing/path
```

Ожидаемый результат:

```text
Найдено совпадений: 0
Совпадений нет.
```

## 7. Архив для скачивания

Собрать zip:

```bash
./gradlew distZip
```

Архив появится здесь:

```bash
app/build/distributions/codesearch.zip
```

Проверка архива:

```bash
unzip app/build/distributions/codesearch.zip
./codesearch/bin/codesearch --help
./codesearch/bin/codesearch class TestClass /path/to/java/project
```



`Codesearch` разбирает Java-код, создаёт индекс и позволяет искать классы, методы, поля и типы точнее, чем обычный текстовый поиск.
