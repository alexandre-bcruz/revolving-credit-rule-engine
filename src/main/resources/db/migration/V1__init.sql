CREATE TABLE cliente (
    cpf                  VARCHAR(11) PRIMARY KEY,
    nome                 VARCHAR(255) NOT NULL,
    score_credito        INTEGER NOT NULL CHECK (score_credito BETWEEN 0 AND 1000),
    valor_fatura_atual   NUMERIC(14, 2) NOT NULL CHECK (valor_fatura_atual >= 0),
    limite_disponivel    NUMERIC(14, 2) NOT NULL CHECK (limite_disponivel >= 0),
    numero_atrasos       INTEGER NOT NULL CHECK (numero_atrasos >= 0),
    comportamento_gastos JSONB NOT NULL
);

CREATE TABLE decisao_avaliacao (
    id             UUID PRIMARY KEY,
    cliente_cpf    VARCHAR(11) NOT NULL REFERENCES cliente (cpf),
    status         VARCHAR(20) NOT NULL CHECK (status IN ('APROVADO', 'REPROVADO')),
    valor_liberado NUMERIC(14, 2) NOT NULL CHECK (valor_liberado >= 0),
    avaliado_em    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_decisao_avaliacao_cliente_cpf ON decisao_avaliacao (cliente_cpf);
CREATE INDEX idx_decisao_avaliacao_avaliado_em ON decisao_avaliacao (avaliado_em);

CREATE TABLE resultado_regra (
    id         BIGSERIAL PRIMARY KEY,
    decisao_id UUID NOT NULL REFERENCES decisao_avaliacao (id),
    nome_regra VARCHAR(100) NOT NULL,
    aprovada   BOOLEAN NOT NULL,
    detalhe    TEXT NOT NULL
);

CREATE INDEX idx_resultado_regra_decisao_id ON resultado_regra (decisao_id);
CREATE INDEX idx_resultado_regra_nome_regra ON resultado_regra (nome_regra);
