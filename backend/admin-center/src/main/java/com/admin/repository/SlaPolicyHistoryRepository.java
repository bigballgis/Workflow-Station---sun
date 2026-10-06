package com.admin.repository;

import com.admin.entity.SlaPolicyHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SlaPolicyHistoryRepository extends JpaRepository<SlaPolicyHistory, String> {

    List<SlaPolicyHistory> findTop50ByFunctionUnitCodeOrderByChangedAtDesc(String functionUnitCode);
}
