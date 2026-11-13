package com.creditengine.revolving_credit_rule_engine.domain.regras

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.ComportamentoGastoMensal
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegraAtrasosMaximosTest {

    private val regra = RegraAtrasosMaximos()

    private fun clienteComAtrasos(atrasos: Int) = Cliente(
        cpf = "12345678901",
        nome = "Teste",
        scoreCredito = 700,
        valorFaturaAtual = BigDecimal("500"),
        limiteDisponivel = BigDecimal("1500"),
        numeroAtrasos = atrasos,
        comportamentoGastos = listOf(ComportamentoGastoMensal(YearMonth.of(2026, 7), BigDecimal("500")))
    )

    @Test
    fun `aprova quando numero de atrasos esta abaixo do maximo`() {
        assertTrue(regra.avaliar(clienteComAtrasos(0)).aprovada)
    }

    @Test
    fun `reprova quando numero de atrasos excede o maximo`() {
        assertFalse(regra.avaliar(clienteComAtrasos(3)).aprovada)
    }

    @Test
    fun `aprova na fronteira exata do maximo`() {
        assertTrue(regra.avaliar(clienteComAtrasos(2)).aprovada)
    }
}
