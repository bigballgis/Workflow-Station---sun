package com.developer.repository;

import com.developer.entity.EmailConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailConnectionRepository extends JpaRepository<EmailConnection, Long> {

    List<EmailConnection> findByFunctionUnitIdOrderByNameAsc(Long functionUnitId);

    Optional<EmailConnection> findByConnectionUid(String connectionUid);

    boolean existsByFunctionUnitIdAndName(Long functionUnitId, String name);

    boolean existsByFunctionUnitIdAndNameAndIdNot(Long functionUnitId, String name, Long id);
}
