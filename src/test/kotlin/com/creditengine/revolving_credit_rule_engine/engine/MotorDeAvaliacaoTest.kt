package com.creditengine.revolving_credit_rule_engine.engine

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.ComportamentoGastoMensal
import com.creditengine.revolving_credit_rule_engine.domain.RegraCredito
import com.creditengine.revolving_credit_rule_engine.domain.ResultadoRegra
import com.creditengine.revolving_credit_rule_engine.domain.StatusAvaliacao
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotorDeAvaliacaoTest {

    private fun clientePadrao(score: Int = 1000, fatura: String = "0", limite: String = "1000") = Cliente(
        cpf = "12345678901",
        nome = "Teste",
        scoreCredito = score,
        valorFaturaAtual = BigDecimal(fatura),
        limiteDisponivel = BigDecimal(limite),
        numeroAtrasos = 0,
        comportamentoGastos = listOf(ComportamentoGastoMensal(YearMonth.of(2026, 7), BigDecimal("500")))
    )

    @Test
    fun `aprova e libera valor quando todas as regras aprovam`() {
        val regraAprova = RegraCredito { ResultadoRegra("RegraOk", true, "ok") }
        val motor = MotorDeAvaliacao(listOf(regraAprova))

        val decisao = motor.avaliar(clientePadrao(score = 1000, fatura = "0", limite = "1000"))

        assertEquals(StatusAvaliacao.APROVADO, decisao.status)
        // fatorScore = 1.0, fatorFolga = 1 - 0/(0+1000) = 1.0 => valorLiberado = limiteDisponivel
        assertEquals(0, decisao.valorLiberado.compareTo(BigDecimal("1000.00")))
        assertEquals(1, decisao.motivos.size)
    }

    @Test
    fun `reprova quando qualquer regra reprova e nao libera valor`() {
        val regraAprova = RegraCredito { ResultadoRegra("RegraOk", true, "ok") }
        val regraReprova = RegraCredito { ResultadoRegra("RegraFalha", false, "falhou") }
        val motor = MotorDeAvaliacao(listOf(regraAprova, regraReprova))

        val decisao = motor.avaliar(clientePadrao())

        assertEquals(StatusAvaliacao.REPROVADO, decisao.status)
        assertEquals(0, decisao.valorLiberado.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `mantem o motivo de todas as regras mesmo quando uma reprova`() {
        val regraAprova = RegraCredito { ResultadoRegra("RegraOk", true, "ok") }
        val regraReprova = RegraCredito { ResultadoRegra("RegraFalha", false, "falhou") }
        val motor = MotorDeAvaliacao(listOf(regraAprova, regraReprova))

        val decisao = motor.avaliar(clientePadrao())

        assertEquals(2, decisao.motivos.size)
        assertTrue(decisao.motivos.any { it.nomeRegra == "RegraOk" })
        assertTrue(decisao.motivos.any { it.nomeRegra == "RegraFalha" })
    }

    @Test
    fun `valor liberado nunca excede o limite disponivel`() {
        val regraAprova = RegraCredito { ResultadoRegra("RegraOk", true, "ok") }
        val motor = MotorDeAvaliacao(listOf(regraAprova))

        val decisao = motor.avaliar(clientePadrao(score = 1000, fatura = "0", limite = "2000"))

        assertTrue(decisao.valorLiberado <= BigDecimal("2000"))
    }
}
