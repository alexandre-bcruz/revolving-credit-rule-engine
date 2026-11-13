package com.creditengine.revolving_credit_rule_engine.domain.regras

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.RegraCredito
import com.creditengine.revolving_credit_rule_engine.domain.ResultadoRegra
import org.springframework.stereotype.Component
import java.math.BigDecimal

private val LIMITE_MINIMO = BigDecimal("50")

/**
 * Regra adicionada DEPOIS do MVP (User Story 2) para demonstrar extensibilidade: nenhuma linha
 * de engine/MotorDeAvaliacao.kt foi tocada para incluí-la — basta esta classe existir como
 * `@Component` (ver MotorDeAvaliacaoSpringWiringTest e SC-002).
 */
@Component
class RegraLimiteMinimoAbsoluto : RegraCredito {
    override fun avaliar(cliente: Cliente): ResultadoRegra {
        val aprovada = cliente.limiteDisponivel >= LIMITE_MINIMO
        val detalhe = if (aprovada) {
            "limiteDisponivel ${cliente.limiteDisponivel} atende ao mínimo absoluto de $LIMITE_MINIMO"
        } else {
            "limiteDisponivel ${cliente.limiteDisponivel} abaixo do mínimo absoluto de $LIMITE_MINIMO"
        }
        return ResultadoRegra("RegraLimiteMinimoAbsoluto", aprovada, detalhe)
    }
}
