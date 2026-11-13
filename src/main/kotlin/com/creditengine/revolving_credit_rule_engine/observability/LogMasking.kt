package com.creditengine.revolving_credit_rule_engine.observability

import java.math.BigDecimal

/**
 * Mascaramento de dados sensíveis para logs (FR-009 / SC-005). Nenhum log da aplicação deve
 * conter CPF ou valor financeiro completo em texto claro — usar estas funções em qualquer
 * ponto de log que referencie dados de [com.creditengine.revolving_credit_rule_engine.domain.Cliente].
 */
object LogMasking {

    fun mascararCpf(cpf: String): String {
        val digitos = cpf.filter { it.isDigit() }
        if (digitos.length <= 2) return "*".repeat(digitos.length)
        return "*".repeat(digitos.length - 2) + digitos.takeLast(2)
    }

    fun mascararValor(valor: BigDecimal): String = "R$ ****"
}
