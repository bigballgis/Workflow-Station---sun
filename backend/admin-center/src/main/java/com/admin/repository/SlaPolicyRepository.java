package com.admin.repository;

import com.admin.entity.SlaPolicy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, String> {

    /** Serialises concurrent lead time changes of one Function Unit so versions stay gap-free. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM SlaPolicy p WHERE p.functionUnitCode = :code")
    Optional<SlaPolicy> findForUpdate(@Param("code") String code);
}
