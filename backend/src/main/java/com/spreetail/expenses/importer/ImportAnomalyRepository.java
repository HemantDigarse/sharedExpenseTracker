package com.spreetail.expenses.importer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportAnomalyRepository extends JpaRepository<ImportAnomaly, Long> {

    List<ImportAnomaly> findBySessionId(Long sessionId);

    List<ImportAnomaly> findBySessionIdAndUserDecision(Long sessionId, UserDecision decision);

    long countBySessionIdAndUserDecision(Long sessionId, UserDecision decision);
}
