package com.creditengine.revolving_credit_rule_engine.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.Callable

/**
 * Métricas expostas em /actuator/prometheus (FR-010): volume total avaliado, taxa de
 * aprovação/reprovação, e latência de decisão (usada para acompanhar o alvo de FR-007/SC-003).
 */
@Component
class Metricas(registry: MeterRegistry) {

    private val avaliacoesTotal: Counter = Counter.builder("avaliacoes_total")
        .description("Total de clientes avaliados")
        .register(registry)

    private val avaliacoesAprovadas: Counter = Counter.builder("avaliacoes_aprovadas_total")
        .description("Total de avaliações aprovadas")
        .register(registry)

    private val avaliacoesReprovadas: Counter = Counter.builder("avaliacoes_reprovadas_total")
        .description("Total de avaliações reprovadas")
        .register(registry)

    private val latenciaDecisao: Timer = Timer.builder("avaliacao_decisao_latencia_seconds")
        .description("Latência de uma decisão individual de crédito rotativo")
        .register(registry)

    fun registrarAprovada() {
        avaliacoesTotal.increment()
        avaliacoesAprovadas.increment()
    }

    fun registrarReprovada() {
        avaliacoesTotal.increment()
        avaliacoesReprovadas.increment()
    }

    fun <T> medirLatencia(bloco: Callable<T>): T = latenciaDecisao.recordCallable(bloco)!!
}
