# codesearch

`codesearch` - это CLI для поиска по Java-коду.

Он похож на `grep`, но ищет не только текстовые совпадения. Программа разбирает Java-файлы, достаёт из них сущности языка и кладёт их в Lucene-индекс. Поэтому можно искать, например, классы, методы, поля, локальные переменные, строки, литералы и объявления с конкретным типом.

## Что есть сейчас

- поиск по Java-файлам
- grep-подобный режим: `codesearch <query> [path]`
- поиск по виду сущности: `codesearch class TestClass .`
- постоянный индекс для быстрых повторных запросов
- поиск полей, локальных переменных и методов по declared type / return type
- поиск переменных по совместимому типу с простым выводом `var`
- фильтр результатов по пути
- ограничение количества результатов
- zip-дистрибутив, чтобы в дальнейшем скачивать пакет и использовать как grep-подобный инструмент

## Требования

Нужен JDK 21.

Проверить проект:

```bash
./gradlew test
```

## Быстрый запуск через Gradle

Можно запускать CLI прямо из проекта:

```bash
./gradlew run --args="TestClass app/src/test/resources"
./gradlew run --args="class TestClass app/src/test/resources"
./gradlew run --args="field testField app/src/test/resources"
```

Первый вариант ищет `TestClass` по всем найденным Java-сущностям. Второй ограничивает поиск классами. Третий ищет поля.

Если путь не указать, используется текущая директория:

```bash
./gradlew run --args="class TestClass"
```

## Как запускать как codesearch

Собрать локальную installed-версию:

```bash
./gradlew installDist
```

Запустить:

```bash
app/build/install/codesearch/bin/codesearch --help
app/build/install/codesearch/bin/codesearch class TestClass app/src/test/resources
```

Чтобы команда была доступна как обычная программа:

```bash
export PATH="$PATH:$PWD/app/build/install/codesearch/bin"
codesearch class TestClass app/src/test/resources
```

## Два режима работы

### 1. Быстрый одноразовый поиск

```bash
codesearch <query> [path]
codesearch <kind> <query> [path]
```

Примеры:

```bash
codesearch TestClass .
codesearch class TestClass .
codesearch field testField .
codesearch method getTestField .
```

В этом режиме программа сама создаёт временный индекс для указанного пути, выполняет поиск и удаляет временный индекс после завершения. Это удобно как `grep -r`, когда нужен один быстрый запрос.

### 2. Поиск по постоянному индексу

Сначала создать индекс:

```bash
codesearch index .
```

Потом выполнять быстрые повторные запросы:

```bash
codesearch --cached class TestClass
codesearch --cached field testField
codesearch --cached field-type String
codesearch --cached method-return-type String
codesearch --cached local-variable-type String
codesearch --cached variable-assignable-to Appendable
codesearch --cached variable-assignable-to Appendable --explain
```

`--cached` не переиндексирует проект. Он читает уже созданный локальный индекс из директории `index/`. Если код изменился, индекс нужно пересоздать командой `codesearch index .`.

## Поиск по совместимому типу

Обычный поиск по типам ищет точное объявление:

```bash
codesearch --cached local-variable-type String
```

Если нужен более семантический поиск, можно искать переменные, которые совместимы с заданным типом:

```bash
codesearch --cached variable-assignable-to Appendable
codesearch --cached variable-assignable-to CharSequence
```

Например, для кода:

```java
var text = "hello";
var builder = new StringBuilder("hello");
```

`text` найдётся как `CharSequence`, потому что для строкового литерала выводится тип `String`, а `String` реализует `CharSequence`.

`builder` найдётся как `Appendable`, потому что `StringBuilder` реализует `Appendable`.

Если добавить `--explain`, в выдаче будет видно, почему результат подошел:

```bash
codesearch --cached variable-assignable-to Appendable --explain
```

Пример объяснения:

```text
explain: var -> StringBuilder -> Appendable
explain: совместимые типы: StringBuilder, Appendable, CharSequence, Serializable, Object
```

## Полезные опции

```bash
codesearch class TestClass . --limit 5
codesearch --cached class TestClass --path app/src/test/resources
codesearch class testclass . -cs
```

- `--limit` или `-n` ограничивает количество строк в выдаче
- `--path` фильтрует результаты по части пути в режиме `--cached`
- `-cs` включает case-sensitive поиск
- `--kind` или `-k` задаёт вид сущности, если не хочется писать его первым аргументом
- `--explain` показывает, за счет какой цепочки типов результат подошел под запрос

Пример с `--kind`:

```bash
codesearch TestClass . --kind class
```

## Архив для скачивания

Собрать zip:

```bash
./gradlew distZip
```

Готовый архив:

```bash
app/build/distributions/codesearch.zip
```

После распаковки:

```bash
unzip codesearch.zip
./codesearch/bin/codesearch --help
./codesearch/bin/codesearch class TestClass /path/to/java/project
```

Такой архив можно прикрепить к GitHub Release, чтобы пользователь скачал его, распаковал и запускал `codesearch` из `bin/`.

## Как это работает внутри

1. CLI получает запрос и путь.
2. Индексатор обходит Java-файлы.
3. Служебные директории вроде `build`, `.git`, `.gradle`, `target`, `node_modules` пропускаются.
4. Java-код разбирается через ANTLR.
5. Найденные сущности превращаются в документы Lucene.
6. Поиск выполняется по индексу.
7. В выдаче показываются вид сущности, имя, тип при наличии, файл и строка.

## Ограничения

- сейчас поддерживается Java
- если используется `--cached`, индекс нужно пересоздавать после изменений в коде
- это не замена `grep` для любого текста, а поиск по сущностям Java-кода

## Демо

Пошаговые примеры лежат в [docs/demo.md](docs/demo.md).
