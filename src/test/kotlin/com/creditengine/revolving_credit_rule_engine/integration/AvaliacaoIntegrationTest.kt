package com.creditengine.revolving_credit_rule_engine.integration

import com.creditengine.revolving_credit_rule_engine.persistence.ClienteRepository
import com.creditengine.revolving_credit_rule_engine.persistence.DecisaoRepository
import com.creditengine.revolving_credit_rule_engine.persistence.ResultadoRegraRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercita a cadeia completa API -> MotorDeAvaliacao -> Postgres real (Testcontainers), indo além
 * do contrato HTTP (AvaliacaoControllerTest) ao verificar diretamente que cliente e decisão foram
 * persistidos e que TODOS os motivos das regras (não só a que reprovou) chegam ao banco — FR-006.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AvaliacaoIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var clienteRepository: ClienteRepository

    @Autowired
    lateinit var decisaoRepository: DecisaoRepository

    @Autowired
    lateinit var resultadoRegraRepository: ResultadoRegraRepository

    @Test
    fun `avaliacao aprovada e persistida com cliente e todos os motivos das regras`() {
        val cpf = "44444444444"
        mockMvc.perform(
            post("/api/v1/avaliacoes")
                .contentType("application/json")
                .content(
                    """
                    {"clientes": [{
                      "cpf": "$cpf",
                      "nome": "Joao Pereira",
                      "scoreCredito": 800,
                      "valorFaturaAtual": 200.0,
                      "limiteDisponivel": 1800.0,
                      "numeroAtrasos": 0,
                      "comportamentoGastos": [
                        {"mes": "2026-06", "valor": 500.0},
                        {"mes": "2026-07", "valor": 520.0}
                      ]
                    }]}
                    """.trimIndent()
                )
        ).andExpect(status().isOk)

        val clientePersistido = clienteRepository.findById(cpf)
        assertTrue(clientePersistido.isPresent, "cliente deveria ter sido persistido")
        assertEquals("Joao Pereira", clientePersistido.get().nome)

        val decisoes = decisaoRepository.findAll().filter { it.clienteCpf == cpf }
        assertEquals(1, decisoes.size)
        val decisao = decisoes.first()
        assertEquals("APROVADO", decisao.status)

        // motivos vira uma linha por regra em resultado_regra — todas as 4 regras de negócio
        // devem aparecer, não só o resultado consolidado (Princípio III / FR-006).
        val nomesRegras = resultadoRegraRepository.findByDecisaoId(decisao.id).map { it.nomeRegra }
        assertTrue(nomesRegras.contains("RegraScoreMinimo"))
        assertTrue(nomesRegras.contains("RegraAtrasosMaximos"))
        assertTrue(nomesRegras.contains("RegraFaturaSobreLimite"))
        assertTrue(nomesRegras.contains("RegraTendenciaDeGastos"))
    }

    @Test
    fun `avaliacao reprovada nao libera valor mas ainda e persistida com motivo`() {
        val cpf = "55555555555"
        mockMvc.perform(
            post("/api/v1/avaliacoes")
                .contentType("application/json")
                .content(
                    """
                    {"clientes": [{
                      "cpf": "$cpf",
                      "nome": "Ana Lima",
                      "scoreCredito": 500,
                      "valorFaturaAtual": 950.0,
                      "limiteDisponivel": 50.0,
                      "numeroAtrasos": 5,
                      "comportamentoGastos": [{"mes": "2026-07", "valor": 500.0}]
                    }]}
                    """.trimIndent()
                )
        ).andExpect(status().isOk)

        val decisoes = decisaoRepository.findAll().filter { it.clienteCpf == cpf }
        assertEquals(1, decisoes.size)
        val decisao = decisoes.first()
        assertEquals("REPROVADO", decisao.status)
        assertEquals(0, decisao.valorLiberado.compareTo(java.math.BigDecimal.ZERO))
        assertTrue(resultadoRegraRepository.findByDecisaoId(decisao.id).any { !it.aprovada })
    }
}
