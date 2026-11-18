package com.creditengine.revolving_credit_rule_engine.batch

import com.creditengine.revolving_credit_rule_engine.api.dto.ClienteInputDto
import com.creditengine.revolving_credit_rule_engine.api.dto.ClienteInputMapper
import com.creditengine.revolving_credit_rule_engine.api.dto.MotivoRegraDto
import com.creditengine.revolving_credit_rule_engine.domain.StatusAvaliacao
import com.creditengine.revolving_credit_rule_engine.engine.MotorDeAvaliacao
import com.creditengine.revolving_credit_rule_engine.observability.LogMasking
import com.creditengine.revolving_credit_rule_engine.persistence.DecisaoPersistenceService
import tools.jackson.databind.JsonNode
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.json.JsonMapper
import jakarta.validation.Validator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ResumoLote(
    val totalRecebidos: Int,
    val totalAvaliados: Int,
    val totalAprovados: Int,
    val totalReprovados: Int,
    val totalInvalidos: Int,
    val valorTotalLiberado: BigDecimal
)

data class ItemAvaliadoLote(
    /** Mesmo id gravado em `decisao_avaliacao` — liga o item do relatório ao registro de auditoria. */
    val decisaoId: UUID,
    val avaliadoEm: Instant,
    val cpf: String,
    val status: String,
    val valorLiberado: BigDecimal,
    val motivos: List<MotivoRegraDto>
)

data class ItemInvalidoLote(
    /** Posição (0-based) do registro no arquivo de entrada — permite localizá-lo mesmo sem CPF legível. */
    val indice: Int,
    val cpf: String?,
    val motivo: String
)

data class RelatorioLote(
    val geradoEm: Instant,
    val resumo: ResumoLote,
    val aprovados: List<ItemAvaliadoLote>,
    val reprovados: List<ItemAvaliadoLote>,
    val invalidos: List<ItemInvalidoLote>
)

/**
 * Núcleo do modo em lote (FR-014), separado do [RelatorioLoteRunner] para ser testável sem
 * arquivo e sem profile. Reaproveita o mesmo motor, a mesma validação e o mesmo `JsonMapper` da
 * API (o bean autoconfigurado pelo Spring Boot), então o lote lê JSON exatamente como o HTTP.
 *
 * Isolamento por registro (FR-012): cada item do array é desserializado individualmente
 * (`treeToValue`), então um registro com tipo errado (ex.: `"scoreCredito": "abc"`) vira uma
 * entrada em `invalidos` em vez de abortar o lote inteiro. Falhas de persistência não chegam
 * aqui: [DecisaoPersistenceService] as absorve com retry/circuit breaker + fallback (FR-011).
 */
@Component
class ProcessadorLote(
    private val motor: MotorDeAvaliacao,
    private val persistenceService: DecisaoPersistenceService,
    private val validator: Validator,
    private val jsonMapper: JsonMapper
) {
    private val log = LoggerFactory.getLogger(ProcessadorLote::class.java)

    fun processar(registros: List<JsonNode>): RelatorioLote {
        val aprovados = mutableListOf<ItemAvaliadoLote>()
        val reprovados = mutableListOf<ItemAvaliadoLote>()
        val invalidos = mutableListOf<ItemInvalidoLote>()

        registros.forEachIndexed { indice, node ->
            val cpfBruto = node.get("cpf")?.takeIf { it.isString }?.stringValue()
            try {
                val input = jsonMapper.treeToValue(node, ClienteInputDto::class.java)

                val violacoes = validator.validate(input)
                if (violacoes.isNotEmpty()) {
                    invalidos += ItemInvalidoLote(
                        indice = indice,
                        cpf = input.cpf,
                        motivo = violacoes
                            .map { "${it.propertyPath}: ${it.message}" }
                            .sorted()
                            .joinToString("; ")
                    )
                    return@forEachIndexed
                }

                val cliente = ClienteInputMapper.paraDominio(input)
                val decisao = motor.avaliar(cliente)
                persistenceService.persistir(cliente, decisao)

                val item = ItemAvaliadoLote(
                    decisaoId = decisao.id,
                    avaliadoEm = decisao.avaliadoEm,
                    cpf = decisao.clienteCpf,
                    status = decisao.status.name,
                    valorLiberado = decisao.valorLiberado,
                    motivos = decisao.motivos.map { MotivoRegraDto(it.nomeRegra, it.aprovada, it.detalhe) }
                )
                if (decisao.status == StatusAvaliacao.APROVADO) aprovados += item else reprovados += item
            } catch (ex: Exception) {
                invalidos += ItemInvalidoLote(indice = indice, cpf = cpfBruto, motivo = motivoLegivel(ex))
                log.warn(
                    "Registro {} (cpf={}) ignorado no lote: {}",
                    indice,
                    LogMasking.mascararCpf(cpfBruto ?: ""),
                    ex.javaClass.simpleName
                )
                log.debug("Detalhe da falha no registro {}", indice, ex)
            }
        }

        return RelatorioLote(
            geradoEm = Instant.now(),
            resumo = ResumoLote(
                totalRecebidos = registros.size,
                totalAvaliados = aprovados.size + reprovados.size,
                totalAprovados = aprovados.size,
                totalReprovados = reprovados.size,
                totalInvalidos = invalidos.size,
                valorTotalLiberado = aprovados.fold(BigDecimal.ZERO) { acc, it -> acc + it.valorLiberado }
            ),
            aprovados = aprovados,
            reprovados = reprovados,
            invalidos = invalidos
        )
    }

    /**
     * Mensagem curta para o relatório. Erros de desserialização do Jackson trazem trechos do
     * input e detalhes internos na mensagem original — aqui fica só o campo e o tipo do erro.
     */
    private fun motivoLegivel(ex: Exception): String = when (ex) {
        is MismatchedInputException ->
            "formato inválido no campo '${ex.path.joinToString(".") { it.propertyName ?: "[${it.index}]" }}'"
        is java.time.format.DateTimeParseException -> "mes deve estar no formato yyyy-MM"
        else -> ex.message ?: ex.javaClass.simpleName
    }
}
