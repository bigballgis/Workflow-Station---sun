package com.admin.repository;

import com.platform.security.entity.VirtualGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 虚拟组仓库接口
 */
@Repository
public interface VirtualGroupRepository extends JpaRepository<VirtualGroup, String> {
    
    /**
     * 根据名称查找虚拟组
     */
    Optional<VirtualGroup> findByName(String name);
    
    /**
     * 检查名称是否存在
     */
    boolean existsByName(String name);
    
    /**
     * 根据代码查找虚拟组
     */
    Optional<VirtualGroup> findByCode(String code);
    
    /**
     * 检查代码是否存在
     */
    boolean existsByCode(String code);
    
    /**
     * 根据类型查找虚拟组
     */
    List<VirtualGroup> findByType(String type);
    
    /**
     * 根据类型分页查找虚拟组
     */
    Page<VirtualGroup> findByType(String type, Pageable pageable);
    
    /**
     * 根据状态查找虚拟组
     */
    List<VirtualGroup> findByStatus(String status);
    
    /**
     * 根据类型和状态查找虚拟组
     */
    List<VirtualGroup> findByTypeAndStatus(String type, String status);
    
    /**
     * 查找有效的虚拟组（状态为ACTIVE）
     */
    @Query("SELECT vg FROM VirtualGroup vg WHERE vg.status = 'ACTIVE'")
    List<VirtualGroup> findValidGroups();
    
    /**
     * 根据条件查询虚拟组
     */
    @Query("SELECT vg FROM VirtualGroup vg WHERE " +
           "(:type IS NULL OR vg.type = :type) AND " +
           "(:status IS NULL OR vg.status = :status) AND " +
           "(:keyword IS NULL OR LOWER(vg.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<VirtualGroup> findByConditions(
            @Param("type") String type,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);
    
    /**
     * 统计某类型的虚拟组数量
     */
    long countByType(String type);
    
    /**
     * 统计某状态的虚拟组数量
     */
    long countByStatus(String status);
    
    /**
     * 查找用户所属的虚拟组
     */
    @Query("SELECT vg FROM VirtualGroup vg " +
           "JOIN vg.members m " +
           "WHERE m.user.id = :userId AND vg.status = 'ACTIVE'")
    List<VirtualGroup> findByUserId(@Param("userId") String userId);
}
