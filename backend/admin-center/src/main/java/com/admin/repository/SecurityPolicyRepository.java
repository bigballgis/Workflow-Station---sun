package com.admin.repository;

import com.admin.entity.SecurityPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecurityPolicyRepository extends JpaRepository<SecurityPolicy, String> {
    
    Optional<SecurityPolicy> findByPolicyType(String policyType);
    
    List<SecurityPolicy> findByEnabled(Boolean enabled);
}
