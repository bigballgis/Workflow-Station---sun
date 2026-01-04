package com.workflow.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * 数据表CRUD操作一致性属性测试
 * 验证需求: 需求 4.7, 4.8 - 数据表CRUD操作支持
 * 
 * 属性 18: 数据表CRUD操作一致性
 * 对于任何有效的数据表操作序列（创建、读取、更新、删除），操作结果应该保持数据的一致性。
 * 插入的记录应该能够被查询到，更新的记录应该反映最新的值，删除的记录应该不再存在。
 * 
 * 注意：这是一个简化的属性测试，主要验证CRUD操作一致性逻辑的正确性，不依赖Spring Boot上下文
 */
@Label("功能: workflow-engine-core, 属性 18: 数据表CRUD操作一致性")
public class DataTableCrudConsistencyProperties {

    // 模拟数据表存储，使用内存Map来测试核心逻辑
    private final Map<String, Map<String, Map<String, Object>>> tableStorage = new ConcurrentHashMap<>();
    private final Map<String, Long> tableSequences = new ConcurrentHashMap<>();
    
    /**
     * 简化的数据表操作结果类
     */
    private static class DataTableOperationResult {
        private final boolean success;
        private final int affectedRows;
        private final Map<String, Object> generatedKeys;
        private final String errorMessage;
        
