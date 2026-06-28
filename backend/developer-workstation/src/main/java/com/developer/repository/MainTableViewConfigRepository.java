package com.developer.repository;

import com.developer.entity.MainTableViewConfig;
import com.developer.enums.MainTableViewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MainTableViewConfigRepository extends JpaRepository<MainTableViewConfig, Long> {

    List<MainTableViewConfig> findByFunctionUnitIdOrderByIsDefaultDescViewNameAsc(Long functionUnitId);

    List<MainTableViewConfig> findByFunctionUnitIdAndStatusOrderByIsDefaultDescViewNameAsc(
            Long functionUnitId, MainTableViewStatus status);

    Optional<MainTableViewConfig> findByFunctionUnitIdAndIsDefaultTrue(Long functionUnitId);

    boolean existsByFunctionUnitIdAndIsDefaultTrue(Long functionUnitId);

    Optional<MainTableViewConfig> findByMainTableIdAndIsDefaultTrue(Long mainTableId);

    boolean existsByMainTableIdAndIsDefaultTrue(Long mainTableId);

    @Query("SELECT v FROM MainTableViewConfig v LEFT JOIN FETCH v.viewFields WHERE v.id = :id")
    Optional<MainTableViewConfig> findByIdWithFields(@Param("id") Long id);

    @Query("SELECT v FROM MainTableViewConfig v LEFT JOIN FETCH v.viewFields "
            + "WHERE v.functionUnit.id = :functionUnitId ORDER BY v.isDefault DESC, v.viewName ASC")
    List<MainTableViewConfig> findByFunctionUnitIdWithFields(@Param("functionUnitId") Long functionUnitId);

    @Query("SELECT v FROM MainTableViewConfig v LEFT JOIN FETCH v.viewFields WHERE v.mainTableId = :tableId")
    List<MainTableViewConfig> findByMainTableIdWithFields(@Param("tableId") Long tableId);

    void deleteByFunctionUnitId(Long functionUnitId);
}
