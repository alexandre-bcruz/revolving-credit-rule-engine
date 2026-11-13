package com.creditengine.revolving_credit_rule_engine.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DecisaoRepository : JpaRepository<DecisaoEntity, UUID>
