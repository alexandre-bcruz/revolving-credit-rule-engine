package com.creditengine.revolving_credit_rule_engine.domain.regras

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.RegraCredito
import com.creditengine.revolving_credit_rule_engine.domain.ResultadoRegra
import org.springframework.stereotype.Component

private const val SCORE_MINIMO = 600

/** Score abaixo de 600 (escala 0-1000) é associado a risco elevado — ver data-model.md. */
@Component
class RegraScoreMinimo : RegraCredito {
    override fun avaliar(cliente: Cliente): ResultadoRegra {
        val aprovada = cliente.scoreCredito >= SCORE_MINIMO
        val detalhe = if (aprovada) {
            "score ${cliente.scoreCredito} atende ao mínimo de $SCORE_MINIMO"
        } else {
            "score ${cliente.scoreCredito} abaixo do mínimo de $SCORE_MINIMO"
        }
        return ResultadoRegra("RegraScoreMinimo", aprovada, detalhe)
    }
}
