package com.developer.repository;

import com.developer.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    List<EmailTemplate> findByFunctionUnitIdOrderByNameAsc(Long functionUnitId);

    boolean existsByFunctionUnitIdAndName(Long functionUnitId, String name);

    boolean existsByFunctionUnitIdAndNameAndIdNot(Long functionUnitId, String name, Long id);

    void deleteByFunctionUnitId(Long functionUnitId);
}
