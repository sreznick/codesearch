# Демо codesearch

Короткий сценарий для ручной проверки CLI.

## 1. Сборка

```bash
./gradlew installDist
export PATH="$PATH:$PWD/app/build/install/codesearch/bin"
codesearch --help
```

## 2. Java: быстрый поиск

```bash
codesearch class TestClass app/src/test/resources
codesearch field testField app/src/test/resources
codesearch method getTestField app/src/test/resources
```

## 3. Java: постоянный индекс

```bash
codesearch index app/src/test/resources
codesearch --cached class TestClass
codesearch --cached field testField
codesearch stats
```

## 4. Java: типы

```bash
codesearch --cached field-type String
codesearch --cached local-variable-type String
codesearch --cached method-return-type String
```

## 5. Java: совместимые типы

```bash
codesearch --cached variable-assignable-to Appendable --explain
codesearch --cached variable-assignable-to Animal --explain
```

## 6. Java: аннотации

```bash
codesearch --cached annotation DemoController
codesearch --cached annotation DemoController --json
```

## 7. Snippet и JSON

```bash
codesearch method getTestField app/src/test/resources --snippet
codesearch --cached annotation DemoController --json
```

## 8. Go: быстрый поиск

```bash
codesearch --lang go function intMin app/src/test/resources/go
codesearch --lang go struct BitSet app/src/test/resources/go
codesearch --lang go import fmt app/src/test/resources/go
```

## 9. Go: постоянный индекс

```bash
codesearch index --lang go app/src/test/resources/go
codesearch --cached --lang go function intMin
codesearch --cached --lang go method Add
codesearch --cached --lang go interface Printer
codesearch --cached --lang go var defaultName
codesearch stats --lang go
```

## 10. Python

```bash
codesearch --lang python class Product app/src/test/resources/python
codesearch decorator route app/src/test/resources/python
codesearch variable-assignable-to Entity app/src/test/resources/python --explain
```

## 11. Все языки сразу

```bash
codesearch index app/src/test/resources
codesearch stats
codesearch --cached method t
```

## 12. Наследники, вызовы, контейнеры

```bash
codesearch subtypes-of Shape app/src/test/resources --explain
codesearch subtypes-of Animal app/src/test/resources
codesearch calls make_product app/src/test/resources
codesearch --lang java calls StringBuilder app/src/test/resources
codesearch calls --in list_products --path app/src/test/resources
```

## 13. Бенчмарк

```bash
codesearch bench app/src --repeat 20
```

Результаты на реальных проектах: [benchmark.md](benchmark.md).

## 14. Дистрибутив

```bash
./gradlew distZip
ls app/build/distributions/codesearch.zip
```
