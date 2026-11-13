# Implementation Plan: Sistema de Regras para Crédito Rotativo

**Branch**: `001-revolving-credit-engine` | **Date**: 2026-08-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-revolving-credit-engine/spec.md`

## Summary

Motor de regras extensível que avalia clientes fictícios de cartão de crédito e decide liberação
de crédito rotativo + valor máximo, expondo esse motor tanto via API REST (Spring Boot) quanto via
um runner de lote que processa uma lista de clientes e gera um relatório consolidado. Regras são
implementadas como componentes independentes (Strategy) descobertos via injeção de dependência do
Spring — adicionar uma regra é adicionar uma classe, sem tocar o orquestrador central. Persistência
em PostgreSQL real, inicializado via `Makefile` (Docker Compose + Flyway), para demonstrar
modelagem de dados de fato, não apenas em texto.

## Technical Context

**Language/Version**: Kotlin 2.0.0 / JVM 21 (já fixado no `pom.xml` existente)

**Primary Dependencies**: Spring Boot 4.1.0 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
`spring-boot-starter-actuator`, `spring-boot-starter-validation`), Flyway (migrations), driver
PostgreSQL, Resilience4j (circuit breaker/retry na escrita de persistência), Micrometer (métricas,
via Actuator), Testcontainers + JUnit5/kotlin-test (testes de integração com Postgres real)

**Storage**: PostgreSQL 16, rodando em container Docker, com schema versionado por Flyway
(`src/main/resources/db/migration`); inicialização e seed orquestrados por `Makefile`

**Testing**: JUnit5 + kotlin-test (unit, um teste por regra), `@SpringBootTest` + `MockMvc` +
Testcontainers (contrato da API e integração motor+repositório+Postgres real — pivot de
`@WebMvcTest` documentado em `research.md` §6)

**Target Platform**: Serviço backend JVM, empacotado como aplicação Spring Boot, executado em
container Linux (via Docker Compose local para o case; a doc descreve o caminho para
Kubernetes/ECS em produção)

**Project Type**: Web service único (API REST) com um segundo ponto de entrada em processo
(`CommandLineRunner`/endpoint de lote) reaproveitando o mesmo motor de domínio — não é um
monorepo com frontend separado

**Performance Goals**: p95 de decisão por cliente < 1s (FR-007/SC-003); design (não
necessariamente testado sob carga real) para sustentar picos de 10.000 avaliações/min (FR-008)

**Constraints**: sem chamadas de rede síncronas bloqueantes na avaliação de regras (regras operam
apenas sobre os dados já recebidos do cliente); CPF e valores financeiros nunca em texto claro em
log (FR-009); falha de persistência não pode impedir a resposta da decisão (FR-011)

**Scale/Scope**: MVP de avaliação: dezenas de clientes fictícios por lote de demonstração;
arquitetura documentada (não implementada em nível produtivo) para os 10k/min do case

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Como o plano atende |
|---|---|
| I. Extensible Rule Engine (Open/Closed) | Cada regra é uma classe Kotlin implementando uma interface `RegraCredito` (Strategy), anotada `@Component`. O orquestrador recebe `List<RegraCredito>` via injeção do Spring e itera sobre ela — nunca referencia uma regra concreta por nome. Nova regra = nova classe + teste, zero edição do orquestrador. **PASS**. |
| II. Test-First Correctness on Business Rules | Cada regra tem teste unitário cobrindo aprovação, reprovação e valor de fronteira. **PASS**. |
| III. Explainable Decisions | Cada `RegraCredito` retorna um `ResultadoRegra` com `aprovada: Boolean` + `motivo: String`; o orquestrador agrega todos os `ResultadoRegra` na `DecisaoAvaliacao` persistida e exposta na API/relatório. **PASS**. |
| IV. Pragmatic Scope with Documented Trade-offs | Simplificações explícitas (ver `research.md` e README a criar): sem autenticação real, sem HA de banco, sem teste de carga real dos 10k/min, ambiente único via Docker Compose. **PASS** — documentado abaixo e em research.md. |
| V. Design for Operability | Latência medida via Micrometer Timer por decisão; logs SLF4J com CPF/valores mascarados; Actuator expõe métricas de volume/aprovação-reprovação; Resilience4j envolve a escrita de persistência (retry + circuit breaker) para que uma falha temporária do Postgres não bloqueie a resposta da decisão (FR-011); throughput de 10k/min tratado como argumento de design (stateless + horizontal scaling), não testado sob carga real neste exercício. **PASS**, com a ressalva documentada de que carga real não será testada. |

Nenhuma violação sem justificativa — tabela de Complexity Tracking não se aplica (vazia).

**Re-check pós Fase 1 (design)**: `data-model.md`, `contracts/openapi.yaml` e `quickstart.md`
não introduzem nada que contradiga a tabela acima — `motivos` é normalizado em `resultado_regra`
para permitir auditoria por regra via SQL (Princípio IV, trade-off documentado em
`data-model.md`), a API expõe motivos de todas as regras (Princípio III), e o endpoint único de
avaliação não acopla o cliente HTTP a uma regra específica (Princípio I). **PASS mantido**.

## Project Structure

### Documentation (this feature)

```text
specs/001-revolving-credit-engine/
├── spec.md               # user stories, requisitos funcionais, critérios de aceite
├── plan.md               # este arquivo — stack, Constitution Check, estrutura
├── research.md           # decisões técnicas e alternativas consideradas
├── data-model.md          # entidades, regras de negócio, schema de persistência
├── quickstart.md          # como rodar
└── contracts/
    └── openapi.yaml       # contrato da API
