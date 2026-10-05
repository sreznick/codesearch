.PHONY: help test install dist env codesearch

CODESEARCH_BIN := $(CURDIR)/app/build/install/codesearch/bin

help:
	@echo "Targets:"
	@echo "  make test       - run tests"
	@echo "  make install    - build local CLI distribution"
	@echo "  make env        - print PATH export command"
	@echo "  make codesearch - build CLI and print PATH export command"
	@echo "  make dist       - build zip distribution"

test:
	./gradlew test

install:
	./gradlew installDist
	@echo ""
	@echo "CLI built at: $(CODESEARCH_BIN)/codesearch"
	@echo "Add it to current shell with:"
	@echo '  export PATH="$$PATH:$(CODESEARCH_BIN)"'
	@echo "Or run:"
	@echo '  eval "$$(make env)"'

env:
	@echo 'export PATH="$$PATH:$(CODESEARCH_BIN)"'

codesearch: install

dist:
	./gradlew distZip
