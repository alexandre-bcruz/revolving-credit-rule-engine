# Feature Specification: Sistema de Regras para Crédito Rotativo

**Feature Branch**: `001-revolving-credit-engine`

**Created**: 2026-08-18

**Status**: Draft

**Input**: User description: "Sistema de regras para avaliação e liberação de crédito rotativo em cartão de crédito — Itaú take-home case (PDF: 'CE 3 - Sistema de Regras para Crédito Rotativo em Cartão de Crédito')"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Avaliar um lote de clientes e obter aprovações/reprovações com valores (Priority: P1)

Como analista/avaliador, eu submeto uma lista de clientes fictícios (nome, CPF, score de crédito,
valor da fatura atual, limite disponível, número de atrasos e comportamento de gastos recente) e
recebo, para cada cliente, uma decisão (aprovado ou reprovado) e, quando aprovado, o valor máximo
de crédito rotativo liberado.

**Why this priority**: É o núcleo do desafio (itens 1–3 e 5 do case) — sem isso não há produto.
Todos os demais requisitos (extensibilidade, observabilidade, segurança) só fazem sentido em cima
deste fluxo funcionando.

**Independent Test**: Pode ser testado isoladamente enviando uma lista fixa de clientes fictícios
(cobrindo casos de aprovação e reprovação) e conferindo que a saída contém, para cada cliente, um
status e — se aprovado — um valor de crédito liberado maior que zero e menor ou igual ao limite
elegível.

**Acceptance Scenarios**:

1. **Given** um cliente com score alto, sem atrasos e fatura bem abaixo do limite, **When** o
   sistema avalia esse cliente, **Then** o cliente é aprovado e recebe um valor de crédito
   rotativo maior que zero.
2. **Given** um cliente com score baixo e múltiplos atrasos recentes, **When** o sistema avalia
   esse cliente, **Then** o cliente é reprovado e nenhum valor é liberado.
3. **Given** uma lista com múltiplos clientes em situações distintas, **When** o lote é avaliado,
   **Then** o relatório final separa claramente aprovados e reprovados, com o valor liberado ao
   lado de cada aprovado.

---

### User Story 2 - Adicionar uma nova regra de negócio sem alterar o núcleo da aplicação (Priority: P2)

Como responsável por manter as regras de crédito, eu preciso incluir uma nova regra de
elegibilidade ou uma nova forma de calcular o limite (ex.: um novo critério baseado em
comportamento de gastos) registrando uma nova unidade de regra, sem editar a lógica central de
orquestração da avaliação.

**Why this priority**: É um requisito explícito e avaliado do case (item 4) — "permitir a inclusão
de novas regras de negócio sem necessidade de alterar a estrutura principal da aplicação". Sem
isso, o design não atende a um critério de aceite nomeado no desafio.

**Independent Test**: Pode ser testado isoladamente escrevendo uma nova regra (nova classe/unidade)
e registrando-a, confirmando via teste automatizado que ela passa a ser aplicada nas avaliações sem
que nenhum arquivo do núcleo de orquestração precise ser modificado.

**Acceptance Scenarios**:

1. **Given** o motor de regras já em funcionamento com um conjunto inicial de regras, **When** uma
   nova regra é adicionada e registrada, **Then** ela passa a influenciar as decisões dos próximos
   clientes avaliados sem exigir mudanças na classe/função de orquestração central.
2. **Given** duas regras que avaliam o mesmo cliente, **When** ambas são aplicadas, **Then** o
   resultado final reflete a combinação determinística de ambas (ex.: reprovação de qualquer regra
   bloqueia a liberação), de forma testável isoladamente por regra.

---

### User Story 3 - Auditar critérios de decisão e acompanhar a operação (Priority: P3)

Como avaliador técnico (ou operador do sistema), eu quero ver, para cada cliente, quais critérios
levaram à decisão (motivos de aprovação/reprovação) e acompanhar métricas agregadas de volume e
taxa de aprovação/reprovação, sem que dados sensíveis do cliente (CPF, valores financeiros)
apareçam em texto claro em logs.

**Why this priority**: Cobre os itens 6 do desafio funcional (documentar critérios) e os requisitos
não funcionais de observabilidade e segurança/LGPD — importantes para a nota, mas dependem do fluxo
P1 já existir para terem o que auditar.

**Independent Test**: Pode ser testado isoladamente inspecionando a saída de uma avaliação e
confirmando que ela contém a lista de critérios/regras que geraram a decisão, e inspecionando os
logs gerados para confirmar que CPF e dados financeiros não aparecem sem mascaramento.

