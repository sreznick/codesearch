# codesearch

`codesearch` - CLI для структурного поиска по исходному коду.

Он ищет не просто текст, а сущности языка: классы, методы, поля, функции, структуры, импорты и т.д.

## Возможности

- Java (до Java 21): классы, records, enum, интерфейсы, методы, поля, локальные переменные, аннотации.
- Go: package, import, function, method, struct, interface, field, var, const, локальные переменные.
- Python: import, class, function, method, поля классов (включая `self.x`), переменные, константы, декораторы.
- Поиск по объявленному типу (`field-type`, `method-return-type`, `function-return-type`, ...).
- Поиск переменных, совместимых с типом (`variable-assignable-to`): наследование в Java и Python,
  неявная реализация интерфейсов в Go, вывод типа из `var`, `:=` и присваиваний Python.
- Поиск наследников и реализаций (`subtypes-of`), включая транзитивное наследование и неявные интерфейсы Go.
- Поиск мест вызова функций, методов и конструкторов (`calls`) с указанием, откуда сделан вызов.
- Фильтр по контейнеру (`--in Order`): только сущности внутри указанного класса или функции.
- Один CLI для всех языков: без `--lang` индексация и поиск идут по всем найденным языкам.
- JSON output, stats, limit, path filter, fuzzy, сниппеты кода.

## Быстрый запуск

```bash
./gradlew installDist
export PATH="$PATH:$PWD/app/build/install/codesearch/bin"
codesearch --help
```

## Примеры

```bash
codesearch class TestClass app/src/test/resources
codesearch index app/src
codesearch --cached field-type String
codesearch --cached variable-assignable-to Appendable --explain
codesearch --lang go function intMin app/src/test/resources/go
codesearch --lang python decorator route app/src/test/resources/python
codesearch subtypes-of Shape app/src/test/resources --explain
codesearch calls make_product app/src/test/resources
codesearch method --in Order --path app/src/test/resources
codesearch bench app/src
```

## Архитектура

```
cli/        разбор аргументов, команды, вывод (text/json/snippet)
engine/     CodeSearchEngine — индексация/поиск по одному или всем языкам; LanguageRegistry
index/      обход исходников, Lucene-индекс: index/<language>/ + манифест
search/     LanguageSearchService — общий для всех языков поиск по индексу
core/       модель: CodeEntity, EntityKind, SearchRequest, SearchResponse
plugin/     LanguagePlugin — единственная точка расширения для нового языка
java/ go/ python/   языковые плагины: извлечение сущностей и типов из ANTLR-дерева
```

Чтобы добавить язык, нужно добавить ANTLR-грамматику, реализовать `LanguagePlugin`
(список сущностей, `EntityExtractor`, возможности поиска по типам) и зарегистрировать его
в `LanguageRegistry.withDefaults()`. Индексация, хранение, поиск и CLI менять не нужно.

## Подробнее

Подробные сценарии: [docs/demo.md](docs/demo.md)
