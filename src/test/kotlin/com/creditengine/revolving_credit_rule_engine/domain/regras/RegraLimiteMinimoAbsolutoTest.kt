package com.creditengine.revolving_credit_rule_engine.domain.regras

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.ComportamentoGastoMensal
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegraLimiteMinimoAbsolutoTest {

    private val regra = RegraLimiteMinimoAbsoluto()

    private fun clienteComLimite(limite: String) = Cliente(
        cpf = "12345678901",
        nome = "Teste",
        scoreCredito = 700,
        valorFaturaAtual = BigDecimal("0"),
        limiteDisponivel = BigDecimal(limite),
        numeroAtrasos = 0,
        comportamentoGastos = listOf(ComportamentoGastoMensal(YearMonth.of(2026, 7), BigDecimal("500")))
    )

    @Test
    fun `aprova quando limite disponivel esta bem acima do minimo`() {
        assertTrue(regra.avaliar(clienteComLimite("100")).aprovada)
    }

    @Test
    fun `reprova quando limite disponivel esta abaixo do minimo`() {
        assertFalse(regra.avaliar(clienteComLimite("10")).aprovada)
    }

    @Test
    fun `aprova na fronteira exata do minimo`() {
        assertTrue(regra.avaliar(clienteComLimite("50")).aprovada)
    }
}
