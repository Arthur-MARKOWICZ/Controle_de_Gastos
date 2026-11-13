.PHONY: test check hooks pre-commit backend-test backend-unit-test web-test web-check mcp-test mcp-check deploy-check mobile-check mobile-unit-test desktop-test desktop-check desktop-skip infra-up infra-down

test: backend-test web-test mcp-test

# O desktop entra no check só quando há toolchain Rust (ADR-0029).
HAS_CARGO := $(shell command -v cargo 2>/dev/null)

check: backend-test web-check mcp-check deploy-check $(if $(HAS_CARGO),desktop-check,desktop-skip)

backend-test:
	cd backend && ./gradlew test

# Só o que roda sem Docker; os *IntegrationTest ficam de fora.
backend-unit-test:
	cd backend && ./gradlew unitTest

web-test:
	cd web && pnpm test

web-check:
	cd web && pnpm lint
	cd web && pnpm test
	cd web && pnpm build

mcp-test:
	cd mcp && pnpm test

mcp-check:
	cd mcp && pnpm test
	cd mcp && pnpm build

# `cargo test` roda só no crate de sessão, que não depende do WebKitGTK; o
# shell em src-tauri/ exige as bibliotecas de GUI e fica fora da verificação.
desktop-test:
	cd desktop && pnpm test
	cd desktop/session && cargo test

# O build de verificação fixa a API local; o .env do desenvolvedor não entra.
desktop-check: desktop-test
	cd desktop && API_ENV=local API_BASE_URL_LOCAL=http://localhost:8080 pnpm build

desktop-skip:
	@echo "desktop: cargo não está no PATH; desktop-check omitido do make check."

deploy-check:
	sh infra/test-production-config.sh

mobile-check:
	cd mobile && ./gradlew :shared:jvmTest :androidApp:assembleDebug --no-configuration-cache

mobile-unit-test:
	cd mobile && ./gradlew :shared:jvmTest --no-configuration-cache

# Instala os hooks de git do lefthook. Rode uma vez por clone.
hooks:
	pnpm install

# Roda o mesmo conjunto do hook de pre-commit, sem precisar commitar.
pre-commit:
	pnpm exec lefthook run pre-commit --all-files

infra-up:
	docker compose up -d postgres

infra-down:
	docker compose down
