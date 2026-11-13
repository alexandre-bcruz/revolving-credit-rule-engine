# Data Model: Sistema de Regras para Crédito Rotativo

## Entidades

### Cliente

| Campo | Tipo | Regras de validação |
|---|---|---|
| `cpf` | string (11 dígitos) | obrigatório, exatamente 11 dígitos numéricos (sem validação de dígito verificador — dados fictícios); identifica o cliente dentro de um lote |
| `nome` | string | obrigatório, não vazio |
| `scoreCredito` | inteiro (0–1000) | obrigatório, faixa 0–1000 (escala de score de mercado, ex. Serasa) |
| `valorFaturaAtual` | decimal ≥ 0 | obrigatório |
| `limiteDisponivel` | decimal ≥ 0 | obrigatório |
| `numeroAtrasos` | inteiro ≥ 0 | obrigatório — atrasos nos últimos 12 meses |
| `comportamentoGastos` | lista de `{mes, valor}` | obrigatório, mínimo 1 item — histórico simulado dos últimos meses |

Um `Cliente` com qualquer campo obrigatório ausente ou fora da faixa é rejeitado como registro
inválido (FR-012), não avaliado com valores padrão.

### RegraCredito (contrato de extensão, não persistido)

Interface implementada por cada regra concreta. Cada implementação retorna um `ResultadoRegra`
para um dado `Cliente`. Novas regras = novas implementações registradas como componentes; ver
Princípio I / research.md §1.

### ResultadoRegra

| Campo | Tipo | Descrição |
|---|---|---|
| `nomeRegra` | string | identifica a regra para auditoria (FR-006) |
| `aprovada` | boolean | se este critério específico foi atendido |
| `detalhe` | string | explicação legível (ex.: "score 550 abaixo do mínimo 600") |

### DecisaoAvaliacao (persistida)

| Campo | Tipo | Descrição |
|---|---|---|
| `id` | UUID | chave primária |
| `clienteCpf` | string | FK lógica para o Cliente avaliado |
| `status` | enum `APROVADO`/`REPROVADO` | reprovado se qualquer `ResultadoRegra.aprovada == false` |
| `valorLiberado` | decimal | 0 se reprovado; calculado conforme fórmula abaixo se aprovado |
| `motivos` | lista de `ResultadoRegra` | resultado de **todas** as regras aplicadas (não só a que reprovou) |
| `avaliadoEm` | timestamp | quando a decisão foi calculada |

### RelatorioAvaliacao (agregação, não persistida como entidade própria — projeção sobre `DecisaoAvaliacao`)

| Campo | Tipo | Descrição |
|---|---|---|
| `totalAvaliados` | inteiro | tamanho do lote |
| `totalAprovados` / `totalReprovados` | inteiro | contagens |
| `decisoes` | lista de `DecisaoAvaliacao` | detalhamento por cliente |

## Regras de negócio propostas (com justificativa)

Conjunto inicial que demonstra o motor extensível — ajustável na revisão com o avaliador, mas
deterministo e testável desde já:

| Regra | Critério | Justificativa |
|---|---|---|
| `RegraScoreMinimo` | `scoreCredito >= 600` | Score abaixo de 600 (escala 0–1000) é geralmente associado a risco de inadimplência elevado nas faixas de mercado brasileiras — corte conservador para uma modalidade de crédito rotativo (juros altos, sem garantia). |
| `RegraAtrasosMaximos` | `numeroAtrasos <= 2` | Mais de 2 atrasos nos últimos 12 meses indica inadimplência recorrente, não pontual — reprova independentemente do score. |
| `RegraFaturaSobreLimite` | `valorFaturaAtual / (valorFaturaAtual + limiteDisponivel) <= 0.8` | Cliente já comprometendo mais de 80% do limite total está com baixa folga — liberar rotativo aumentaria o risco de superendividamento. |
| `RegraTendenciaDeGastos` | crescimento médio mês a mês do `comportamentoGastos` ≤ 30% | Crescimento acelerado e sustentado de gastos sem contrapartida de score/renda é um sinal comportamental de risco (parte do requisito "comportamento de gastos" do case). |

Todas as regras são avaliadas para todo cliente (nunca curto-circuitam) — o `status` final é
`REPROVADO` se **qualquer** regra reprovar, mas o relatório sempre mostra o resultado de todas,
por exigência do Princípio III (Explainable Decisions).

### Fórmula do valor liberado (apenas para clientes aprovados)

```
fatorScore = min(scoreCredito / 1000, 1.0)
fatorFolga = 1 - (valorFaturaAtual / (valorFaturaAtual + limiteDisponivel))
valorLiberado = limiteDisponivel * fatorScore * fatorFolga
```

**Justificativa**: quanto maior o score e quanto mais folga o cliente tem em relação ao limite já
comprometido, maior a fração do limite disponível liberada como rotativo; o valor nunca excede o
`limiteDisponivel`. É uma fórmula simples e auditável (cada fator é rastreável no `detalhe` do
`ResultadoRegra` correspondente), adequada para o escopo do case — uma calibração real usaria
modelos estatísticos/de risco proprietários, fora de escopo aqui (documentado como limitação).

## Persistência (PostgreSQL, via Flyway)

Três tabelas cobrem o modelo acima:

- `cliente` (cpf PK, nome, score_credito, valor_fatura_atual, limite_disponivel, numero_atrasos,
  comportamento_gastos jsonb)
- `decisao_avaliacao` (id PK, cliente_cpf FK, status, valor_liberado, avaliado_em)
- `resultado_regra` (id PK, decisao_id FK, nome_regra, aprovada, detalhe) — uma linha por regra
  aplicada a cada decisão, com índice em `nome_regra`

`comportamento_gastos` continua `jsonb`: é histórico de entrada do cliente, sem necessidade de
consulta analítica no escopo deste case. Já `motivos` foi normalizado em `resultado_regra` — decisão
deliberada (não a recomendação inicial, que era `jsonb` também para `motivos`) para permitir
consultas indexadas por regra (ex.: "quantos clientes reprovaram por `RegraFaturaSobreLimite` este
mês" vira um `WHERE nome_regra = ... AND aprovada = false`, sem parsear JSON), mais alinhado a um
cenário real onde compliance/risco audita reprovações por critério. Trade-off aceito: mais uma
tabela e um `saveAll` extra no `DecisaoPersistenceService`, sem cascade JPA — persistência
explícita, consistente com `cliente`/`decisao_avaliacao`, que também não usam relações JPA.
