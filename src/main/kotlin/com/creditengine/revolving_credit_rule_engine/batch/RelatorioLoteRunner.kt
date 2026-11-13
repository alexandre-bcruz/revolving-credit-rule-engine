package com.creditengine.revolving_credit_rule_engine.batch

import com.creditengine.revolving_credit_rule_engine.api.dto.ClienteInputDto
import com.creditengine.revolving_credit_rule_engine.api.dto.ClienteInputMapper
import com.creditengine.revolving_credit_rule_engine.engine.MotorDeAvaliacao
import com.creditengine.revolving_credit_rule_engine.observability.LogMasking
import com.creditengine.revolving_credit_rule_engine.persistence.DecisaoPersistenceService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.File
import java.math.BigDecimal

/**
 * Modo de execução em lote (FR-014): reaproveita o mesmo MotorDeAvaliacao da API para processar
 * uma lista fictícia de clientes e produzir o relatório consolidado (FR-005) sem chamadas HTTP
 * manuais. Ativado só no profile `batch` (ver application-batch.yaml, que desliga o servidor web
 * para o processo encerrar sozinho ao terminar).
 */
@Profile("batch")
@Component
class RelatorioLoteRunner(
    private val motor: MotorDeAvaliacao,
    private val persistenceService: DecisaoPersistenceService,
    @Value("\${app.batch.fixture-path}") private val fixturePath: String,
    @Value("\${app.batch.report-output-path}") private val reportOutputPath: String
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(RelatorioLoteRunner::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    override fun run(vararg args: String) {
        val clientesInput: List<ClienteInputDto> = objectMapper.readValue(
            File(fixturePath),
            objectMapper.typeFactory.constructCollectionType(List::class.java, ClienteInputDto::class.java)
        )

        val decisoes = clientesInput.map { input ->
            val cliente = ClienteInputMapper.paraDominio(input)
            val decisao = motor.avaliar(cliente)
            persistenceService.persistir(cliente, decisao)
            mapOf(
                "cpf" to decisao.clienteCpf,
                "status" to decisao.status.name,
                "valorLiberado" to decisao.valorLiberado,
                "motivos" to decisao.motivos
            )
        }

        val relatorio = mapOf(
            "resumo" to mapOf(
                "totalAvaliados" to decisoes.size,
                "totalAprovados" to decisoes.count { it["status"] == "APROVADO" },
                "totalReprovados" to decisoes.count { it["status"] == "REPROVADO" }
            ),
            "decisoes" to decisoes
        )

        val outputFile = File(reportOutputPath)
        outputFile.parentFile?.mkdirs()
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, relatorio)

        log.info("Relatório em lote gerado: {} clientes avaliados -> {}", decisoes.size, reportOutputPath)
        decisoes.forEach {
            log.info(
                "cpf={} status={} valorLiberado={}",
                LogMasking.mascararCpf(it["cpf"] as String),
                it["status"],
                LogMasking.mascararValor(it["valorLiberado"] as BigDecimal)
            )
        }
    }
}
