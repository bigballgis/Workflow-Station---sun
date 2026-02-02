package com.admin.repository;

import com.platform.security.entity.UserBusinessUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户业务单元成员关系仓库接口
 */
@Repository
public interface UserBusinessUnitRepository extends JpaRepository<UserBusinessUnit, String> {
    
    /**
     * 根据用户ID查找所有业务单元成员关系
     */
    List<UserBusinessUnit> findByUserId(String userId);
    
    /**
     * 根据业务单元ID查找所有成员关系
     */
    List<UserBusinessUnit> findByBusinessUnitId(String businessUnitId);
    
    /**
     * 根据用户ID和业务单元ID查找成员关系
     */
    Optional<UserBusinessUnit> findByUserIdAndBusinessUnitId(String userId, String businessUnitId);
    
    /**
     * 检查用户是否是业务单元的成员
     */
    boolean existsByUserIdAndBusinessUnitId(String userId, String businessUnitId);
    
    /**
     * 删除用户的所有业务单元成员关系
     */
    void deleteByUserId(String userId);
    
    /**
     * 删除业务单元的所有成员关系
     */
    void deleteByBusinessUnitId(String businessUnitId);
    
    /**
     * 根据用户ID查找所有业务单元成员关系（包含业务单元信息）
     */
    @Query("SELECT ub FROM UserBusinessUnit ub LEFT JOIN FETCH ub.businessUnit WHERE ub.userId = :userId")
    List<UserBusinessUnit> findByUserIdWithBusinessUnit(@Param("userId") String userId);
    
    /**
     * 根据业务单元ID查找所有成员关系（包含用户信息）
     */
    @Query("SELECT ub FROM UserBusinessUnit ub LEFT JOIN FETCH ub.user WHERE ub.businessUnitId = :businessUnitId")
    List<UserBusinessUnit> findByBusinessUnitIdWithUser(@Param("businessUnitId") String businessUnitId);
    
    /**
     * 统计业务单元的成员数量
     */
    long countByBusinessUnitId(String businessUnitId);
    
    /**
     * 统计用户加入的业务单元数量
     */
    long countByUserId(String userId);
}
