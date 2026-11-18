package com.creditengine.revolving_credit_rule_engine.batch

import com.creditengine.revolving_credit_rule_engine.domain.regras.RegraAtrasosMaximos
import com.creditengine.revolving_credit_rule_engine.domain.regras.RegraFaturaSobreLimite
import com.creditengine.revolving_credit_rule_engine.domain.regras.RegraLimiteMinimoAbsoluto
import com.creditengine.revolving_credit_rule_engine.domain.regras.RegraScoreMinimo
import com.creditengine.revolving_credit_rule_engine.domain.regras.RegraTendenciaDeGastos
import com.creditengine.revolving_credit_rule_engine.engine.MotorDeAvaliacao
import com.creditengine.revolving_credit_rule_engine.persistence.DecisaoPersistenceService
import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cobre o isolamento por registro do lote (FR-012): registros inválidos — por validação ou por
 * tipo errado no JSON — vão para `invalidos` sem impedir a avaliação dos demais.
 */
class ProcessadorLoteTest {

    private val persistence = mock(DecisaoPersistenceService::class.java)
    private val motor = MotorDeAvaliacao(
        listOf(
            RegraScoreMinimo(), RegraAtrasosMaximos(), RegraFaturaSobreLimite(),
            RegraTendenciaDeGastos(), RegraLimiteMinimoAbsoluto()
        )
    )
    private val validator = Validation.buildDefaultValidatorFactory().validator
    /** Mesmos módulos que o Spring Boot registra (descoberta automática via ServiceLoader). */
    private val mapper = JsonMapper.builder().findAndAddModules().build()
    private val processador = ProcessadorLote(motor, persistence, validator, mapper)

    private fun processar(json: String) = processador.processar(mapper.readTree(json).values().toList())

    private fun cliente(cpf: String, score: Any = 800, extra: String = "") = """
        {
          "cpf": "$cpf", "nome": "Teste", "scoreCredito": ${if (score is String) "\"$score\"" else score},
          "valorFaturaAtual": 100.00, "limiteDisponivel": 900.00, "numeroAtrasos": 0,
          "comportamentoGastos": [{"mes": "2026-06", "valor": 500.00}, {"mes": "2026-07", "valor": 520.00}]
          $extra
        }
    """

    @Test
    fun `registro com tipo errado no JSON vai para invalidos sem derrubar o lote`() {
        val relatorio = processar("[${cliente("11111111111")}, ${cliente("22222222222", score = "abc")}]")

        assertEquals(2, relatorio.resumo.totalRecebidos)
        assertEquals(1, relatorio.resumo.totalAprovados)
        assertEquals(1, relatorio.resumo.totalInvalidos)
        val invalido = relatorio.invalidos.single()
        assertEquals(1, invalido.indice)
        assertEquals("22222222222", invalido.cpf)
        assertTrue(invalido.motivo.contains("scoreCredito"), invalido.motivo)
    }

    @Test
    fun `registro que viola validacao vai para invalidos com motivo do campo`() {
        val relatorio = processar("[${cliente("123")}, ${cliente("33333333333")}]")

        assertEquals(1, relatorio.resumo.totalAvaliados)
        val invalido = relatorio.invalidos.single()
        assertEquals("123", invalido.cpf)
        assertTrue(invalido.motivo.startsWith("cpf:"), invalido.motivo)
    }

    @Test
    fun `mes em formato invalido vai para invalidos`() {
        val ruim = cliente("44444444444").replace("2026-06", "junho")
        val relatorio = processar("[$ruim]")

        assertEquals(0, relatorio.resumo.totalAvaliados)
        assertEquals("mes deve estar no formato yyyy-MM", relatorio.invalidos.single().motivo)
    }

    @Test
    fun `campo desconhecido no registro e ignorado como na API`() {
        val relatorio = processar("[${cliente("55555555555", extra = ""","campoNovo": 1""")}]")

        assertEquals(1, relatorio.resumo.totalAvaliados)
        assertEquals(0, relatorio.resumo.totalInvalidos)
    }

    @Test
    fun `separa aprovados e reprovados, soma valor liberado e persiste so os avaliados`() {
        val relatorio = processar(
            "[${cliente("66666666666")}, ${cliente("77777777777", score = 300)}, ${cliente("1")}]"
        )

        assertEquals(1, relatorio.aprovados.size)
        assertEquals(1, relatorio.reprovados.size)
        assertEquals(1, relatorio.invalidos.size)
        assertEquals(0, relatorio.reprovados.single().valorLiberado.compareTo(BigDecimal.ZERO))
        assertEquals(
            0,
            relatorio.resumo.valorTotalLiberado.compareTo(relatorio.aprovados.single().valorLiberado)
        )
        verify(persistence, times(2)).persistir(qualquer(), qualquer())
    }

    @Test
    fun `cada item avaliado traz o id da decisao e a data, para cruzar com o banco`() {
        val relatorio = processar("[${cliente("88888888888")}, ${cliente("99999999999", score = 300)}]")

        val itens = relatorio.aprovados + relatorio.reprovados
        assertEquals(2, itens.map { it.decisaoId }.toSet().size)
        assertTrue(itens.all { !it.avaliadoEm.isAfter(relatorio.geradoEm) })
    }

    @Test
    fun `nome do arquivo do relatorio usa a data de geracao em UTC`() {
        assertEquals(
            "relatorio-20261118T093005Z.json",
            RelatorioLoteRunner.nomeDoArquivo(java.time.Instant.parse("2026-11-18T09:30:05Z"))
        )
    }

    @Test
    fun `lote vazio gera relatorio vazio`() {
        val relatorio = processar("[]")

        assertEquals(0, relatorio.resumo.totalRecebidos)
        assertEquals(0, relatorio.resumo.valorTotalLiberado.compareTo(BigDecimal.ZERO))
    }

    /** `any()` do Mockito devolve null, o que quebra parâmetros não-nulos do Kotlin. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> qualquer(): T {
        any<T>()
        return null as T
    }
}
