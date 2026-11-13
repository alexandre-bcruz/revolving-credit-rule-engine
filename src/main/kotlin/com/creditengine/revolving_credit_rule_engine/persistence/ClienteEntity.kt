package com.creditengine.revolving_credit_rule_engine.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal

@Entity
@Table(name = "cliente")
class ClienteEntity(
    @Id
    @Column(name = "cpf", length = 11)
    val cpf: String,

    @Column(name = "nome", nullable = false)
    val nome: String,

    @Column(name = "score_credito", nullable = false)
    val scoreCredito: Int,

    @Column(name = "valor_fatura_atual", nullable = false, precision = 14, scale = 2)
    val valorFaturaAtual: BigDecimal,

    @Column(name = "limite_disponivel", nullable = false, precision = 14, scale = 2)
    val limiteDisponivel: BigDecimal,

    @Column(name = "numero_atrasos", nullable = false)
    val numeroAtrasos: Int,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "comportamento_gastos", nullable = false, columnDefinition = "jsonb")
    val comportamentoGastos: String
)
