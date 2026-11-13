package com.creditengine.revolving_credit_rule_engine.engine

import com.creditengine.revolving_credit_rule_engine.domain.RegraCredito
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import kotlin.test.assertTrue

@Configuration
@ComponentScan(
    basePackages = [
        "com.creditengine.revolving_credit_rule_engine.domain",
        "com.creditengine.revolving_credit_rule_engine.engine"
    ]
)
private class RegrasScanConfig

/**
 * Prova de extensibilidade (User Story 2 / SC-002): sobe um contexto Spring mínimo (sem banco)
 * escaneando apenas domain+engine e confirma que MotorDeAvaliacao recebe RegraLimiteMinimoAbsoluto
 * — uma regra adicionada DEPOIS do MVP — automaticamente via injeção de `List<RegraCredito>`,
 * sem que `engine/MotorDeAvaliacao.kt` tenha sido alterado para conhecê-la.
 */
class MotorDeAvaliacaoSpringWiringTest {

    @Test
    fun `motor descobre automaticamente regras adicionadas depois do MVP`() {
        AnnotationConfigApplicationContext(RegrasScanConfig::class.java).use { ctx ->
            val motor = ctx.getBean(MotorDeAvaliacao::class.java)
            val regras = ctx.getBeansOfType(RegraCredito::class.java)

            assertTrue(
                regras.size >= 5,
                "esperava pelo menos as 4 regras do MVP + RegraLimiteMinimoAbsoluto, achou ${regras.size}"
            )
            assertTrue(regras.values.any { it.javaClass.simpleName == "RegraLimiteMinimoAbsoluto" })
            assertTrue(motor.javaClass == MotorDeAvaliacao::class.java)
        }
    }
}
