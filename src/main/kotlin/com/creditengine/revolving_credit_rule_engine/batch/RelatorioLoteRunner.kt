package com.creditengine.revolving_credit_rule_engine.batch

import com.creditengine.revolving_credit_rule_engine.observability.LogMasking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Modo de execução em lote (FR-014): só faz I/O — lê `fixture-path`, delega a [ProcessadorLote]
 * e grava o relatório em `report-output-dir`, num arquivo novo por execução
 * (`relatorio-<UTC>.json`), para que uma execução não sobrescreva a anterior. Ativado só no profile `batch` (ver
 * application-batch.yaml, que desliga o servidor web para o processo encerrar sozinho).
 *
 * Usa o `JsonMapper` autoconfigurado pelo Spring Boot (o mesmo da API) — sem mapper próprio.
 *
 * Arquivo inexistente ou que não seja um array JSON é erro de configuração: falha rápido, sem
 * relatório. Registros individuais com problema NÃO derrubam o lote (ver [ProcessadorLote]).
 */
@Profile("batch")
@Component
class RelatorioLoteRunner(
    private val processador: ProcessadorLote,
    private val jsonMapper: JsonMapper,
    @Value("\${app.batch.fixture-path}") private val fixturePath: String,
    @Value("\${app.batch.report-output-dir}") private val reportOutputDir: String
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(RelatorioLoteRunner::class.java)

    override fun run(vararg args: String) {
        val raiz = jsonMapper.readTree(File(fixturePath))
        require(raiz.isArray) { "Arquivo de lote $fixturePath deve conter um array JSON de clientes" }

        val relatorio = processador.processar(raiz.values().toList())

        val outputFile = File(reportOutputDir, nomeDoArquivo(relatorio.geradoEm))
        outputFile.parentFile?.mkdirs()
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, relatorio)

        with(relatorio.resumo) {
            log.info(
                "Relatório em lote gerado: recebidos={} aprovados={} reprovados={} invalidos={} valorTotalLiberado={} -> {}",
                totalRecebidos, totalAprovados, totalReprovados, totalInvalidos,
                LogMasking.mascararValor(valorTotalLiberado), outputFile.path
            )
        }
    }

    companion object {
        private val FORMATO_NOME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

        /** Ex.: `relatorio-20261118T093000Z.json`. Ordena cronologicamente e é seguro em qualquer sistema de arquivos. */
        internal fun nomeDoArquivo(geradoEm: Instant): String = "relatorio-${FORMATO_NOME.format(geradoEm)}.json"
    }
}
