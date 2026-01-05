package com.admin.repository;

import com.admin.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 密码历史仓库接口
 */
@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, String> {
    
    /**
     * 根据用户ID查找密码历史，按创建时间降序
     */
    List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(String userId);
    
    /**
     * 根据用户ID查找最近N条密码历史
     */
    List<PasswordHistory> findTop5ByUserIdOrderByCreatedAtDesc(String userId);
    
    /**
     * 删除用户的密码历史
     */
    void deleteByUserId(String userId);
}
