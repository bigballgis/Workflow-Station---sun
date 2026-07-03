package com.developer.repository;

import com.developer.entity.MainTableViewAccess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MainTableViewAccessRepository extends JpaRepository<MainTableViewAccess, Long> {

    List<MainTableViewAccess> findByViewConfigId(Long viewConfigId);

    void deleteByViewConfigId(Long viewConfigId);
}
