package com.creditengine.revolving_credit_rule_engine.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class StatusAvaliacao { APROVADO, REPROVADO }

data class DecisaoAvaliacao(
    val id: UUID,
    val clienteCpf: String,
    val status: StatusAvaliacao,
    val valorLiberado: BigDecimal,
    val motivos: List<ResultadoRegra>,
    val avaliadoEm: Instant
)
