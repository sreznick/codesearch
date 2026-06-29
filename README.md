# codesearch

`codesearch` - CLI для структурного поиска по исходному коду.

Он ищет не просто текст, а сущности языка: классы, методы, поля, функции, структуры, импорты и т.д.

## Возможности

- Java: поиск классов, методов, полей, локальных переменных, аннотаций.
- Java: поиск по типам и совместимым типам.
- Go: поиск по package, import, function, method, struct, interface, field, var, const.
- Один CLI для Java и Go через `--lang`.
- JSON output, stats, limit, path filter.

## Быстрый запуск

```bash
./gradlew installDist
export PATH="$PATH:$PWD/app/build/install/codesearch/bin"
codesearch --help
```

## Примеры

```bash
codesearch class TestClass app/src/test/resources
codesearch --cached field-type String
codesearch --cached variable-assignable-to Appendable --explain
codesearch --lang go function intMin app/src/test/resources/go
```

## Подробнее

Подробные сценарии: [docs/demo.md](docs/demo.md)
