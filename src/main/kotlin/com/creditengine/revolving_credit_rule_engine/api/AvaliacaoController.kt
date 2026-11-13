package com.creditengine.revolving_credit_rule_engine.api

import com.creditengine.revolving_credit_rule_engine.api.dto.AvaliacaoRequestDto
import com.creditengine.revolving_credit_rule_engine.api.dto.AvaliacaoResponseDto
import com.creditengine.revolving_credit_rule_engine.api.dto.ClienteInputDto
import com.creditengine.revolving_credit_rule_engine.api.dto.ClienteInputMapper
import com.creditengine.revolving_credit_rule_engine.api.dto.ClienteInvalidoDto
import com.creditengine.revolving_credit_rule_engine.api.dto.DecisaoAvaliacaoDto
import com.creditengine.revolving_credit_rule_engine.api.dto.MotivoRegraDto
import com.creditengine.revolving_credit_rule_engine.api.dto.ResumoDto
import com.creditengine.revolving_credit_rule_engine.domain.StatusAvaliacao
import com.creditengine.revolving_credit_rule_engine.engine.MotorDeAvaliacao
import com.creditengine.revolving_credit_rule_engine.observability.LogMasking
import com.creditengine.revolving_credit_rule_engine.observability.Metricas
import com.creditengine.revolving_credit_rule_engine.persistence.DecisaoPersistenceService
import jakarta.validation.Validator
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.Callable

/**
 * Único ponto de entrada HTTP do motor (FR-013). Cada cliente inválido do lote vira uma entrada
 * em `invalidos` em vez de derrubar a requisição inteira (FR-012) — um lote vazio é uma resposta
 * 200 válida com listas vazias (edge case "lote vazio").
 */
@RestController
class AvaliacaoController(
    private val motor: MotorDeAvaliacao,
    private val persistenceService: DecisaoPersistenceService,
    private val metricas: Metricas,
    private val validator: Validator
) {
    private val log = LoggerFactory.getLogger(AvaliacaoController::class.java)

    @PostMapping("/api/v1/avaliacoes")
    fun avaliar(@RequestBody request: AvaliacaoRequestDto): ResponseEntity<AvaliacaoResponseDto> {
        val invalidos = mutableListOf<ClienteInvalidoDto>()
        val decisoes = mutableListOf<DecisaoAvaliacaoDto>()

        for (input in request.clientes) {
            try {
                decisoes.add(avaliarUmCliente(input))
            } catch (ex: Exception) {
                invalidos.add(ClienteInvalidoDto(cpf = input.cpf, motivo = ex.message ?: "dados inválidos"))
            }
        }

        val resumo = ResumoDto(
            totalAvaliados = decisoes.size,
            totalAprovados = decisoes.count { it.status == StatusAvaliacao.APROVADO.name },
            totalReprovados = decisoes.count { it.status == StatusAvaliacao.REPROVADO.name }
        )

        return ResponseEntity.ok(AvaliacaoResponseDto(resumo, decisoes, invalidos))
    }

    private fun avaliarUmCliente(input: ClienteInputDto): DecisaoAvaliacaoDto {
        val violacoes = validator.validate(input)
        if (violacoes.isNotEmpty()) {
            throw IllegalArgumentException(violacoes.joinToString("; ") { "${it.propertyPath}: ${it.message}" })
        }

        val cliente = ClienteInputMapper.paraDominio(input)
        val decisao = metricas.medirLatencia(Callable { motor.avaliar(cliente) })

        if (decisao.status == StatusAvaliacao.APROVADO) metricas.registrarAprovada() else metricas.registrarReprovada()

        // Falha de persistência é absorvida internamente (retry/circuit breaker + fallback) —
        // esta chamada nunca lança, então a resposta abaixo nunca é bloqueada por ela (FR-011).
        persistenceService.persistir(cliente, decisao)

        log.info(
            "Cliente cpf={} avaliado: status={} valorLiberado={}",
            LogMasking.mascararCpf(cliente.cpf),
            decisao.status,
            LogMasking.mascararValor(decisao.valorLiberado)
        )

        return DecisaoAvaliacaoDto(
            cpf = decisao.clienteCpf,
            status = decisao.status.name,
            valorLiberado = decisao.valorLiberado,
            motivos = decisao.motivos.map { MotivoRegraDto(it.nomeRegra, it.aprovada, it.detalhe) },
            avaliadoEm = decisao.avaliadoEm
        )
    }
}
