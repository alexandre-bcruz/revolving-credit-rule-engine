package com.creditengine.revolving_credit_rule_engine.domain.regras

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.RegraCredito
import com.creditengine.revolving_credit_rule_engine.domain.ResultadoRegra
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

private val CRESCIMENTO_MAXIMO = BigDecimal("0.3")

/**
 * Crescimento médio mês a mês de gastos acima de 30% é um sinal comportamental de risco —
 * ver data-model.md. Histórico com menos de 2 pontos não tem tendência a avaliar (aprovada
 * por padrão, não é motivo de reprovação por insuficiência de dados).
 */
@Component
class RegraTendenciaDeGastos : RegraCredito {
    override fun avaliar(cliente: Cliente): ResultadoRegra {
        val serie = cliente.comportamentoGastos.sortedBy { it.mes }
        if (serie.size < 2) {
            return ResultadoRegra(
                "RegraTendenciaDeGastos",
                true,
                "histórico insuficiente para avaliar tendência (${serie.size} mês(es))"
            )
        }

        val taxas = serie.zipWithNext { anterior, atual ->
            if (anterior.valor <= BigDecimal.ZERO) {
                BigDecimal.ZERO
            } else {
                atual.valor.subtract(anterior.valor).divide(anterior.valor, 6, RoundingMode.HALF_UP)
            }
        }
        val crescimentoMedio = taxas.reduce(BigDecimal::add)
            .divide(BigDecimal(taxas.size), 6, RoundingMode.HALF_UP)

        val aprovada = crescimentoMedio <= CRESCIMENTO_MAXIMO
        val detalhe = if (aprovada) {
            "crescimento médio de gastos $crescimentoMedio dentro do máximo de $CRESCIMENTO_MAXIMO"
        } else {
            "crescimento médio de gastos $crescimentoMedio acima do máximo de $CRESCIMENTO_MAXIMO"
        }
        return ResultadoRegra("RegraTendenciaDeGastos", aprovada, detalhe)
    }
}
