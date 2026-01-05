package com.admin.repository;

import com.admin.entity.SystemLog;
import com.admin.enums.LogLevel;
import com.admin.enums.LogType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, String>, JpaSpecificationExecutor<SystemLog> {
    
    Page<SystemLog> findByLogType(LogType logType, Pageable pageable);
    
    Page<SystemLog> findByLogLevel(LogLevel logLevel, Pageable pageable);
    
    Page<SystemLog> findByUserId(String userId, Pageable pageable);
    
    Page<SystemLog> findByModule(String module, Pageable pageable);
    
    Page<SystemLog> findByTimestampBetween(Instant start, Instant end, Pageable pageable);
    
    @Query("SELECT l FROM SystemLog l WHERE l.logType = :type AND l.timestamp BETWEEN :start AND :end")
    Page<SystemLog> findByTypeAndTimeRange(@Param("type") LogType type, 
                                           @Param("start") Instant start, 
                                           @Param("end") Instant end, 
                                           Pageable pageable);
    
    @Query("SELECT l FROM SystemLog l WHERE l.message LIKE %:keyword% OR l.action LIKE %:keyword%")
    Page<SystemLog> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
    
    @Query("SELECT l.logLevel, COUNT(l) FROM SystemLog l WHERE l.timestamp >= :since GROUP BY l.logLevel")
    List<Object[]> countByLogLevelSince(@Param("since") Instant since);
    
    @Query("SELECT l.module, COUNT(l) FROM SystemLog l WHERE l.logLevel = 'ERROR' AND l.timestamp >= :since GROUP BY l.module ORDER BY COUNT(l) DESC")
    List<Object[]> getErrorTrendByModule(@Param("since") Instant since);
    
    @Query("SELECT FUNCTION('DATE', l.timestamp), COUNT(l) FROM SystemLog l WHERE l.logLevel = 'ERROR' AND l.timestamp >= :since GROUP BY FUNCTION('DATE', l.timestamp)")
    List<Object[]> getErrorTrendByDate(@Param("since") Instant since);
    
    long countByLogTypeAndTimestampAfter(LogType logType, Instant since);
    
    long countByLogLevelAndTimestampAfter(LogLevel logLevel, Instant since);
    
    void deleteByTimestampBefore(Instant before);
}
