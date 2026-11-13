-- Seed opcional: alguns clientes de exemplo já persistidos, para inspecionar o schema
-- diretamente via psql sem precisar rodar a API ou o modo batch primeiro.
INSERT INTO cliente (cpf, nome, score_credito, valor_fatura_atual, limite_disponivel, numero_atrasos, comportamento_gastos)
VALUES
    ('90000000001', 'Lucas Andrade', 800, 300.00, 2700.00, 0, '[{"mes":"2026-07","valor":900.00}]'),
    ('90000000002', 'Juliana Ramos', 480, 1900.00, 100.00, 6, '[{"mes":"2026-07","valor":1900.00}]')
ON CONFLICT (cpf) DO NOTHING;
