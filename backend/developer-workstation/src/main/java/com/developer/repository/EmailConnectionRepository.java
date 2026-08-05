package com.developer.repository;

import com.developer.entity.EmailConnection;
import com.developer.enums.EmailConnectionDirection;
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

    boolean existsByFunctionUnitIdAndNameAndDirection(
            Long functionUnitId, String name, EmailConnectionDirection direction);

    boolean existsByFunctionUnitIdAndNameAndDirectionAndIdNot(
            Long functionUnitId, String name, EmailConnectionDirection direction, Long id);

    void deleteByFunctionUnitId(Long functionUnitId);
}
