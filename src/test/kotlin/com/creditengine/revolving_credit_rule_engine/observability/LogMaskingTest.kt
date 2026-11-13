package com.creditengine.revolving_credit_rule_engine.observability

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LogMaskingTest {

    @Test
    fun `mascara todos os digitos do cpf exceto os dois ultimos`() {
        val mascarado = LogMasking.mascararCpf("12345678901")
        assertEquals("*********01", mascarado)
        assertFalse(mascarado.contains("123456789"))
    }

    @Test
    fun `mascara cpf com formatacao mantendo apenas digitos`() {
        val mascarado = LogMasking.mascararCpf("123.456.789-01")
        assertEquals("*********01", mascarado)
    }

    @Test
    fun `mascara valor financeiro sem expor o numero`() {
        val mascarado = LogMasking.mascararValor(BigDecimal("1999.90"))
        assertFalse(mascarado.contains("1999"))
        assertFalse(mascarado.contains("90"))
    }
}
