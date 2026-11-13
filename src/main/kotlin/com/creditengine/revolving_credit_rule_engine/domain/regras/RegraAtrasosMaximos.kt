package com.creditengine.revolving_credit_rule_engine.domain.regras

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.RegraCredito
import com.creditengine.revolving_credit_rule_engine.domain.ResultadoRegra
import org.springframework.stereotype.Component

private const val ATRASOS_MAXIMOS = 2

/** Mais de 2 atrasos nos últimos 12 meses indica inadimplência recorrente — ver data-model.md. */
@Component
class RegraAtrasosMaximos : RegraCredito {
    override fun avaliar(cliente: Cliente): ResultadoRegra {
        val aprovada = cliente.numeroAtrasos <= ATRASOS_MAXIMOS
        val detalhe = if (aprovada) {
            "numeroAtrasos ${cliente.numeroAtrasos} dentro do máximo de $ATRASOS_MAXIMOS"
        } else {
            "numeroAtrasos ${cliente.numeroAtrasos} acima do máximo de $ATRASOS_MAXIMOS"
        }
        return ResultadoRegra("RegraAtrasosMaximos", aprovada, detalhe)
    }
}
