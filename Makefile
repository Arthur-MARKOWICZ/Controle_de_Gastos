.PHONY: test check backend-test web-test web-check mobile-check infra-up infra-down

test: backend-test web-test

check: backend-test web-check

backend-test:
	cd backend && ./gradlew test

web-test:
	cd web && pnpm test

web-check:
	cd web && pnpm lint
	cd web && pnpm test
	cd web && pnpm build

mobile-check:
	cd mobile && ./gradlew :shared:jvmTest :androidApp:assembleDebug --no-configuration-cache

infra-up:
	docker compose up -d postgres

infra-down:
	docker compose down
