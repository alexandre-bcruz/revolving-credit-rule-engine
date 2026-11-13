package com.creditengine.revolving_credit_rule_engine.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ResultadoRegraRepository : JpaRepository<ResultadoRegraEntity, Long> {
    fun findByDecisaoId(decisaoId: UUID): List<ResultadoRegraEntity>
}
