# codesearch

`codesearch` - это CLI для структурного поиска по исходному коду.

Он похож на `grep`, но ищет не только текстовые совпадения. Программа разбирает исходники, достаёт из них сущности языка и кладёт их в Lucene-индекс. Поэтому можно искать, например, классы, функции, методы, поля, локальные переменные, строки, литералы и объявления с конкретным типом.

## Что есть сейчас

- поиск по Java-файлам
- поиск по Go-файлам
- grep-подобный режим: `codesearch <query> [path]`
- поиск по виду сущности: `codesearch class TestClass .`, `codesearch --lang go function intMin .`
- постоянный индекс для быстрых повторных запросов
- поиск полей, локальных переменных и методов по declared type / return type
- поиск переменных по совместимому типу с простым выводом `var`
- JSON-вывод для скриптов, CI и внешних инструментов
- вывод фрагмента кода вокруг найденной строки
- статистика постоянного индекса
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
codesearch --lang go function intMin app/src/test/resources/go
codesearch --lang go struct BitSet app/src/test/resources/go
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
codesearch --cached annotation DemoController
codesearch --cached variable-assignable-to Appendable
codesearch --cached variable-assignable-to Appendable --explain
codesearch --cached annotation DemoController --json
codesearch --cached method getTestField --snippet
codesearch stats
```

`--cached` не переиндексирует проект. Он читает уже созданный локальный индекс из директории `index/`. Если код изменился, индекс нужно пересоздать командой `codesearch index .`.

## Go MVP

Go-поддержка пока сделана как MVP: она использует тот же CLI и тот же Lucene-индекс, но покрывает только базовые структурные сущности.

Тестовый Go-файл лежит здесь:

```bash
app/src/test/resources/go/sample.go
```

Быстрый одноразовый поиск:

```bash
codesearch --lang go function intMin app/src/test/resources/go
codesearch --lang go struct BitSet app/src/test/resources/go
codesearch --lang go import fmt app/src/test/resources/go
```

Постоянный Go-индекс:

```bash
codesearch index --lang go app/src/test/resources/go
codesearch --cached --lang go function intMin
codesearch --cached --lang go method Add
codesearch --cached --lang go interface Printer
codesearch --cached --lang go var defaultName
codesearch stats --lang go
```

Сейчас для Go поддерживаются `package`, `import`, `function`, `method`, `struct`, `interface`, `field`, `var`, `const` и базовые литералы. Семантический поиск по совместимым типам пока остаётся Java-фичей.

## Статистика индекса

После создания постоянного индекса можно посмотреть, сколько файлов и сущностей попало в индекс:

```bash
codesearch stats
codesearch stats --lang go
codesearch stats --path app/src/test/resources
```

Пример вывода:

```text
Статистика индекса
Язык: java
Файлов: 1
Сущностей: 51

По видам сущностей
  Class:           5
  Interface:       1
  Method:          6
  Field:           7
  LocalVariable:   8
```

## Поиск аннотаций

Можно искать Java-аннотации по имени:

```bash
codesearch annotation DemoController
codesearch --cached annotation DemoController
```

Для кода:

```java
@DemoController
class AnnotationFixture {
}
```

вывод будет показывать не только саму аннотацию, но и место применения:

```text
Annotation DemoController on Class AnnotationFixture
```

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

Также учитываются простые интерфейсы из самого проекта:

```java
interface Printable {
    void print();
}

class Report implements Printable {
    public void print() {
    }
}

var report = new Report();
```

Запрос:

```bash
codesearch --cached variable-assignable-to Printable --explain
```

покажет цепочку:

```text
explain: var -> Report -> Printable
```

То же работает для простого наследования классов:

```java
class Animal {
}

class Dog extends Animal {
}

var dog = new Dog();
```

```bash
codesearch --cached variable-assignable-to Animal --explain
```

```text
explain: var -> Dog -> Animal
```

## Полезные опции

```bash
codesearch class TestClass . --limit 5
codesearch --cached class TestClass --path app/src/test/resources
codesearch class testclass . -cs
codesearch method getTestField . --context 2
```

- `--limit` или `-n` ограничивает количество строк в выдаче
- `--path` фильтрует результаты по части пути в режиме `--cached`
- `-cs` включает case-sensitive поиск
- `--kind` или `-k` задаёт вид сущности, если не хочется писать его первым аргументом
- `--explain` показывает, за счет какой цепочки типов результат подошел под запрос
- `--snippet` показывает фрагмент исходного кода вокруг найденной строки
- `-A`, `-B`, `-C` работают как в `grep`: строки после, до или вокруг результата

Пример с `--kind`:

```bash
codesearch TestClass . --kind class
```

## Фрагмент кода в выдаче

Если нужно сразу увидеть найденное место в контексте, можно включить snippet:

```bash
codesearch method getTestField app/src/test/resources --snippet
```

По умолчанию показываются две строки до и две строки после результата. Количество строк можно задать явно:

```bash
codesearch method getTestField app/src/test/resources -C 3
codesearch method getTestField app/src/test/resources -B 1 -A 4
```

Пример вывода:

```text
1. Method getTestField  [String]  app/src/test/resources/TestClass.java:32
      31 |
   >  32 |     public String getTestField() {
      33 |         return testField;
```

## JSON-вывод

Если результат нужно обработать скриптом, удобнее не парсить обычный консольный текст, а включить JSON:

```bash
codesearch --cached annotation DemoController --json
codesearch field testField app/src/test/resources --json
```

Формат специально простой: общее количество совпадений и массив найденных сущностей.

```json
{
  "totalHits": 1,
  "results": [
    {
      "kind": "Annotation",
      "name": "DemoController",
      "language": "java",
      "file": "app/src/test/resources/TestClass.java",
      "line": 49,
      "declaredType": null,
      "score": 1.0,
      "attributes": {
        "annotationTargetKind": "Class",
        "annotationTargetName": "AnnotationFixture"
      },
      "explanation": []
    }
  ]
}
```

`--json` можно совмещать с `--explain`. Тогда объяснение попадёт в поле `explanation`, и его можно читать уже не глазами, а из другого инструмента.

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
2. Индексатор обходит файлы выбранного языка.
3. Служебные директории вроде `build`, `.git`, `.gradle`, `target`, `node_modules` пропускаются.
4. Код разбирается через ANTLR.
5. Найденные сущности превращаются в документы Lucene.
6. Поиск выполняется по индексу.
7. В выдаче показываются вид сущности, имя, тип при наличии, файл и строка.

## Ограничения

- Java поддерживается как основной сценарий
- Go поддерживается для базовых объявлений
- если используется `--cached`, индекс нужно пересоздавать после изменений в коде
- это не замена `grep` для любого текста, а поиск по сущностям исходного кода

## Демо

Пошаговые примеры лежат в [docs/demo.md](docs/demo.md).
