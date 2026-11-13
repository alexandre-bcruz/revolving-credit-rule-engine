package com.creditengine.revolving_credit_rule_engine.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ClienteRepository : JpaRepository<ClienteEntity, String>
