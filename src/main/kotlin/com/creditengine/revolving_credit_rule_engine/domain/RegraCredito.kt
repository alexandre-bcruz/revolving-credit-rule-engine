package com.creditengine.revolving_credit_rule_engine.domain

/**
 * Contrato de extensão (Strategy) do motor de regras — Constitution Principle I.
 * Nova regra de negócio = nova implementação registrada como `@Component`,
 * nunca uma mudança em `engine.MotorDeAvaliacao`.
 */
fun interface RegraCredito {
    fun avaliar(cliente: Cliente): ResultadoRegra
}
