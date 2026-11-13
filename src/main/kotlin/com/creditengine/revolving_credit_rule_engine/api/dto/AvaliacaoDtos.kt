package com.creditengine.revolving_credit_rule_engine.api.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.math.BigDecimal
import java.time.Instant

data class ComportamentoGastoMensalDto(
    @field:NotBlank(message = "mes é obrigatório")
    val mes: String,

    @field:NotNull(message = "valor é obrigatório")
    @field:DecimalMin(value = "0", inclusive = true, message = "valor deve ser maior ou igual a 0")
    val valor: BigDecimal
)

data class ClienteInputDto(
    @field:NotBlank(message = "cpf é obrigatório")
    @field:Pattern(regexp = "\\d{11}", message = "cpf deve ter 11 dígitos numéricos")
    val cpf: String?,

    @field:NotBlank(message = "nome é obrigatório")
    val nome: String?,

    @field:NotNull(message = "scoreCredito é obrigatório")
    @field:Min(value = 0, message = "scoreCredito deve ser maior ou igual a 0")
    @field:Max(value = 1000, message = "scoreCredito deve ser menor ou igual a 1000")
    val scoreCredito: Int?,

    @field:NotNull(message = "valorFaturaAtual é obrigatório")
    @field:DecimalMin(value = "0", inclusive = true, message = "valorFaturaAtual deve ser maior ou igual a 0")
    val valorFaturaAtual: BigDecimal?,

    @field:NotNull(message = "limiteDisponivel é obrigatório")
    @field:DecimalMin(value = "0", inclusive = true, message = "limiteDisponivel deve ser maior ou igual a 0")
    val limiteDisponivel: BigDecimal?,

    @field:NotNull(message = "numeroAtrasos é obrigatório")
    @field:Min(value = 0, message = "numeroAtrasos deve ser maior ou igual a 0")
    val numeroAtrasos: Int?,

    @field:NotEmpty(message = "comportamentoGastos deve ter ao menos 1 item")
    val comportamentoGastos: List<@Valid ComportamentoGastoMensalDto>?
)

data class AvaliacaoRequestDto(
    @field:NotNull
    val clientes: List<@Valid ClienteInputDto>
)

data class MotivoRegraDto(
    val nomeRegra: String,
    val aprovada: Boolean,
    val detalhe: String
)

data class DecisaoAvaliacaoDto(
    val cpf: String,
    val status: String,
    val valorLiberado: BigDecimal,
    val motivos: List<MotivoRegraDto>,
    val avaliadoEm: Instant
)

data class ClienteInvalidoDto(
    val cpf: String?,
    val motivo: String
)

data class ResumoDto(
    val totalAvaliados: Int,
    val totalAprovados: Int,
    val totalReprovados: Int
)

data class AvaliacaoResponseDto(
    val resumo: ResumoDto,
    val decisoes: List<DecisaoAvaliacaoDto>,
    val invalidos: List<ClienteInvalidoDto>
)
