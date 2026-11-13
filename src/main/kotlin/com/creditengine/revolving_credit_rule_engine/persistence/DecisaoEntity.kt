package com.creditengine.revolving_credit_rule_engine.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "decisao_avaliacao")
class DecisaoEntity(
    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "cliente_cpf", nullable = false, length = 11)
    val clienteCpf: String,

    @Column(name = "status", nullable = false, length = 20)
    val status: String,

    @Column(name = "valor_liberado", nullable = false, precision = 14, scale = 2)
    val valorLiberado: BigDecimal,

    @Column(name = "avaliado_em", nullable = false)
    val avaliadoEm: Instant
)
