package com.creditengine.revolving_credit_rule_engine.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "resultado_regra")
class ResultadoRegraEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "decisao_id", nullable = false)
    val decisaoId: UUID,

    @Column(name = "nome_regra", nullable = false, length = 100)
    val nomeRegra: String,

    @Column(name = "aprovada", nullable = false)
    val aprovada: Boolean,

    @Column(name = "detalhe", nullable = false, columnDefinition = "text")
    val detalhe: String
)
