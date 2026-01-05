package com.portal.repository;

import com.portal.entity.FavoriteProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 收藏流程Repository
 */
@Repository
public interface FavoriteProcessRepository extends JpaRepository<FavoriteProcess, Long> {

    List<FavoriteProcess> findByUserIdOrderByDisplayOrderAsc(String userId);

    Optional<FavoriteProcess> findByUserIdAndProcessDefinitionKey(String userId, String processDefinitionKey);

    boolean existsByUserIdAndProcessDefinitionKey(String userId, String processDefinitionKey);

    void deleteByUserIdAndProcessDefinitionKey(String userId, String processDefinitionKey);

    long countByUserId(String userId);
}
