# Research: Sistema de Regras para Crédito Rotativo

Nenhum item do Technical Context ficou como `NEEDS CLARIFICATION` — os três pontos genuinamente
em aberto (interface de entrega, autoria das regras, persistência) já foram resolvidos na sessão
de Clarifications do `spec.md`. Este documento registra as decisões técnicas derivadas delas.

## 1. Padrão de extensibilidade das regras

- **Decision**: Interface `RegraCredito` (Strategy) + descoberta automática via injeção de
  dependência do Spring (`List<RegraCredito>` no construtor do orquestrador). Cada regra é um
  `@Component` independente.
- **Rationale**: O Spring já resolve a "inclusão sem alterar a estrutura principal" de graça —
  registrar uma nova regra é anotá-la `@Component`; nenhum `if`/registro manual central existe
  para editar. Mais simples que Chain of Responsibility aqui, porque não há necessidade de uma
  regra decidir se a próxima é chamada (todas as regras sempre são avaliadas, para que o relatório
  tenha o motivo completo de cada uma — ver FR-006).
- **Alternatives considered**: Chain of Responsibility clássica (cada regra decide se repassa ao
  próximo elo) — rejeitada porque interromper a cadeia esconderia motivos de regras não
  avaliadas, conflitando com o Princípio III (Explainable Decisions), que exige o motivo de todas
  as regras aplicadas, não só a primeira que reprovou.

## 2. Banco de dados e inicialização

- **Decision**: PostgreSQL 16 em container Docker + Flyway para schema versionado, tudo
  orquestrado por um `Makefile` na raiz (`make db-up`, `make migrate`, `make seed`, `make run`,
  `make test`, `make down`).
- **Rationale**: O usuário pediu explicitamente banco real inicializado por Makefile — Postgres é
  o banco relacional mais representativo de um ambiente real do Itaú (vs. H2 em memória, que
  reforçaria a mesma simplificação que se queria evitar). Flyway dá versionamento de schema
  auditável, relevante para a habilidade avaliada "Modelagem, Armazenamento e Governança de
  Dados".
- **Alternatives considered**: H2 em modo arquivo — mais simples, zero Docker, mas não demonstra
  de fato governança de schema/migrations reais; MongoDB — rejeitado porque o domínio (clientes,
  decisões, regras aplicadas) é fortemente relacional (decisão pertence a um cliente, motivos
  pertencem a uma decisão), sem vantagem de um modelo documento aqui.

## 3. Resiliência a falha de persistência (FR-011)

- **Decision**: A decisão é sempre calculada em memória primeiro (motor de regras não depende do
  banco); a escrita no Postgres é envolvida por Resilience4j (retry curto + circuit breaker). Se a
  escrita falhar após as tentativas, a API ainda responde com a decisão calculada e loga o erro de
  persistência separadamente — a resposta ao cliente nunca é bloqueada por indisponibilidade do
  banco.
- **Rationale**: Atende literalmente ao NFR "resiliência e tolerância a falhas" do case
  (indisponibilidade do banco) sem exigir uma fila/outbox completa, que seria desproporcional ao
  escopo de 7 dias.
- **Alternatives considered**: Padrão outbox transacional com fila de reprocessamento — mais
  correto para produção real, mas fica documentado como evolução futura (ver limitações), não
  implementado, por custo/tempo desproporcional ao case.

## 4. Observabilidade

- **Decision**: Spring Boot Actuator + Micrometer para métricas (contador de avaliações, contador
  aprovado/reprovado, timer de latência por decisão), SLF4J com um utilitário de mascaramento
  (`LogMasking`) aplicado a CPF e valores financeiros antes de qualquer log.
- **Rationale**: Sai "de graça" do ecossistema Spring já usado (sem nova infraestrutura de
  observabilidade a montar); cobre FR-009/FR-010/SC-005 diretamente.
- **Alternatives considered**: Stack completo Prometheus+Grafana rodando localmente — documentado
  como evolução (Actuator já expõe endpoint `/actuator/prometheus` pronto para scrape), não
  subido neste exercício para não desviar o foco do motor de regras.

## 5. Throughput de 10.000 avaliações/min

- **Decision**: Argumento de design documentado (stateless service, sem estado compartilhado em
  memória entre requisições, permitindo múltiplas réplicas atrás de um load balancer; a decisão em
  si não faz I/O síncrono bloqueante além da escrita assíncrona/resiliente do item 3). Não
  implementado nem testado sob carga real neste exercício.
- **Rationale**: Consistente com o Princípio IV da constituição — não se espera implementação
  produtiva completa, simplificações devem ser explicadas.
- **Alternatives considered**: Rodar um teste de carga real (ex.: k6/Gatling) contra a API local —
  desejável, mas tratado como evolução futura pelo tempo/escopo do take-home; se houver tempo
  sobrando ao final, pode ser adicionado como extra.

