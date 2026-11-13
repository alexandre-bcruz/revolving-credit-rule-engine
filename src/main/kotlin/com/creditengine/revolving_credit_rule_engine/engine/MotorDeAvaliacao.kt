package com.creditengine.revolving_credit_rule_engine.engine

import com.creditengine.revolving_credit_rule_engine.domain.Cliente
import com.creditengine.revolving_credit_rule_engine.domain.DecisaoAvaliacao
import com.creditengine.revolving_credit_rule_engine.domain.RegraCredito
import com.creditengine.revolving_credit_rule_engine.domain.StatusAvaliacao
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * Orquestrador central. Nunca referencia uma regra concreta — apenas itera sobre as
 * implementações de [RegraCredito] que o Spring injetar aqui (Constitution Principle I).
 * Todas as regras são sempre avaliadas (nunca curto-circuita) para que o relatório carregue
 * o motivo de cada uma, mesmo quando a decisão final já está determinada (Principle III /
 * research.md §1).
 */
@Component
class MotorDeAvaliacao(private val regras: List<RegraCredito>) {

    fun avaliar(cliente: Cliente): DecisaoAvaliacao {
        val motivos = regras.map { it.avaliar(cliente) }
        val aprovado = motivos.all { it.aprovada }
        val valorLiberado = if (aprovado) calcularValorLiberado(cliente) else BigDecimal.ZERO

        return DecisaoAvaliacao(
            id = UUID.randomUUID(),
            clienteCpf = cliente.cpf,
            status = if (aprovado) StatusAvaliacao.APROVADO else StatusAvaliacao.REPROVADO,
            valorLiberado = valorLiberado,
            motivos = motivos,
            avaliadoEm = Instant.now()
        )
    }

    /**
     * valorLiberado = limiteDisponivel * fatorScore * fatorFolga (ver data-model.md).
     * fatorScore cresce com o score de crédito; fatorFolga cresce quanto menor a fatura
     * atual em relação ao total (fatura + limite) — nunca excede o limiteDisponivel.
     */
    private fun calcularValorLiberado(cliente: Cliente): BigDecimal {
        if (cliente.limiteDisponivel <= BigDecimal.ZERO) return BigDecimal.ZERO

        val fatorScore = BigDecimal(cliente.scoreCredito)
            .divide(BigDecimal(1000), 6, RoundingMode.HALF_UP)
            .min(BigDecimal.ONE)

        val faturaMaisLimite = cliente.valorFaturaAtual.add(cliente.limiteDisponivel)
        val fatorFolga = if (faturaMaisLimite <= BigDecimal.ZERO) {
            BigDecimal.ONE
        } else {
            BigDecimal.ONE.subtract(
                cliente.valorFaturaAtual.divide(faturaMaisLimite, 6, RoundingMode.HALF_UP)
            )
        }

        return cliente.limiteDisponivel
            .multiply(fatorScore)
            .multiply(fatorFolga)
            .setScale(2, RoundingMode.HALF_UP)
    }
}
