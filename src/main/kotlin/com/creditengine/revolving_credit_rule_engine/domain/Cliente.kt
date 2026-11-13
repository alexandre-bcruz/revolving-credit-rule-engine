package com.creditengine.revolving_credit_rule_engine.domain

import java.math.BigDecimal
import java.time.YearMonth

data class ComportamentoGastoMensal(
    val mes: YearMonth,
    val valor: BigDecimal
)

data class Cliente(
    val cpf: String,
    val nome: String,
    val scoreCredito: Int,
    val valorFaturaAtual: BigDecimal,
    val limiteDisponivel: BigDecimal,
    val numeroAtrasos: Int,
    val comportamentoGastos: List<ComportamentoGastoMensal>
)
