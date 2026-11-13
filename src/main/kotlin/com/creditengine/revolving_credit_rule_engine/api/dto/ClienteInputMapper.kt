package com.creditengine.revolving_credit_rule_engine.api.dto

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.ComportamentoGastoMensal
import java.time.YearMonth

/** Compartilhado entre a API (AvaliacaoController) e o runner em lote (RelatorioLoteRunner). */
object ClienteInputMapper {
    fun paraDominio(input: ClienteInputDto): Cliente = Cliente(
        cpf = input.cpf!!,
        nome = input.nome!!,
        scoreCredito = input.scoreCredito!!,
        valorFaturaAtual = input.valorFaturaAtual!!,
        limiteDisponivel = input.limiteDisponivel!!,
        numeroAtrasos = input.numeroAtrasos!!,
        comportamentoGastos = input.comportamentoGastos!!.map {
            ComportamentoGastoMensal(YearMonth.parse(it.mes), it.valor)
        }
    )
}
