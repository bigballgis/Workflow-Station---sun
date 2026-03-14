package com.admin.repository;

import com.admin.entity.N8nConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface N8nConfigRepository extends JpaRepository<N8nConfig, String> {

    List<N8nConfig> findByIsActiveTrue();
}
