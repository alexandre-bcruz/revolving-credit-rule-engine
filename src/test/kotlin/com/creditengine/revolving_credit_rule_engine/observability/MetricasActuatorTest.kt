package com.creditengine.revolving_credit_rule_engine.observability

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Confirma que /actuator/prometheus expõe as métricas de volume/aprovação/reprovação/latência
 * exigidas por FR-010, depois de pelo menos uma avaliação real ter passado pela API.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MetricasActuatorTest {

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

    @Test
    fun `prometheus expoe contadores de volume aprovacao reprovacao e latencia`() {
        mockMvc.perform(
            post("/api/v1/avaliacoes")
                .contentType("application/json")
                .content(
                    """{"clientes": [{
                      "cpf": "66666666666", "nome": "Teste Metricas", "scoreCredito": 700,
                      "valorFaturaAtual": 100.0, "limiteDisponivel": 900.0, "numeroAtrasos": 0,
                      "comportamentoGastos": [{"mes": "2026-07", "valor": 500.0}]
                    }]}"""
                )
        ).andExpect(status().isOk)

        val resultado = mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk)
            .andReturn()

        val corpo = resultado.response.contentAsString
        listOf(
            "avaliacoes_total",
            "avaliacoes_aprovadas_total",
            "avaliacoes_reprovadas_total",
            "avaliacao_decisao_latencia_seconds"
        ).forEach { metrica ->
            org.junit.jupiter.api.Assertions.assertTrue(
                corpo.contains(metrica),
                "esperava a métrica '$metrica' exposta em /actuator/prometheus"
            )
        }
    }
}
