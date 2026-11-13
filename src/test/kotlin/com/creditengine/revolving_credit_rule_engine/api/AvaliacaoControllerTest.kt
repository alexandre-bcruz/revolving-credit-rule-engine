package com.creditengine.revolving_credit_rule_engine.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Contrato HTTP de POST /api/v1/avaliacoes (contracts/openapi.yaml), exercitando as regras
 * reais registradas via Spring (não dublês) contra um Postgres real via Testcontainers.
 *
 * Nota técnica: `@WebMvcTest` (slice mais leve, sem banco) não faz component scan do pacote da
 * aplicação nesta versão do Spring Boot usada no ambiente do case — decisão documentada em
 * research.md: usamos `@SpringBootTest` + `@AutoConfigureMockMvc` com Testcontainers em vez do
 * slice para o contrato HTTP, aceitando o custo extra de subir um Postgres efêmero.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AvaliacaoControllerTest {

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

    private fun clienteInputJson(cpf: String, score: Int): String = """
        {
          "cpf": "$cpf",
          "nome": "Maria Souza",
          "scoreCredito": $score,
          "valorFaturaAtual": 500.0,
          "limiteDisponivel": 1500.0,
          "numeroAtrasos": 0,
          "comportamentoGastos": [{"mes": "2026-07", "valor": 500.0}]
        }
    """.trimIndent()

    @Test
    fun `retorna aprovado quando cliente atende as regras reais`() {
        mockMvc.perform(
            post("/api/v1/avaliacoes")
                .contentType("application/json")
                .content("""{"clientes": [${clienteInputJson("11111111111", 700)}]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resumo.totalAvaliados").value(1))
            .andExpect(jsonPath("$.resumo.totalAprovados").value(1))
            .andExpect(jsonPath("$.decisoes[0].status").value("APROVADO"))
            .andExpect(jsonPath("$.decisoes[0].motivos").isArray)
            .andExpect(jsonPath("$.invalidos").isEmpty)
    }

    @Test
    fun `retorna reprovado quando score esta abaixo do minimo`() {
        mockMvc.perform(
            post("/api/v1/avaliacoes")
                .contentType("application/json")
                .content("""{"clientes": [${clienteInputJson("22222222222", 400)}]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.decisoes[0].status").value("REPROVADO"))
            .andExpect(jsonPath("$.decisoes[0].valorLiberado").value(0.0))
    }

    @Test
    fun `retorna cliente invalido em vez de falhar o lote inteiro`() {
        mockMvc.perform(
            post("/api/v1/avaliacoes")
                .contentType("application/json")
                .content("""{"clientes": [{"cpf": "33333333333", "nome": "Sem Score"}]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.invalidos[0].cpf").value("33333333333"))
            .andExpect(jsonPath("$.decisoes").isEmpty)
    }

    @Test
    fun `lote vazio retorna relatorio vazio valido`() {
        mockMvc.perform(
            post("/api/v1/avaliacoes")
                .contentType("application/json")
                .content("""{"clientes": []}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resumo.totalAvaliados").value(0))
            .andExpect(jsonPath("$.decisoes").isEmpty)
            .andExpect(jsonPath("$.invalidos").isEmpty)
    }

    @Test
    fun `corpo malformado sem campo clientes retorna 400`() {
        mockMvc.perform(
            post("/api/v1/avaliacoes")
                .contentType("application/json")
                .content("""{}""")
        )
            .andExpect(status().isBadRequest)
    }
}
