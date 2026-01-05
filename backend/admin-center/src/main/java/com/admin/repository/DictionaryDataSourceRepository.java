package com.admin.repository;

import com.admin.entity.DictionaryDataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DictionaryDataSourceRepository extends JpaRepository<DictionaryDataSource, String> {
    
    Optional<DictionaryDataSource> findByDictionaryIdAndEnabled(String dictionaryId, Boolean enabled);
    
    List<DictionaryDataSource> findByDictionaryId(String dictionaryId);
    
    void deleteByDictionaryId(String dictionaryId);
}
