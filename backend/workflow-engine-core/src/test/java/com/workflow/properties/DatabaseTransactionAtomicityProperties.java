package com.workflow.properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据库事务原子性属性测试
 * 功能: workflow-engine-core, 属性 14: 数据库事务原子性
 * 验证需求: 需求 8.2
 */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseTransactionAtomicityProperties {

    @Autowired
    private TransactionTemplate transactionTemplate;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @BeforeEach
    void setUp() {
        // 清理并创建测试表
        jdbcTemplate.execute("DROP TABLE IF EXISTS test_transaction");
        jdbcTemplate.execute("""
            CREATE TABLE test_transaction (
                id BIGINT PRIMARY KEY,
                name VARCHAR(255),
                amount INTEGER
            )
        """);
    }

    @Test
    void testTransactionCommitAtomicity() {
        // 测试事务成功提交的原子性
        Long testId = 1L;
        String testName = "test";
        Integer testAmount = 100;
        
        // 清理测试数据
        jdbcTemplate.update("DELETE FROM test_transaction");
        int initialCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_transaction", Integer.class);
        
        // 执行成功的事务
        transactionTemplate.execute(status -> {
            // 第一个操作：插入数据
            jdbcTemplate.update("INSERT INTO test_transaction (id, name, amount) VALUES (?, ?, ?)",
                testId, testName, testAmount);
            
            // 第二个操作：更新数据
            jdbcTemplate.update("UPDATE test_transaction SET amount = ? WHERE id = ?",
                testAmount + 100, testId);
            
            return null;
        });
        
        // 验证：事务成功时，所有操作都已提交
        int finalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_transaction", Integer.class);
        Integer finalAmount = jdbcTemplate.queryForObject(
            "SELECT amount FROM test_transaction WHERE id = ?", 
            Integer.class, testId);
        
        assertEquals(initialCount + 1, finalCount, "事务成功时应该有新记录");
        assertEquals(testAmount + 100, finalAmount, "事务成功时数据应该被正确更新");
    }

    @Test
    void testTransactionRollbackAtomicity() {
        // 测试事务回滚的原子性
        Long testId = 2L;
        String testName = "test2";
        Integer testAmount = 200;
        
        // 清理测试数据
        jdbcTemplate.update("DELETE FROM test_transaction");
        int initialCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_transaction", Integer.class);
        
        // 执行会回滚的事务
        assertThrows(RuntimeException.class, () -> {
            transactionTemplate.execute(status -> {
                // 第一个操作：插入数据
                jdbcTemplate.update("INSERT INTO test_transaction (id, name, amount) VALUES (?, ?, ?)",
                    testId, testName, testAmount);
                
                // 第二个操作：更新数据
                jdbcTemplate.update("UPDATE test_transaction SET amount = ? WHERE id = ?",
                    testAmount + 100, testId);
                
                // 抛出异常触发回滚
                throw new RuntimeException("模拟业务异常，触发回滚");
            });
        });
        
        // 验证：事务失败时，所有操作都已回滚
        int finalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_transaction", Integer.class);
        assertEquals(initialCount, finalCount, "事务回滚时不应该有新记录");
    }

    @Test
    void testMultipleTransactionAtomicity() {
        // 测试多个事务的独立性
        jdbcTemplate.update("DELETE FROM test_transaction");
        
        // 第一个成功的事务
        transactionTemplate.execute(status -> {
            jdbcTemplate.update("INSERT INTO test_transaction (id, name, amount) VALUES (?, ?, ?)",
                1L, "success", 100);
            return null;
        });
        
        // 第二个失败的事务
        assertThrows(RuntimeException.class, () -> {
            transactionTemplate.execute(status -> {
                jdbcTemplate.update("INSERT INTO test_transaction (id, name, amount) VALUES (?, ?, ?)",
                    2L, "failure", 200);
                throw new RuntimeException("模拟异常");
            });
        });
        
        // 验证：只有成功的事务被提交
        int count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_transaction", Integer.class);
        assertEquals(1, count, "只有成功的事务应该被提交");
        
        String name = jdbcTemplate.queryForObject("SELECT name FROM test_transaction WHERE id = 1", String.class);
        assertEquals("success", name, "成功事务的数据应该存在");
    }
}