.PHONY: db-up migrate seed run run-batch test down help

.DEFAULT_GOAL := help

db-up: ## Sobe o Postgres via docker compose e aguarda ficar healthy (localhost:5433)
	docker compose up -d postgres
	@echo "Waiting for postgres to be healthy..."
	@until [ "$$(docker inspect -f '{{.State.Health.Status}}' revolving-credit-postgres 2>/dev/null)" = "healthy" ]; do sleep 1; done
	@echo "postgres is up"

migrate: ## Roda as migrations Flyway contra o Postgres local
	./mvnw -q flyway:migrate \
		-Dflyway.url=jdbc:postgresql://localhost:5433/revolving_credit \
		-Dflyway.user=revolving_credit \
		-Dflyway.password=revolving_credit \
		-Dflyway.locations=filesystem:src/main/resources/db/migration

seed: ## Popula o banco com os dados de fixtures/seed.sql
	docker exec -i revolving-credit-postgres psql -U revolving_credit -d revolving_credit < fixtures/seed.sql

run: ## Sobe a aplicacao Spring Boot (API REST, porta 8081)
	./mvnw spring-boot:run

run-batch: ## Sobe a aplicacao no profile batch (processa fixtures/clientes-demo.json)
	./mvnw spring-boot:run -Dspring-boot.run.profiles=batch

test: ## Roda a suite de testes (./mvnw test)
	./mvnw test

down: ## Derruba os containers do docker compose (Postgres)
	docker compose down

help: ## Lista os comandos disponiveis neste Makefile
	@echo "Comandos disponiveis:"
	@echo ""
	@echo "Fluxo local tipico: make db-up -> make migrate -> make seed -> make run"
	@echo ""
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z_-]+:.*##/ { printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
