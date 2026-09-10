.PHONY: test check hooks pre-commit backend-test backend-unit-test web-test web-check deploy-check mobile-check mobile-unit-test infra-up infra-down

test: backend-test web-test

check: backend-test web-check deploy-check

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
