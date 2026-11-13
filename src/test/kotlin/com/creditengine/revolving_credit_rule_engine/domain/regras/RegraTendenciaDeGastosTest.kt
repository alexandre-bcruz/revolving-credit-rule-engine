package com.creditengine.revolving_credit_rule_engine.domain.regras

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.ComportamentoGastoMensal
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegraTendenciaDeGastosTest {

    private val regra = RegraTendenciaDeGastos()

    private fun clienteComSerie(vararg valores: String) = Cliente(
        cpf = "12345678901",
        nome = "Teste",
        scoreCredito = 700,
        valorFaturaAtual = BigDecimal("500"),
        limiteDisponivel = BigDecimal("1500"),
        numeroAtrasos = 0,
        comportamentoGastos = valores.mapIndexed { index, valor ->
            ComportamentoGastoMensal(YearMonth.of(2026, index + 1), BigDecimal(valor))
        }
    )

    @Test
    fun `aprova quando crescimento medio esta bem abaixo do maximo`() {
        assertTrue(regra.avaliar(clienteComSerie("1000", "1050")).aprovada)
    }

    @Test
    fun `reprova quando crescimento medio excede o maximo`() {
        assertFalse(regra.avaliar(clienteComSerie("1000", "1400")).aprovada)
    }

    @Test
    fun `aprova na fronteira exata de 30 por cento`() {
        assertTrue(regra.avaliar(clienteComSerie("1000", "1300")).aprovada)
    }

    @Test
    fun `aprova por padrao quando historico tem menos de dois meses`() {
        assertTrue(regra.avaliar(clienteComSerie("1000")).aprovada)
    }
}
