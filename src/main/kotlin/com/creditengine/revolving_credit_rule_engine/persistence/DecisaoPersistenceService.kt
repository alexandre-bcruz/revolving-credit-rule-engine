package com.creditengine.revolving_credit_rule_engine.persistence

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.DecisaoAvaliacao
import com.creditengine.revolving_credit_rule_engine.observability.LogMasking
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

/**
 * Escreve cliente + decisão no Postgres. A decisão em si já foi calculada em memória pelo
 * MotorDeAvaliacao antes de chegar aqui — uma falha de persistência (após retry/circuit
 * breaker) NUNCA impede a resposta já dada ao chamador; apenas fica registrada no log de erro
 * (FR-011 / research.md §3).
 *
 * `@Transactional` garante que cliente, decisão e os `resultado_regra` são gravados atomicamente
 * — sem ela, uma falha entre os `save`s deixaria uma decisão órfã, sem nenhuma regra associada.
 *
 * Ordem dos aspectos (padrão do Resilience4j): Retry(CircuitBreaker(Transactional(persistir))).
 * O `fallbackMethod` fica SÓ no `@Retry` (o externo): se o CircuitBreaker também tivesse fallback,
 * ele absorveria a exceção antes do Retry vê-la e nenhuma nova tentativa aconteceria. Cada
 * tentativa abre uma transação nova. Com o circuito aberto, `CallNotPermittedException` é
 * ignorada pelo Retry (ver application.yaml) e cai direto no fallback, sem esperas inúteis —
 * comportamento coberto por DecisaoPersistenceResilienciaTest.
 *
 * `comportamentoGastos` é gravado como `jsonb` usando o `JsonMapper` autoconfigurado pelo Spring
 * Boot — o mesmo da API e do lote, para haver uma única configuração de JSON no serviço.
 */
@Service
class DecisaoPersistenceService(
    private val clienteRepository: ClienteRepository,
    private val decisaoRepository: DecisaoRepository,
    private val resultadoRegraRepository: ResultadoRegraRepository,
    private val jsonMapper: JsonMapper
) {
    private val log = LoggerFactory.getLogger(DecisaoPersistenceService::class.java)

    @Retry(name = "persistencia-decisao", fallbackMethod = "aoFalharPersistencia")
    @CircuitBreaker(name = "persistencia-decisao")
    @Transactional
    fun persistir(cliente: Cliente, decisao: DecisaoAvaliacao) {
        clienteRepository.save(
            ClienteEntity(
                cpf = cliente.cpf,
                nome = cliente.nome,
                scoreCredito = cliente.scoreCredito,
                valorFaturaAtual = cliente.valorFaturaAtual,
                limiteDisponivel = cliente.limiteDisponivel,
                numeroAtrasos = cliente.numeroAtrasos,
                comportamentoGastos = jsonMapper.writeValueAsString(cliente.comportamentoGastos)
            )
        )
        decisaoRepository.save(
            DecisaoEntity(
                id = decisao.id,
                clienteCpf = decisao.clienteCpf,
                status = decisao.status.name,
                valorLiberado = decisao.valorLiberado,
                avaliadoEm = decisao.avaliadoEm
            )
        )
        resultadoRegraRepository.saveAll(
            decisao.motivos.map {
                ResultadoRegraEntity(
                    decisaoId = decisao.id,
                    nomeRegra = it.nomeRegra,
                    aprovada = it.aprovada,
                    detalhe = it.detalhe
                )
            }
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun aoFalharPersistencia(cliente: Cliente, decisao: DecisaoAvaliacao, ex: Throwable) {
        log.error(
            "Falha ao persistir decisão {} do cliente cpf={} após retries/circuit breaker — " +
                "a decisão já foi respondida ao chamador e não é afetada; ficará ausente do " +
                "histórico persistido até nova tentativa. causa={}",
            decisao.id,
            LogMasking.mascararCpf(cliente.cpf),
            ex.message
        )
    }
}
