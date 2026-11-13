package com.creditengine.revolving_credit_rule_engine.domain.regras

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.RegraCredito
import com.creditengine.revolving_credit_rule_engine.domain.ResultadoRegra
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

private val COMPROMETIMENTO_MAXIMO = BigDecimal("0.8")

/**
 * Cliente já comprometendo mais de 80% do limite total (fatura + limite) tem baixa folga —
 * ver data-model.md.
 */
@Component
class RegraFaturaSobreLimite : RegraCredito {
    override fun avaliar(cliente: Cliente): ResultadoRegra {
        val total = cliente.valorFaturaAtual.add(cliente.limiteDisponivel)
        val comprometimento = if (total <= BigDecimal.ZERO) {
            BigDecimal.ZERO
        } else {
            cliente.valorFaturaAtual.divide(total, 4, RoundingMode.HALF_UP)
        }
        val aprovada = comprometimento <= COMPROMETIMENTO_MAXIMO
        val detalhe = if (aprovada) {
            "comprometimento $comprometimento dentro do máximo de $COMPROMETIMENTO_MAXIMO"
        } else {
            "comprometimento $comprometimento acima do máximo de $COMPROMETIMENTO_MAXIMO"
        }
        return ResultadoRegra("RegraFaturaSobreLimite", aprovada, detalhe)
    }
}
