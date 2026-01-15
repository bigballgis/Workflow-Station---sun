# Implementation Plan: Process Key to Function Unit Mapping

## Overview

This implementation plan covers the feature that enables the user-portal to resolve function unit information from Flowable process definition keys. The implementation follows a bottom-up approach: utility class → API endpoint → component enhancement → integration.

## Tasks

- [x] 1. Implement ProcessKeyExtractor utility class
  - Create `ProcessKeyExtractor.java` in user-portal util package
  - Implement `extractProcessKey(String processDefinitionId)` method
  - Handle null, empty, and no-colon cases
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 1.1 Write property test for ProcessKeyExtractor

  - **Property 1: Process Key Extraction Correctness**
  - Generate random process definition IDs in format `{key}:{version}:{uuid}`
  - Verify extraction returns correct key portion
  - Test edge cases: null, empty, no colons, special characters
  - **Validates: Requirements 1.1, 1.2, 3.5**

- [x] 2. Implement Admin Center API endpoint
  - [x] 2.1 Add `findByProcessKey` method to FunctionUnitManagerComponent
    - Query dw_process_definitions table
    - Decode Base64 BPMN XML
    - Search for `<bpmn:process id="{processKey}">`
    - Return associated function unit
    - _Requirements: 2.2, 2.5, 2.6_

  - [x] 2.2 Add REST endpoint to FunctionUnitController
    - `GET /function-units/by-process-key/{processKey}`
    - Return FunctionUnitDTO if found
    - Return 404 if not found
    - _Requirements: 2.1, 2.3, 2.4_

- [x] 2.3 Write property test for BPMN process ID search

  - **Property 2: BPMN Process ID Search Correctness**
  - Generate random BPMN XML with various process IDs
  - Verify search finds correct function unit
  - **Validates: Requirements 2.2, 2.5, 2.6**

- [x] 3. Checkpoint - Ensure Admin Center API works
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Enhance FunctionUnitAccessComponent in User Portal
  - [x] 4.1 Add process key resolution method
    - Already implemented in `resolveFunctionUnitId` method
    - Calls Admin Center API `/api/v1/admin/function-units/by-process-key/{processKey}`
    - Handles 404 response gracefully (falls through to name search)
    - _Requirements: 3.1, 3.2, 3.4_

  - [x] 4.2 Add caching for resolved mappings
    - Added `processKeyCache` ConcurrentHashMap for thread-safe caching
    - Cache process key → function unit ID mappings with TTL
    - Check cache before making API calls
    - Added `clearProcessKeyCache()`, `getProcessKeyCacheSize()`, `isProcessKeyCached()` methods
    - _Requirements: 3.3_

  - [x] 4.3 Update resolution order in resolveFunctionUnitId
    - Already implemented with correct order
    - Order: UUID → code → process-key → name search
    - _Requirements: 3.5_

- [x] 4.4 Write property test for cache consistency

  - **Property 3: Cache Consistency**
  - Resolve same process key twice
  - Verify second resolution uses cache
  - **Validates: Requirements 3.3**

- [x] 5. Checkpoint - Ensure User Portal integration works
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Write property test for multiple function unit uniqueness

  - **Property 4: Multiple Function Unit Uniqueness**
  - Created `FunctionUnitUniquenessProperties.java` with 4 property tests:
    1. `eachDistinctProcessKeyReturnsExactlyOneFunctionUnit` - verifies each distinct process key returns exactly one function unit
    2. `processKeyMapsToExactlyOneFunctionUnit` - verifies process key maps to exactly one function unit
    3. `differentProcessKeysMayMapToDifferentFunctionUnits` - verifies different process keys map to different function units
    4. `searchResultsAreDeterministic` - verifies search results are deterministic
  - All 100 iterations passed successfully
  - **Validates: Requirements 5.3**

- [x] 7. Final checkpoint - Ensure all tests pass
  - All property tests passed:
    - `ProcessKeyExtractorProperties` (7 tests, 100 iterations each)
    - `ProcessKeySearchProperties` (4 tests, 100 iterations each)
    - `ProcessKeyCacheProperties` (4 tests, 100 iterations each)
    - `FunctionUnitUniquenessProperties` (4 tests, 100 iterations each)
  - Note: BCryptTest.testAdmin123Password failure is unrelated to this spec (pre-existing issue)

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- The implementation uses Java 17 and Spring Boot 3.x
- Property-based testing uses jqwik library