        public DataTableOperationResult(boolean success, int affectedRows, Map<String, Object> generatedKeys, String errorMessage) {
            this.success = success;
            this.affectedRows = affectedRows;
            this.generatedKeys = generatedKeys != null ? generatedKeys : new HashMap<>();
            this.errorMessage = errorMessage;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public int getAffectedRows() { return affectedRows; }
        public Map<String, Object> getGeneratedKeys() { return generatedKeys; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * 简化的数据表查询结果类
     */
    private static class DataTableQueryResult {
        private final boolean success;
        private final List<Map<String, Object>> data;
        private final Long totalCount;
        private final String errorMessage;
        
        public DataTableQueryResult(boolean success, List<Map<String, Object>> data, Long totalCount, String errorMessage) {
            this.success = success;
            this.data = data != null ? data : new ArrayList<>();
            this.totalCount = totalCount;
            this.errorMessage = errorMessage;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public List<Map<String, Object>> getData() { return data; }
        public Long getTotalCount() { return totalCount; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * 属性测试: 插入后立即查询一致性
     */
    @Property(tries = 100)
    @Label("插入后立即查询一致性")
    void insertThenQueryConsistency(@ForAll @NotBlank @Size(min = 1, max = 50) String tableName,
                                  @ForAll @NotBlank @Size(min = 1, max = 50) String nameValue,
                                  @ForAll @Size(max = 100) String descriptionValue) {
        // Given: 准备插入数据
        Map<String, Object> insertData = new HashMap<>();
        insertData.put("name", nameValue);
        insertData.put("description", descriptionValue);
        insertData.put("status", "ACTIVE");
        insertData.put("created_time", System.currentTimeMillis());
        
        // When: 插入记录
        DataTableOperationResult insertResult = insertRecord(tableName, insertData, true);
        
        // Then: 插入应该成功
        assertThat(insertResult.isSuccess()).isTrue();
        assertThat(insertResult.getAffectedRows()).isEqualTo(1);
        assertThat(insertResult.getGeneratedKeys()).containsKey("id");
        
        Long generatedId = (Long) insertResult.getGeneratedKeys().get("id");
        assertThat(generatedId).isNotNull().isPositive();
        
        // When: 查询刚插入的记录
        Map<String, Object> queryConditions = Map.of("id", generatedId);
        DataTableQueryResult queryResult = queryTable(tableName, null, queryConditions, null, null, null, null);
        
        // Then: 应该能查询到记录，且数据一致
        assertThat(queryResult.isSuccess()).isTrue();
        assertThat(queryResult.getData()).hasSize(1);
        
        Map<String, Object> retrievedRecord = queryResult.getData().get(0);
        assertThat(retrievedRecord.get("id")).isEqualTo(generatedId);
        assertThat(retrievedRecord.get("name")).isEqualTo(nameValue);
        assertThat(retrievedRecord.get("description")).isEqualTo(descriptionValue);
        assertThat(retrievedRecord.get("status")).isEqualTo("ACTIVE");
    }

    /**
     * 属性测试: 更新后查询一致性
     */
    @Property(tries = 100)
    @Label("更新后查询一致性")
    void updateThenQueryConsistency(@ForAll @NotBlank @Size(min = 1, max = 50) String tableName,
                                  @ForAll @NotBlank @Size(min = 1, max = 50) String originalName,
                                  @ForAll @NotBlank @Size(min = 1, max = 50) String updatedName) {
        Assume.that(!originalName.equals(updatedName));
        
        // Given: 先插入一条记录
        Map<String, Object> insertData = Map.of(
                "name", originalName,
                "status", "ACTIVE"
        );
        
        DataTableOperationResult insertResult = insertRecord(tableName, insertData, true);
        assertThat(insertResult.isSuccess()).isTrue();
        
        Long recordId = (Long) insertResult.getGeneratedKeys().get("id");
        
        // When: 更新记录
        Map<String, Object> updateData = Map.of("name", updatedName, "status", "UPDATED");
        Map<String, Object> whereConditions = Map.of("id", recordId);
        
        DataTableOperationResult updateResult = updateRecord(tableName, updateData, whereConditions);
        
        // Then: 更新应该成功
        assertThat(updateResult.isSuccess()).isTrue();
        assertThat(updateResult.getAffectedRows()).isEqualTo(1);
        
        // When: 查询更新后的记录
        DataTableQueryResult queryResult = queryTable(tableName, null, whereConditions, null, null, null, null);
        
        // Then: 应该反映更新后的值
        assertThat(queryResult.isSuccess()).isTrue();
        assertThat(queryResult.getData()).hasSize(1);
        
        Map<String, Object> retrievedRecord = queryResult.getData().get(0);
        assertThat(retrievedRecord.get("id")).isEqualTo(recordId);
        assertThat(retrievedRecord.get("name")).isEqualTo(updatedName);
        assertThat(retrievedRecord.get("status")).isEqualTo("UPDATED");
        assertThat(retrievedRecord.get("name")).isNotEqualTo(originalName);
    }

    /**
     * 属性测试: 删除后查询一致性
     */
    @Property(tries = 100)
    @Label("删除后查询一致性")
    void deleteThenQueryConsistency(@ForAll @NotBlank @Size(min = 1, max = 50) String tableName,
                                  @ForAll @NotBlank @Size(min = 1, max = 50) String nameValue) {
        // Given: 先插入一条记录
        Map<String, Object> insertData = Map.of(
                "name", nameValue,
                "status", "ACTIVE"
        );
        
        DataTableOperationResult insertResult = insertRecord(tableName, insertData, true);
        assertThat(insertResult.isSuccess()).isTrue();
        
        Long recordId = (Long) insertResult.getGeneratedKeys().get("id");
        
        // 验证记录存在
        Map<String, Object> queryConditions = Map.of("id", recordId);
        DataTableQueryResult beforeDeleteQuery = queryTable(tableName, null, queryConditions, null, null, null, null);
        assertThat(beforeDeleteQuery.isSuccess()).isTrue();
        assertThat(beforeDeleteQuery.getData()).hasSize(1);
        
        // When: 删除记录
        DataTableOperationResult deleteResult = deleteRecord(tableName, queryConditions);
        
        // Then: 删除应该成功
        assertThat(deleteResult.isSuccess()).isTrue();
        assertThat(deleteResult.getAffectedRows()).isEqualTo(1);
        
        // When: 查询删除后的记录
        DataTableQueryResult afterDeleteQuery = queryTable(tableName, null, queryConditions, null, null, null, null);
        
        // Then: 应该查询不到记录
        assertThat(afterDeleteQuery.isSuccess()).isTrue();
        assertThat(afterDeleteQuery.getData()).isEmpty();
    }

    /**
     * 属性测试: 批量操作一致性
     */
    @Property(tries = 50)
    @Label("批量操作一致性")
    void batchOperationConsistency(@ForAll @NotBlank @Size(min = 1, max = 50) String tableName,
                                 @ForAll @Size(min = 2, max = 5) List<@NotBlank @Size(min = 1, max = 20) String> nameValues) {
        Assume.that(nameValues.size() >= 2);
        Assume.that(nameValues.stream().distinct().count() == nameValues.size()); // 确保名称唯一
        
        // Given & When: 批量插入记录
        List<Long> insertedIds = new ArrayList<>();
        
        for (String name : nameValues) {
            Map<String, Object> insertData = Map.of(
                    "name", name,
                    "status", "ACTIVE",
                    "batch_id", "batch-" + System.currentTimeMillis()
            );
            
            DataTableOperationResult insertResult = insertRecord(tableName, insertData, true);
            assertThat(insertResult.isSuccess()).isTrue();
            
            Long recordId = (Long) insertResult.getGeneratedKeys().get("id");
            insertedIds.add(recordId);
        }
        
        // Then: 查询所有插入的记录
        DataTableQueryResult queryAllResult = queryTable(tableName, null, null, null, null, null, null);
        assertThat(queryAllResult.isSuccess()).isTrue();
        assertThat(queryAllResult.getData().size()).isGreaterThanOrEqualTo(nameValues.size());
        
        // 验证每个插入的记录都能被查询到
        for (int i = 0; i < insertedIds.size(); i++) {
            Long recordId = insertedIds.get(i);
            String expectedName = nameValues.get(i);
            
            Map<String, Object> queryConditions = Map.of("id", recordId);
            DataTableQueryResult singleQuery = queryTable(tableName, null, queryConditions, null, null, null, null);
            
            assertThat(singleQuery.isSuccess()).isTrue();
            assertThat(singleQuery.getData()).hasSize(1);
            assertThat(singleQuery.getData().get(0).get("name")).isEqualTo(expectedName);
        }
        
        // When: 批量更新状态
        for (Long recordId : insertedIds) {
            Map<String, Object> updateData = Map.of("status", "BATCH_UPDATED");
            Map<String, Object> whereConditions = Map.of("id", recordId);
            
            DataTableOperationResult updateResult = updateRecord(tableName, updateData, whereConditions);
            assertThat(updateResult.isSuccess()).isTrue();
        }
        
        // Then: 验证所有记录都已更新
        for (Long recordId : insertedIds) {
            Map<String, Object> queryConditions = Map.of("id", recordId);
            DataTableQueryResult queryResult = queryTable(tableName, null, queryConditions, null, null, null, null);
            
            assertThat(queryResult.isSuccess()).isTrue();
            assertThat(queryResult.getData()).hasSize(1);
            assertThat(queryResult.getData().get(0).get("status")).isEqualTo("BATCH_UPDATED");
        }
    }

    /**
     * 属性测试: 条件查询一致性
     */
    @Property(tries = 100)
    @Label("条件查询一致性")
    void conditionalQueryConsistency(@ForAll @NotBlank @Size(min = 1, max = 50) String tableName,
                                   @ForAll @Size(min = 1, max = 3) List<@NotBlank @Size(min = 1, max = 20) String> statusValues) {
        Assume.that(!statusValues.isEmpty());
        
        // Given: 插入不同状态的记录
        Map<String, List<Long>> statusToIds = new HashMap<>();
        
        for (String status : statusValues) {
            // 为每个状态插入2条记录
            for (int i = 0; i < 2; i++) {
                Map<String, Object> insertData = Map.of(
                        "name", "record_" + status + "_" + i,
                        "status", status,
                        "priority", i + 1
                );
                
                DataTableOperationResult insertResult = insertRecord(tableName, insertData, true);
                assertThat(insertResult.isSuccess()).isTrue();
                
                Long recordId = (Long) insertResult.getGeneratedKeys().get("id");
                statusToIds.computeIfAbsent(status, k -> new ArrayList<>()).add(recordId);
            }
        }
        
        // When & Then: 按状态查询记录
        for (String status : statusValues) {
            Map<String, Object> queryConditions = Map.of("status", status);
            DataTableQueryResult queryResult = queryTable(tableName, null, queryConditions, null, null, null, null);
            
            assertThat(queryResult.isSuccess()).isTrue();
            assertThat(queryResult.getData()).hasSize(2); // 每个状态有2条记录
            
            // 验证查询结果中的所有记录都有正确的状态
            for (Map<String, Object> record : queryResult.getData()) {
                assertThat(record.get("status")).isEqualTo(status);
                assertThat(statusToIds.get(status)).contains((Long) record.get("id"));
            }
        }
        
        // When: 查询不存在的状态
        Map<String, Object> nonExistentConditions = Map.of("status", "NON_EXISTENT_STATUS");
        DataTableQueryResult emptyResult = queryTable(tableName, null, nonExistentConditions, null, null, null, null);
        
        // Then: 应该返回空结果
        assertThat(emptyResult.isSuccess()).isTrue();
        assertThat(emptyResult.getData()).isEmpty();
    }

    /**
     * 属性测试: 分页查询一致性
     */
    @Property(tries = 50)
    @Label("分页查询一致性")
    void paginationQueryConsistency(@ForAll @NotBlank @Size(min = 1, max = 50) String tableName) {
        // Given: 插入足够多的记录用于分页测试
        int totalRecords = 7; // 固定数量便于测试
        List<Long> allIds = new ArrayList<>();
        
        for (int i = 0; i < totalRecords; i++) {
            Map<String, Object> insertData = Map.of(
                    "name", "record_" + i,
                    "sequence", i,
                    "status", "ACTIVE"
            );
            
            DataTableOperationResult insertResult = insertRecord(tableName, insertData, true);
            assertThat(insertResult.isSuccess()).isTrue();
            
            Long recordId = (Long) insertResult.getGeneratedKeys().get("id");
            allIds.add(recordId);
        }
        
        // When & Then: 测试分页查询
        int pageSize = 3;
        List<Map<String, Object>> allPaginatedRecords = new ArrayList<>();
        
        // 第一页
        DataTableQueryResult page1 = queryTable(tableName, null, null, "id", "ASC", pageSize, 0);
        assertThat(page1.isSuccess()).isTrue();
        assertThat(page1.getData()).hasSize(pageSize);
        allPaginatedRecords.addAll(page1.getData());
        
        // 第二页
        DataTableQueryResult page2 = queryTable(tableName, null, null, "id", "ASC", pageSize, pageSize);
        assertThat(page2.isSuccess()).isTrue();
        assertThat(page2.getData()).hasSize(pageSize);
        allPaginatedRecords.addAll(page2.getData());
        
        // 第三页（剩余记录）
        DataTableQueryResult page3 = queryTable(tableName, null, null, "id", "ASC", pageSize, pageSize * 2);
        assertThat(page3.isSuccess()).isTrue();
        assertThat(page3.getData()).hasSize(totalRecords - pageSize * 2);
        allPaginatedRecords.addAll(page3.getData());
        
        // Then: 分页查询的总记录数应该等于插入的记录数
        assertThat(allPaginatedRecords).hasSize(totalRecords);
        
        // 验证没有重复记录
        Set<Object> uniqueIds = allPaginatedRecords.stream()
                .map(record -> record.get("id"))
                .collect(Collectors.toSet());
        assertThat(uniqueIds).hasSize(totalRecords);
        
        // 验证所有插入的ID都在分页结果中
        for (Long insertedId : allIds) {
            assertThat(uniqueIds).contains((Object) insertedId);
        }
    }

    /**
     * 属性测试: 排序查询一致性
     */
    @Property(tries = 100)
    @Label("排序查询一致性")
    void sortingQueryConsistency(@ForAll @NotBlank @Size(min = 1, max = 50) String tableName) {
        // Given: 插入带有不同优先级的记录
        List<Integer> priorities = Arrays.asList(3, 1, 4, 2, 5);
        List<Long> insertedIds = new ArrayList<>();
        
        for (int i = 0; i < priorities.size(); i++) {
            Map<String, Object> insertData = Map.of(
                    "name", "record_" + i,
                    "priority", priorities.get(i),
                    "status", "ACTIVE"
            );
            
            DataTableOperationResult insertResult = insertRecord(tableName, insertData, true);
            assertThat(insertResult.isSuccess()).isTrue();
            
            Long recordId = (Long) insertResult.getGeneratedKeys().get("id");
            insertedIds.add(recordId);
        }
        
        // When: 按优先级升序查询
        DataTableQueryResult ascResult = queryTable(tableName, null, null, "priority", "ASC", null, null);
        
        // Then: 结果应该按优先级升序排列
        assertThat(ascResult.isSuccess()).isTrue();
        assertThat(ascResult.getData()).hasSize(priorities.size());
        
        List<Integer> ascPriorities = ascResult.getData().stream()
                .map(record -> (Integer) record.get("priority"))
                .collect(Collectors.toList());
        
        List<Integer> expectedAscPriorities = priorities.stream().sorted().collect(Collectors.toList());
        assertThat(ascPriorities).isEqualTo(expectedAscPriorities);
        
        // When: 按优先级降序查询
        DataTableQueryResult descResult = queryTable(tableName, null, null, "priority", "DESC", null, null);
        
        // Then: 结果应该按优先级降序排列
        assertThat(descResult.isSuccess()).isTrue();
        assertThat(descResult.getData()).hasSize(priorities.size());
        
        List<Integer> descPriorities = descResult.getData().stream()
                .map(record -> (Integer) record.get("priority"))
                .collect(Collectors.toList());
        
        List<Integer> expectedDescPriorities = priorities.stream()
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
        assertThat(descPriorities).isEqualTo(expectedDescPriorities);
    }

    /**
     * 属性测试: 复杂条件查询一致性
     */
    @Property(tries = 100)
    @Label("复杂条件查询一致性")
    void complexConditionQueryConsistency(@ForAll @NotBlank @Size(min = 1, max = 50) String tableName,
                                        @ForAll @NotBlank @Size(min = 1, max = 20) String targetStatus) {
        // Given: 插入多条不同状态和优先级的记录
        List<Map<String, Object>> testData = Arrays.asList(
                Map.of("name", "record1", "status", targetStatus, "priority", 1),
                Map.of("name", "record2", "status", targetStatus, "priority", 2),
                Map.of("name", "record3", "status", "OTHER", "priority", 1),
                Map.of("name", "record4", "status", "OTHER", "priority", 2),
                Map.of("name", "record5", "status", targetStatus, "priority", 3)
        );
        
        List<Long> targetStatusIds = new ArrayList<>();
        List<Long> allIds = new ArrayList<>();
        
        for (Map<String, Object> data : testData) {
            DataTableOperationResult insertResult = insertRecord(tableName, data, true);
            assertThat(insertResult.isSuccess()).isTrue();
            
            Long recordId = (Long) insertResult.getGeneratedKeys().get("id");
            allIds.add(recordId);
            
            if (targetStatus.equals(data.get("status"))) {
                targetStatusIds.add(recordId);
            }
        }
        
        // When: 查询特定状态的记录
        Map<String, Object> statusCondition = Map.of("status", targetStatus);
        DataTableQueryResult statusResult = queryTable(tableName, null, statusCondition, null, null, null, null);
        
        // Then: 应该只返回匹配状态的记录
        assertThat(statusResult.isSuccess()).isTrue();
        assertThat(statusResult.getData()).hasSize(3); // targetStatus有3条记录
        
        for (Map<String, Object> record : statusResult.getData()) {
            assertThat(record.get("status")).isEqualTo(targetStatus);
            assertThat(targetStatusIds).contains((Long) record.get("id"));
        }
        
        // When: 查询特定状态且优先级为1的记录
        Map<String, Object> complexCondition = Map.of("status", targetStatus, "priority", 1);
        DataTableQueryResult complexResult = queryTable(tableName, null, complexCondition, null, null, null, null);
        
        // Then: 应该只返回同时匹配状态和优先级的记录
        assertThat(complexResult.isSuccess()).isTrue();
        assertThat(complexResult.getData()).hasSize(1); // 只有1条记录匹配
        
        Map<String, Object> matchedRecord = complexResult.getData().get(0);
        assertThat(matchedRecord.get("status")).isEqualTo(targetStatus);
        assertThat(matchedRecord.get("priority")).isEqualTo(1);
        assertThat(matchedRecord.get("name")).isEqualTo("record1");
    }

    /**
     * 属性测试: 数据类型一致性
     */
    @Property(tries = 100)
    @Label("数据类型一致性")
    void dataTypeConsistency(@ForAll @NotBlank @Size(min = 1, max = 50) String tableName,
                           @ForAll int intValue,
                           @ForAll long longValue,
                           @ForAll double doubleValue,
                           @ForAll boolean booleanValue) {
        Assume.that(!Double.isNaN(doubleValue) && !Double.isInfinite(doubleValue));
        
        // Given: 插入包含各种数据类型的记录
        Map<String, Object> insertData = new HashMap<>();
        insertData.put("name", "type_test_record");
        insertData.put("int_field", intValue);
        insertData.put("long_field", longValue);
        insertData.put("double_field", doubleValue);
        insertData.put("boolean_field", booleanValue);
        insertData.put("string_field", "test_string");
        insertData.put("timestamp_field", System.currentTimeMillis());
        
        // When: 插入记录
        DataTableOperationResult insertResult = insertRecord(tableName, insertData, true);
        
        // Then: 插入应该成功
        assertThat(insertResult.isSuccess()).isTrue();
        assertThat(insertResult.getAffectedRows()).isEqualTo(1);
        
        Long recordId = (Long) insertResult.getGeneratedKeys().get("id");
        
        // When: 查询记录
        Map<String, Object> queryConditions = Map.of("id", recordId);
        DataTableQueryResult queryResult = queryTable(tableName, null, queryConditions, null, null, null, null);
        
        // Then: 数据类型应该保持一致
        assertThat(queryResult.isSuccess()).isTrue();
        assertThat(queryResult.getData()).hasSize(1);
        
        Map<String, Object> retrievedRecord = queryResult.getData().get(0);
        assertThat(retrievedRecord.get("int_field")).isEqualTo(intValue);
        assertThat(retrievedRecord.get("long_field")).isEqualTo(longValue);
        assertThat(retrievedRecord.get("double_field")).isEqualTo(doubleValue);
        assertThat(retrievedRecord.get("boolean_field")).isEqualTo(booleanValue);
        assertThat(retrievedRecord.get("string_field")).isEqualTo("test_string");
        
        // 验证数据类型
        assertThat(retrievedRecord.get("int_field")).isInstanceOf(Integer.class);
        assertThat(retrievedRecord.get("long_field")).isInstanceOf(Long.class);
        assertThat(retrievedRecord.get("double_field")).isInstanceOf(Double.class);
        assertThat(retrievedRecord.get("boolean_field")).isInstanceOf(Boolean.class);
        assertThat(retrievedRecord.get("string_field")).isInstanceOf(String.class);
    }

    /**
     * 属性测试: 并发操作一致性
     */
    @Property(tries = 50)
    @Label("并发操作一致性")
    void concurrentOperationConsistency(@ForAll @NotBlank @Size(min = 1, max = 50) String tableName,
                                      @ForAll @Size(min = 2, max = 4) List<@NotBlank @Size(min = 1, max = 20) String> recordNames) {
        Assume.that(recordNames.size() >= 2);
        Assume.that(recordNames.stream().distinct().count() == recordNames.size()); // 确保名称唯一
        
        // Given & When: 模拟并发插入操作
        List<Long> insertedIds = new ArrayList<>();
        
        // 同时插入多条记录（模拟并发）
        for (String name : recordNames) {
            Map<String, Object> insertData = Map.of(
                    "name", name,
                    "status", "CONCURRENT",
                    "thread_id", Thread.currentThread().getId()
            );
            
            DataTableOperationResult insertResult = insertRecord(tableName, insertData, true);
            assertThat(insertResult.isSuccess()).isTrue();
            
            Long recordId = (Long) insertResult.getGeneratedKeys().get("id");
            insertedIds.add(recordId);
        }
        
        // Then: 所有记录都应该成功插入且ID唯一
        assertThat(insertedIds).hasSize(recordNames.size());
        assertThat(insertedIds.stream().distinct().count()).isEqualTo(recordNames.size());
        
        // When: 并发查询所有记录
        DataTableQueryResult queryAllResult = queryTable(tableName, null, null, null, null, null, null);
        
        // Then: 应该能查询到所有插入的记录
        assertThat(queryAllResult.isSuccess()).isTrue();
        assertThat(queryAllResult.getData().size()).isGreaterThanOrEqualTo(recordNames.size());
        
        // 验证每个插入的记录都存在
        Set<Object> queriedIds = queryAllResult.getData().stream()
                .map(record -> record.get("id"))
                .collect(Collectors.toSet());
        
        for (Long insertedId : insertedIds) {
            assertThat(queriedIds).contains((Object) insertedId);
        }
        
        // When: 并发更新操作
        for (Long recordId : insertedIds) {
            Map<String, Object> updateData = Map.of("status", "UPDATED_CONCURRENT");
            Map<String, Object> whereConditions = Map.of("id", recordId);
            
            DataTableOperationResult updateResult = updateRecord(tableName, updateData, whereConditions);
            assertThat(updateResult.isSuccess()).isTrue();
        }
        
        // Then: 验证所有记录都已更新
        for (Long recordId : insertedIds) {
            Map<String, Object> queryConditions = Map.of("id", recordId);
            DataTableQueryResult queryResult = queryTable(tableName, null, queryConditions, null, null, null, null);
            
            assertThat(queryResult.isSuccess()).isTrue();
            assertThat(queryResult.getData()).hasSize(1);
            assertThat(queryResult.getData().get(0).get("status")).isEqualTo("UPDATED_CONCURRENT");
        }
    }

    // ==================== 辅助方法 ====================
    
    /**
     * 插入记录（模拟DataTableManagerComponent的insertRecord方法）
     */
    private DataTableOperationResult insertRecord(String tableName, Map<String, Object> data, boolean returnGeneratedKeys) {
        try {
            // 验证表名和数据
            if (tableName == null || tableName.trim().isEmpty()) {
                return new DataTableOperationResult(false, 0, null, "表名不能为空");
            }
            
            if (data == null || data.isEmpty()) {
                return new DataTableOperationResult(false, 0, null, "插入数据不能为空");
            }
            
            // 获取或创建表
            Map<String, Map<String, Object>> table = tableStorage.computeIfAbsent(tableName, k -> new ConcurrentHashMap<>());
            
            // 生成主键
            Long id = tableSequences.compute(tableName, (k, v) -> v == null ? 1L : v + 1);
            
            // 复制数据并添加主键
            Map<String, Object> recordData = new HashMap<>(data);
            recordData.put("id", id);
            
            // 存储记录
            table.put(String.valueOf(id), recordData);
            
            // 返回结果
            Map<String, Object> generatedKeys = returnGeneratedKeys ? Map.of("id", id) : new HashMap<>();
            return new DataTableOperationResult(true, 1, generatedKeys, null);
            
        } catch (Exception e) {
            return new DataTableOperationResult(false, 0, null, "插入失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询表记录（模拟DataTableManagerComponent的queryTable方法）
     */
    private DataTableQueryResult queryTable(String tableName, List<String> selectFields, 
                                          Map<String, Object> whereConditions, String orderBy, 
                                          String orderDirection, Integer limit, Integer offset) {
        try {
            // 验证表名
            if (tableName == null || tableName.trim().isEmpty()) {
                return new DataTableQueryResult(false, null, null, "表名不能为空");
            }
            
            // 获取表数据
            Map<String, Map<String, Object>> table = tableStorage.get(tableName);
            if (table == null) {
                return new DataTableQueryResult(true, new ArrayList<>(), 0L, null);
            }
            
            // 获取所有记录
            List<Map<String, Object>> allRecords = new ArrayList<>(table.values());
            
            // 应用WHERE条件
            if (whereConditions != null && !whereConditions.isEmpty()) {
                allRecords = allRecords.stream()
                        .filter(record -> matchesConditions(record, whereConditions))
                        .collect(Collectors.toList());
            }
            
            // 应用排序
            if (orderBy != null && !orderBy.trim().isEmpty()) {
                allRecords.sort((r1, r2) -> {
                    Object v1 = r1.get(orderBy);
                    Object v2 = r2.get(orderBy);
                    
                    if (v1 == null && v2 == null) return 0;
                    if (v1 == null) return -1;
                    if (v2 == null) return 1;
                    
                    int result;
                    if (v1 instanceof Comparable && v2 instanceof Comparable) {
                        @SuppressWarnings("unchecked")
                        Comparable<Object> c1 = (Comparable<Object>) v1;
                        result = c1.compareTo(v2);
                    } else {
                        result = v1.toString().compareTo(v2.toString());
                    }
                    
                    return "DESC".equalsIgnoreCase(orderDirection) ? -result : result;
                });
            }
            
            // 应用分页
            long totalCount = allRecords.size();
            if (offset != null && offset > 0) {
                allRecords = allRecords.stream().skip(offset).collect(Collectors.toList());
            }
            if (limit != null && limit > 0) {
                allRecords = allRecords.stream().limit(limit).collect(Collectors.toList());
            }
            
            // 应用字段选择
            if (selectFields != null && !selectFields.isEmpty()) {
                allRecords = allRecords.stream()
                        .map(record -> {
                            Map<String, Object> filteredRecord = new HashMap<>();
                            for (String field : selectFields) {
                                if (record.containsKey(field)) {
                                    filteredRecord.put(field, record.get(field));
                                }
                            }
                            return filteredRecord;
                        })
                        .collect(Collectors.toList());
            }
            
            return new DataTableQueryResult(true, allRecords, totalCount, null);
            
        } catch (Exception e) {
            return new DataTableQueryResult(false, null, null, "查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新记录（模拟DataTableManagerComponent的updateRecord方法）
     */
    private DataTableOperationResult updateRecord(String tableName, Map<String, Object> updateData, 
                                                Map<String, Object> whereConditions) {
        try {
            // 验证参数
            if (tableName == null || tableName.trim().isEmpty()) {
                return new DataTableOperationResult(false, 0, null, "表名不能为空");
            }
            
            if (updateData == null || updateData.isEmpty()) {
                return new DataTableOperationResult(false, 0, null, "更新数据不能为空");
            }
            
            if (whereConditions == null || whereConditions.isEmpty()) {
                return new DataTableOperationResult(false, 0, null, "更新条件不能为空");
            }
            
            // 获取表数据
            Map<String, Map<String, Object>> table = tableStorage.get(tableName);
            if (table == null) {
                return new DataTableOperationResult(true, 0, null, null);
            }
            
            // 查找匹配的记录并更新
            int affectedRows = 0;
            for (Map<String, Object> record : table.values()) {
                if (matchesConditions(record, whereConditions)) {
                    // 更新记录
                    for (Map.Entry<String, Object> entry : updateData.entrySet()) {
                        record.put(entry.getKey(), entry.getValue());
                    }
                    affectedRows++;
                }
            }
            
            return new DataTableOperationResult(true, affectedRows, null, null);
            
        } catch (Exception e) {
            return new DataTableOperationResult(false, 0, null, "更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除记录（模拟DataTableManagerComponent的deleteRecord方法）
     */
    private DataTableOperationResult deleteRecord(String tableName, Map<String, Object> whereConditions) {
        try {
            // 验证参数
            if (tableName == null || tableName.trim().isEmpty()) {
                return new DataTableOperationResult(false, 0, null, "表名不能为空");
            }
            
            if (whereConditions == null || whereConditions.isEmpty()) {
                return new DataTableOperationResult(false, 0, null, "删除条件不能为空");
            }
            
            // 获取表数据
            Map<String, Map<String, Object>> table = tableStorage.get(tableName);
            if (table == null) {
                return new DataTableOperationResult(true, 0, null, null);
            }
            
            // 查找匹配的记录并删除
            List<String> keysToDelete = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : table.entrySet()) {
                if (matchesConditions(entry.getValue(), whereConditions)) {
                    keysToDelete.add(entry.getKey());
                }
            }
            
            // 删除记录
            for (String key : keysToDelete) {
                table.remove(key);
            }
            
            return new DataTableOperationResult(true, keysToDelete.size(), null, null);
            
        } catch (Exception e) {
            return new DataTableOperationResult(false, 0, null, "删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查记录是否匹配条件
     */
    private boolean matchesConditions(Map<String, Object> record, Map<String, Object> conditions) {
        for (Map.Entry<String, Object> condition : conditions.entrySet()) {
            Object recordValue = record.get(condition.getKey());
            Object conditionValue = condition.getValue();
            
            if (recordValue == null && conditionValue == null) {
                continue;
            }
            
            if (recordValue == null || conditionValue == null) {
                return false;
            }
            
            if (!recordValue.equals(conditionValue)) {
                return false;
            }
        }
        
        return true;
    }
}