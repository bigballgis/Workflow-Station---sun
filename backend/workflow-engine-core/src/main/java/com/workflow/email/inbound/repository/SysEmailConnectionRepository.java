package com.workflow.email.inbound.repository;

import com.workflow.email.inbound.entity.SysEmailConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SysEmailConnectionRepository extends JpaRepository<SysEmailConnection, String> {
}
