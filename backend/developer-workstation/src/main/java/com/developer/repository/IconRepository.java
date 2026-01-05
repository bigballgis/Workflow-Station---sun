package com.developer.repository;

import com.developer.entity.Icon;
import com.developer.enums.IconCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 图标仓库
 */
@Repository
public interface IconRepository extends JpaRepository<Icon, Long> {
    
    List<Icon> findByCategory(IconCategory category);
    
    Page<Icon> findByCategory(IconCategory category, Pageable pageable);
    
    @Query("SELECT i FROM Icon i WHERE " +
           "(:keyword IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(i.tags) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:category IS NULL OR i.category = :category)")
    Page<Icon> search(@Param("keyword") String keyword, 
                      @Param("category") IconCategory category, 
                      Pageable pageable);
    
    boolean existsByName(String name);
}