**Acceptance Scenarios**:

1. **Given** um cliente reprovado, **When** o resultado é consultado, **Then** o motivo (qual
   regra/critério reprovou) está disponível de forma legível.
2. **Given** um lote de clientes avaliado, **When** os logs/métricas são inspecionados, **Then** é
   possível identificar o volume total avaliado e a taxa de aprovação/reprovação, e nenhum CPF ou
   valor financeiro completo aparece sem mascaramento nos logs.

---

### Edge Cases

- Cliente com dados incompletos ou inválidos (ex.: score ausente, CPF malformado): o sistema deve
  rejeitar/reportar o registro como inválido em vez de aprovar por omissão ou falhar o lote inteiro.
- Cliente com CPF duplicado na mesma lista: comportamento deve ser definido (ex.: processar ambos
  independentemente, já que são solicitações/registros distintos) e documentado.
- Cliente no limite exato de um critério (ex.: score exatamente no valor mínimo aceito, número de
  atrasos exatamente no máximo tolerado): a regra deve definir explicitamente se a borda é inclusiva
  ou exclusiva.
- Cliente que seria aprovado por uma regra mas reprovado por outra: a combinação deve ser
  determinística e documentada (ex.: reprovação de qualquer regra é suficiente para reprovar).
- Lote vazio: o sistema deve retornar um relatório vazio válido, não erro.
- Volume de avaliação muito acima do usual (rajada): o sistema deve continuar respondendo dentro do
  limite de latência por cliente, ou degradar de forma previsível e observável (não travar).
- Falha temporária ao registrar/persistir o resultado de uma avaliação: o cliente deve, ainda assim,
  receber uma decisão; a falha de persistência não pode ser confundida com reprovação de crédito.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST aceitar dados de clientes fictícios contendo: nome, CPF, score de
  crédito, valor da fatura atual, limite disponível, número de atrasos e comportamento de gastos
  dos últimos meses.
- **FR-002**: O sistema MUST avaliar cada cliente contra um conjunto de regras de negócio
  pré-definidas para decidir se o crédito rotativo pode ser liberado.
- **FR-003**: O sistema MUST calcular o valor máximo de crédito rotativo concedido para cada
  cliente considerado elegível.
- **FR-004**: O sistema MUST permitir adicionar ou modificar regras de negócio (elegibilidade ou
  cálculo de limite) sem exigir alteração na lógica central de orquestração da avaliação (ex.:
  via Strategy ou Chain of Responsibility).
- **FR-005**: O sistema MUST gerar um relatório contendo, para o lote avaliado, os clientes
  aprovados, os reprovados e os respectivos valores liberados.
- **FR-006**: O sistema MUST registrar, para cada decisão, os critérios/regras que a
  determinaram, de forma legível e auditável.
- **FR-007**: O sistema MUST produzir a decisão de cada cliente individual em até 1 segundo após
  o recebimento dos seus dados.
- **FR-008**: O sistema MUST ser desenhado para sustentar picos de até 10.000 avaliações por
  minuto — quando não totalmente implementado/testado sob carga, a estratégia MUST estar
  documentada com justificativa.
- **FR-009**: O sistema MUST evitar expor CPF ou dados financeiros completos em texto claro em
  logs (mascaramento ou omissão), mesmo tratando-se de dados simulados.
- **FR-010**: O sistema MUST expor métricas/logs que permitam acompanhar volume de avaliações,
  taxa de aprovação/reprovação e erros de processamento.
- **FR-011**: O sistema MUST continuar entregando uma decisão ao cliente mesmo diante de falha
  temporária em uma dependência não essencial à decisão em si (ex.: falha ao persistir o
  relatório não deve impedir a resposta da avaliação) — a estratégia de resiliência MUST estar
  documentada.
- **FR-012**: O sistema MUST rejeitar/reportar como inválido um registro de cliente com dados
  obrigatórios ausentes ou incoerentes, em vez de avaliá-lo com valores padrão silenciosos.
- **FR-013**: O sistema MUST expor a avaliação de crédito via uma interface de API que aceite um
  ou mais clientes e retorne a(s) decisão(ões) correspondente(s).
- **FR-014**: O sistema MUST oferecer um modo de execução em lote (reaproveitando o mesmo motor de
  regras da API) que processe uma lista fictícia de clientes e produza o relatório consolidado do
  FR-005, sem exigir chamadas HTTP manuais uma a uma para a demonstração.
