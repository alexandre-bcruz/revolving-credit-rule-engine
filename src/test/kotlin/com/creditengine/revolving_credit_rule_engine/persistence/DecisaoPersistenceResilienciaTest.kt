package com.creditengine.revolving_credit_rule_engine.persistence

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.ComportamentoGastoMensal
import com.creditengine.revolving_credit_rule_engine.domain.DecisaoAvaliacao
import com.creditengine.revolving_credit_rule_engine.domain.StatusAvaliacao
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Prova o comportamento de resiliência da escrita (FR-011): uma falha de banco é de fato
 * re-tentada (Retry externo ao CircuitBreaker), e com o circuito aberto a chamada é rejeitada
 * de imediato, sem retries inúteis. Em ambos os casos `persistir` nunca lança — a decisão já
 * respondida ao chamador não é afetada.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DecisaoPersistenceResilienciaTest {

    companion object {
        private const val INSTANCIA = "persistencia-decisao"

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

    @MockitoBean
    lateinit var clienteRepository: ClienteRepository

    @Autowired
    lateinit var persistenceService: DecisaoPersistenceService

    @Autowired
    lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @Autowired
    lateinit var retryRegistry: RetryRegistry

    private val cliente = Cliente(
        cpf = "77777777777",
        nome = "Teste Resiliencia",
        scoreCredito = 700,
        valorFaturaAtual = BigDecimal("100.00"),
        limiteDisponivel = BigDecimal("900.00"),
        numeroAtrasos = 0,
        comportamentoGastos = listOf(ComportamentoGastoMensal(YearMonth.of(2026, 7), BigDecimal("100.00")))
    )

    private val decisao = DecisaoAvaliacao(
        id = UUID.randomUUID(),
        clienteCpf = cliente.cpf,
        status = StatusAvaliacao.APROVADO,
        valorLiberado = BigDecimal("500.00"),
        motivos = emptyList(),
        avaliadoEm = Instant.now()
    )

    @BeforeEach
    fun setUp() {
        reset(clienteRepository)
        circuitBreakerRegistry.circuitBreaker(INSTANCIA).reset()
        doThrow(DataAccessResourceFailureException("banco indisponível"))
            .`when`(clienteRepository).save(any(ClienteEntity::class.java))
    }

    @Test
    fun `falha de banco e re-tentada ate o maximo de tentativas antes do fallback`() {
        val maxTentativas = retryRegistry.retry(INSTANCIA).retryConfig.maxAttempts

        persistenceService.persistir(cliente, decisao) // não deve lançar: fallback absorve

        verify(clienteRepository, times(maxTentativas)).save(any(ClienteEntity::class.java))
    }

    @Test
    fun `com circuito aberto a chamada e rejeitada sem tocar o banco e sem retries`() {
        circuitBreakerRegistry.circuitBreaker(INSTANCIA).transitionToForcedOpenState()
        val metricasRetry = retryRegistry.retry(INSTANCIA).metrics
        val semRetryAntes = metricasRetry.numberOfFailedCallsWithoutRetryAttempt

        persistenceService.persistir(cliente, decisao) // não deve lançar: fallback absorve

        verify(clienteRepository, never()).save(any(ClienteEntity::class.java))
        assertEquals(semRetryAntes + 1, metricasRetry.numberOfFailedCallsWithoutRetryAttempt)
    }
}
