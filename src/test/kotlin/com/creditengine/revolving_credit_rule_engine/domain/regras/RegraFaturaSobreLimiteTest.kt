package com.creditengine.revolving_credit_rule_engine.domain.regras

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.ComportamentoGastoMensal
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegraFaturaSobreLimiteTest {

    private val regra = RegraFaturaSobreLimite()

    private fun clienteCom(fatura: String, limite: String) = Cliente(
        cpf = "12345678901",
        nome = "Teste",
        scoreCredito = 700,
        valorFaturaAtual = BigDecimal(fatura),
        limiteDisponivel = BigDecimal(limite),
        numeroAtrasos = 0,
        comportamentoGastos = listOf(ComportamentoGastoMensal(YearMonth.of(2026, 7), BigDecimal("500")))
    )

    @Test
    fun `aprova quando comprometimento esta bem abaixo do maximo`() {
        assertTrue(regra.avaliar(clienteCom("100", "900")).aprovada)
    }

    @Test
    fun `reprova quando comprometimento excede o maximo`() {
        assertFalse(regra.avaliar(clienteCom("900", "100")).aprovada)
    }

    @Test
    fun `aprova na fronteira exata de 80 por cento`() {
        assertTrue(regra.avaliar(clienteCom("800", "200")).aprovada)
    }
}