- **FR-015**: O sistema MUST persistir clientes e decisões de avaliação em um banco de dados real
  (não apenas em memória), com a inicialização do banco (schema/seed) automatizada por um processo
  de setup do projeto (`Makefile`) reproduzível em uma máquina limpa.

### Key Entities

- **Cliente**: representa o cliente avaliado — nome, CPF, score de crédito, valor da fatura
  atual, limite disponível, número de atrasos, e uma série de comportamento de gastos dos
  últimos meses (histórico simulado).
- **Regra de Negócio**: unidade independente que avalia um Cliente e contribui para a decisão de
  elegibilidade e/ou para o cálculo do valor de crédito liberado. Deve ser identificável
  (nome/critério) para fins de auditoria.
- **Decisão de Avaliação**: resultado da aplicação de todas as regras a um Cliente — status
  (aprovado/reprovado), valor de crédito rotativo liberado (quando aprovado), e a lista de
  critérios/regras que fundamentaram o resultado.
- **Relatório de Avaliação**: agregação das Decisões de um lote de Clientes, organizado por
  aprovados e reprovados, com os respectivos valores liberados.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Para 100% dos clientes de um lote submetido, o sistema retorna uma decisão
  (aprovado ou reprovado) com pelo menos um critério/motivo associado.
- **SC-002**: Uma nova regra de negócio pode ser incorporada ao sistema sem que nenhum arquivo do
  núcleo de orquestração da avaliação precise ser alterado — verificável por revisão de código
  (diff da mudança não toca a orquestração central).
- **SC-003**: Cada decisão individual é produzida em até 1 segundo, medido do recebimento dos
  dados do cliente até a decisão estar disponível.
- **SC-004**: Um stakeholder de negócio consegue, a partir do relatório gerado, identificar quais
  clientes foram aprovados, quais foram reprovados e o valor liberado a cada aprovado, sem
  precisar ler código-fonte.
- **SC-005**: Em nenhum log gerado durante a operação normal aparece um CPF ou valor financeiro
  completo sem mascaramento.

## Assumptions

- Os dados de clientes são fictícios/simulados; não há integração real com sistemas bancários
  externos — mocks e simulações são aceitos e esperados (conforme o case).
- O "comportamento de gastos nos últimos meses" é fornecido como parte do próprio payload do
  cliente (uma série simples associada ao registro), não como consulta a um repositório de
  histórico externo — mantém o motor autocontido para fins de simulação.
- O requisito de 10.000 avaliações/minuto é tratado como meta de design a ser endereçada e
  justificada na arquitetura/documentação; testes de carga completos em nível produtivo estão
  fora do escopo deste take-home (ver constituição, Princípio IV).
- Autenticação/autorização reais (ex.: OAuth, gestão de usuários) não serão implementadas; a
  abordagem de segurança/controle de acesso esperada em produção será documentada como decisão de
  design, não codificada neste exercício.
- O banco de dados usado no case pode ser simplificado em relação a um ambiente produtivo (ex.:
  rodando localmente via container, sem alta disponibilidade real), desde que a inicialização via
  `Makefile` seja reproduzível e o schema reflita as entidades do domínio (Cliente, Decisão de
  Avaliação); a estratégia de HA/backup de produção é documentada, não implementada.

## Clarifications

### Session 2026-08-18

- Q: Qual deve ser a interface primária de entrega da solução? → A: Híbrida — API REST (Spring
  Boot) para avaliar cliente(s), reaproveitada por um runner/seed em lote que processa uma lista
  fictícia de clientes e produz o relatório do item 5 do case.
- Q: As regras de elegibilidade/cálculo de limite devem ser propostas por mim (com thresholds e
  fórmula justificados) ou o usuário já tem regras específicas em mente? → A: O conjunto de
  regras (score mínimo, atrasos máximos, relação fatura/limite, tendência de gastos, fórmula de
  limite) é proposto e documentado com justificativa, sujeito a ajuste na revisão.
- Q: A persistência deve ser apenas em memória (documentada) ou deve incluir um banco de dados
  real (ex.: H2 embarcado) para também demonstrar modelagem/armazenamento de dados? → A: Banco de
  dados real, com inicialização automatizada via `Makefile` (setup/seed reproduzível), para
  demonstrar na prática a habilidade avaliada "Modelagem, Armazenamento e Governança de Dados" —
  não apenas documentada em texto.
