# Quickstart: Sistema de Regras para Crédito Rotativo

## Pré-requisitos

- JDK 21
- Docker (para o PostgreSQL local e para os testes de integração via Testcontainers)

## Subir o ambiente

```bash
make db-up      # docker compose up -d postgres
make migrate    # aplica as migrations Flyway (cria cliente / decisao_avaliacao / resultado_regra)
make seed       # opcional: popula alguns clientes fictícios de exemplo
make run        # ./mvnw spring-boot:run
```

## Avaliar clientes via API

```bash
curl -X POST http://localhost:8081/api/v1/avaliacoes \
  -H "Content-Type: application/json" \
  -d '{
    "clientes": [
      {
        "cpf": "12345678901",
        "nome": "Maria Souza",
        "scoreCredito": 720,
        "valorFaturaAtual": 800.0,
        "limiteDisponivel": 3200.0,
        "numeroAtrasos": 0,
        "comportamentoGastos": [
          {"mes": "2026-06", "valor": 700.0},
          {"mes": "2026-07", "valor": 750.0},
          {"mes": "2026-08", "valor": 800.0}
        ]
      }
    ]
  }'
```

## Gerar o relatório em lote (FR-014)

```bash
make run-batch   # reaproveita o mesmo motor, lê fixtures/clientes-demo.json, grava relatório
```

## Rodar os testes

```bash
make test        # unit (por regra) + integração (Testcontainers sobe Postgres efêmero)
```

## Encerrar

```bash
make down         # docker compose down
```

## Onde olhar primeiro

- `domain/regras/` — cada regra de negócio isolada (Princípio I / User Story 2)
- `engine/MotorDeAvaliacao.kt` — orquestrador, único ponto que agrega os `ResultadoRegra`
- `api/AvaliacaoController.kt` — contrato HTTP (ver `contracts/openapi.yaml`)
- `/actuator/prometheus` — métricas de volume/latência/aprovação uma vez com `make run` ativo
