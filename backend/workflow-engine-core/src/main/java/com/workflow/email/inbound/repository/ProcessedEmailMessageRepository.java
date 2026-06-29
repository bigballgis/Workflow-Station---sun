package com.workflow.email.inbound.repository;

import com.workflow.email.inbound.entity.ProcessedEmailMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEmailMessageRepository extends JpaRepository<ProcessedEmailMessage, Long> {

    boolean existsByRuleUidAndMessageId(String ruleUid, String messageId);
}
