package com.admin.repository;

import com.admin.entity.EmailConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailConnectionRepository extends JpaRepository<EmailConnection, String> {

    List<EmailConnection> findByFunctionUnitId(String functionUnitId);

    Optional<EmailConnection> findByFunctionUnitIdAndId(String functionUnitId, String id);

    void deleteByFunctionUnitId(String functionUnitId);
}