```

### Source Code (repository root)

```text
src/main/kotlin/com/creditengine/revolving_credit_rule_engine/
├── RevolvingCreditRuleEngineApplication.kt
├── domain/
│   ├── Cliente.kt                  # dados de entrada do cliente + histórico de gastos
│   ├── RegraCredito.kt             # interface Strategy das regras
│   ├── ResultadoRegra.kt           # resultado individual de uma regra (aprovada + motivo)
│   ├── DecisaoAvaliacao.kt         # agregação: status, valor liberado, motivos
│   └── regras/                     # implementações concretas (uma classe por regra)
│       ├── RegraScoreMinimo.kt
│       ├── RegraAtrasosMaximos.kt
│       ├── RegraFaturaSobreLimite.kt
│       └── RegraTendenciaDeGastos.kt
├── engine/
│   └── MotorDeAvaliacao.kt         # orquestrador: injeta List<RegraCredito>, aplica todas, calcula limite
├── persistence/
│   ├── ClienteEntity.kt / ClienteRepository.kt
│   ├── DecisaoEntity.kt / DecisaoRepository.kt
│   ├── ResultadoRegraEntity.kt / ResultadoRegraRepository.kt   # motivos, 1 linha por regra
│   └── DecisaoPersistenceService.kt
├── api/
│   ├── AvaliacaoController.kt      # POST /api/v1/avaliacoes (1..N clientes)
│   └── dto/                        # request/response DTOs
├── batch/
│   └── RelatorioLoteRunner.kt      # gera relatório a partir de uma lista fictícia (FR-014)
└── observability/
    └── LogMasking.kt               # mascaramento de CPF/valores nos logs (FR-009)

src/test/kotlin/com/creditengine/revolving_credit_rule_engine/
├── domain/regras/                  # 1 arquivo de teste por regra (aprovação/reprovação/fronteira)
├── engine/MotorDeAvaliacaoTest.kt  # combinação determinística de regras (US2)
├── api/AvaliacaoControllerTest.kt  # contrato HTTP (MockMvc)
└── integration/                    # Testcontainers + Postgres real (persistência + resiliência)

Makefile                            # db-up, migrate, seed, run, test, down
docker-compose.yml                  # serviço postgres para desenvolvimento/case
src/main/resources/db/migration/    # scripts Flyway (V1__init.sql, ...)
```

**Structure Decision**: projeto único (não há frontend/mobile) — pacote único Spring Boot com
separação por camada de domínio (`domain`, `engine`, `persistence`, `api`, `batch`,
`observability`), que já reflete o pacote existente `com.creditengine.revolving_credit_rule_engine`
no `pom.xml`. Isso atende ao princípio I (regras isoladas em `domain/regras`) sem introduzir
módulos Maven adicionais — complexidade extra desnecessária para o escopo do case.

## Complexity Tracking

*Sem violações do Constitution Check — tabela não aplicável.*