## 6. `@WebMvcTest` não disponível como slice leve neste setup — pivot para `@SpringBootTest` + Testcontainers

- **Decision**: O contrato HTTP (`AvaliacaoControllerTest`) roda com `@SpringBootTest(webEnvironment
  = MOCK)` + `@AutoConfigureMockMvc` contra um Postgres real via Testcontainers, em vez do slice
  `@WebMvcTest` originalmente planejado.
- **Rationale**: Na versão do Spring Boot usada neste ambiente (4.1.0), o módulo de teste de
  slice web (`spring-boot-webmvc-test`) não executa component scan do pacote da aplicação —
  `@WebMvcTest(AvaliacaoController::class)` (e mesmo `@WebMvcTest` sem argumento) resultava em
  zero beans do próprio pacote sendo registrados (confirmado inspecionando
  `ApplicationContext.getBeanDefinitionNames()` em um teste de diagnóstico), enquanto
  `@SpringBootTest` localizava e escaneava normalmente `RevolvingCreditRuleEngineApplication`.
  Também foram necessários dois ajustes de dependência específicos desta versão: (1) Flyway só é
  autoconfigurado via `spring-boot-starter-flyway` (ter apenas `flyway-core` no classpath não
  basta); (2) não há mais um bean `com.fasterxml.jackson.databind.ObjectMapper` autoconfigurado
  por padrão — `DecisaoPersistenceService` passou a instanciar o seu próprio `ObjectMapper`
  internamente em vez de depender de injeção.
- **Alternatives considered**: Investigar/reportar o bug do slice e aguardar correção — descartado
  por estar fora do controle do time e do prazo do case; usar apenas testes unitários da camada
  web com mocks manuais do `MotorDeAvaliacao`/`DecisaoPersistenceService` sem contexto Spring —
  descartado porque perderíamos a cobertura de serialização JSON real e o roteamento do
  `DispatcherServlet`, que é justamente o que um teste de contrato precisa validar.
- **Trade-off aceito**: os testes de contrato ficam mais lentos (sobem um Postgres efêmero) do que
  um slice web puro seria — aceitável no escopo deste take-home; documentado aqui para a
  apresentação, já que é uma decisão motivada por uma particularidade do ambiente, não por
  preferência arquitetural.

## 7. Testes de integração com banco real

- **Decision**: Testcontainers sobe um Postgres efêmero para os testes de integração
  (`@SpringBootTest`), em vez de mockar o repositório.
- **Rationale**: Reforça o Princípio II (Test-First Correctness) testando contra o banco real
  declarado no FR-015, não contra um dublê que poderia mascarar problemas de schema/mapeamento.
- **Alternatives considered**: H2 em memória só para testes — rejeitado porque divergiria do
  motor real (Postgres), risco de "passa no teste, falha no Postgres real".

## 8. Timeout de conexão do HikariCP e transação atômica na persistência

- **Decision**: `spring.datasource.hikari.connection-timeout` explicitado em 1000ms (padrão do
  HikariCP é 30000ms); `DecisaoPersistenceService.persistir()` passou a ser `@Transactional`,
  cobrindo os três `save`s (cliente, decisão, `resultado_regra`) numa única transação.
- **Rationale**: Verificação manual — derrubar o container do Postgres com a API rodando e chamar
  o endpoint — revelou que a resposta demorava **30,18s** antes de retornar a decisão (correta,
  HTTP 200), porque o retry/circuit breaker do Resilience4j só reage *depois* que uma tentativa de
  conexão falha, e cada tentativa individual ficava bloqueada até o timeout padrão de 30s do
  HikariCP. Reduzir o timeout para 1s trouxe a mesma chamada para **1,14s**, mantendo a garantia de
  resposta correta mesmo com o banco fora do ar, agora dentro de uma janela compatível com FR-007.
  A falta de `@Transactional` foi descoberta na mesma revisão: sem ela, uma falha entre os `save`s
  poderia deixar uma `decisao_avaliacao` persistida sem nenhuma linha em `resultado_regra`.
- **Alternatives considered**: manter o timeout padrão do HikariCP — rejeitado, viola a SLA de
  latência de FR-007 exatamente no cenário em que a resiliência deveria se provar; aumentar o
  número de tentativas do `@Retry` sem reduzir o timeout — rejeitado, cada tentativa herdaria o
  mesmo bloqueio de 30s, piorando o problema em vez de resolvê-lo.
- **Verificação**: reproduzido manualmente (Postgres parado via `docker stop`, chamada real à API)
  antes e depois da mudança; suíte automatizada (`make test`) não cobre este cenário de falha —
  ver Limitações no README.
