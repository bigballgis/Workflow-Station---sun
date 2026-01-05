package com.admin.repository;

import com.admin.entity.DictionaryVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据字典版本仓库
 */
@Repository
public interface DictionaryVersionRepository extends JpaRepository<DictionaryVersion, String> {
    
    /**
     * 根据字典ID查找所有版本
     */
    List<DictionaryVersion> findByDictionaryIdOrderByVersionDesc(String dictionaryId);
    
    /**
     * 根据字典ID和版本号查找
     */
    Optional<DictionaryVersion> findByDictionaryIdAndVersion(String dictionaryId, Integer version);
    
    /**
     * 获取字典的最新版本
     */
    @Query("SELECT v FROM DictionaryVersion v WHERE v.dictionaryId = :dictionaryId ORDER BY v.version DESC LIMIT 1")
    Optional<DictionaryVersion> findLatestVersion(@Param("dictionaryId") String dictionaryId);
    
    /**
     * 删除字典的所有版本
     */
    void deleteByDictionaryId(String dictionaryId);
}
