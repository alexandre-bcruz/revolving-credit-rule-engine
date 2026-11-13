package com.creditengine.revolving_credit_rule_engine.domain.regras

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.ComportamentoGastoMensal
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegraScoreMinimoTest {

    private val regra = RegraScoreMinimo()

    private fun clienteComScore(score: Int) = Cliente(
        cpf = "12345678901",
        nome = "Teste",
        scoreCredito = score,
        valorFaturaAtual = BigDecimal("500"),
        limiteDisponivel = BigDecimal("1500"),
        numeroAtrasos = 0,
        comportamentoGastos = listOf(ComportamentoGastoMensal(YearMonth.of(2026, 7), BigDecimal("500")))
    )

    @Test
    fun `aprova quando score esta acima do minimo`() {
        assertTrue(regra.avaliar(clienteComScore(700)).aprovada)
    }

    @Test
    fun `reprova quando score esta abaixo do minimo`() {
        assertFalse(regra.avaliar(clienteComScore(599)).aprovada)
    }

    @Test
    fun `aprova na fronteira exata do minimo`() {
        assertTrue(regra.avaliar(clienteComScore(600)).aprovada)
    }
}
