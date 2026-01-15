# Design Document

## Overview

This design document describes the implementation of the Process Key to Function Unit Mapping feature. The feature enables the user-portal to resolve function unit information from Flowable process definition keys, which is essential for loading the correct forms and actions when users view task details.

The core challenge is that Flowable stores process definition IDs in the format `{processKey}:{version}:{uuid}` (e.g., `Process_PurchaseRequest:2:abc123`), while the developer workstation stores function units with their own IDs. This feature bridges that gap by:

1. Extracting the process key from Flowable's full process definition ID
2. Providing an API to look up function units by their BPMN process ID
3. Enhancing the FunctionUnitAccessComponent to support process key resolution

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              User Portal                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Task Detail Page                                   │   │
│  │  1. Get task from Flowable (processDefinitionId: "Process_X:2:uuid") │   │
│  │  2. Extract process key: "Process_X"                                  │   │
│  │  3. Call FunctionUnitAccessComponent.resolveFunctionUnitId()         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                         │
│                                    ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │              FunctionUnitAccessComponent                              │   │
│  │  - resolveFunctionUnitId(processKey)                                  │   │
│  │  - Resolution order: UUID → code → process-key API → name search     │   │
│  │  - Caches resolved mappings                                           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     │ HTTP GET /api/v1/admin/function-units/by-process-key/{processKey}
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Admin Center                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │              FunctionUnitController                                   │   │
│  │  GET /function-units/by-process-key/{processKey}                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                         │
│                                    ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │              FunctionUnitManagerComponent                             │   │
│  │  - findByProcessKey(processKey)                                       │   │
│  │  - Searches BPMN XML for matching process ID                         │   │
│  │  - Decodes Base64 BPMN XML before searching                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                         │
│                                    ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │              Database (admin_function_units + dw_process_definitions) │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. ProcessKeyExtractor (Utility Class)

A utility class to extract the process key from Flowable's full process definition ID.

```java
public class ProcessKeyExtractor {
    /**
     * Extracts the process key from a Flowable process definition ID.
     * Format: {processKey}:{version}:{uuid}
     * Example: "Process_PurchaseRequest:2:abc123" -> "Process_PurchaseRequest"
     * 
     * @param processDefinitionId The full process definition ID from Flowable
     * @return The extracted process key, or the original string if no colon found
     */
    public static String extractProcessKey(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return null;
        }
        int colonIndex = processDefinitionId.indexOf(':');
        if (colonIndex > 0) {
            return processDefinitionId.substring(0, colonIndex);
        }
        return processDefinitionId;
    }
}
```

### 2. FunctionUnitController (Admin Center)

New endpoint to find function unit by process key.

```java
@RestController
@RequestMapping("/function-units")
public class FunctionUnitController {
    
    @GetMapping("/by-process-key/{processKey}")
    public ResponseEntity<FunctionUnitDTO> findByProcessKey(@PathVariable String processKey) {
        Optional<FunctionUnit> functionUnit = functionUnitManagerComponent.findByProcessKey(processKey);
        return functionUnit
            .map(fu -> ResponseEntity.ok(convertToDTO(fu)))
            .orElse(ResponseEntity.notFound().build());
    }
}
```

### 3. FunctionUnitManagerComponent (Admin Center)

Enhanced component to search for function units by BPMN process ID.

```java
@Component
public class FunctionUnitManagerComponent {
    
    /**
     * Finds a function unit by its BPMN process key.
     * Searches the dw_process_definitions table for BPMN XML containing the process ID.
     * 
     * @param processKey The BPMN process ID (e.g., "Process_PurchaseRequest")
     * @return Optional containing the function unit if found
     */
    public Optional<FunctionUnit> findByProcessKey(String processKey) {
        // 1. Query all process definitions
        // 2. Decode Base64 BPMN XML
        // 3. Search for <bpmn:process id="{processKey}">
        // 4. Return the associated function unit
    }
}
```

### 4. FunctionUnitAccessComponent (User Portal)

Enhanced component to support process key resolution.

```java
@Component
public class FunctionUnitAccessComponent {
    
    private final Map<String, Long> processKeyCache = new ConcurrentHashMap<>();
    
    /**
     * Resolves a function unit ID from various identifier types.
     * Resolution order:
     * 1. Try as UUID (direct function unit ID)
     * 2. Try as function unit code
     * 3. Try as process definition key (via Admin Center API)
     * 4. Try as function unit name (fuzzy search)
     * 
     * @param identifier The identifier to resolve
     * @return The function unit ID
     * @throws PortalException if no function unit found
     */
    public Long resolveFunctionUnitId(String identifier) {
        // Check cache first
        if (processKeyCache.containsKey(identifier)) {
            return processKeyCache.get(identifier);
        }
        
        // Try resolution strategies in order
        Long functionUnitId = tryResolveAsUUID(identifier)
            .or(() -> tryResolveAsCode(identifier))
            .or(() -> tryResolveAsProcessKey(identifier))
            .or(() -> tryResolveAsName(identifier))
            .orElseThrow(() -> new PortalException("404", "Function unit not found: " + identifier));
        
        // Cache the result
        processKeyCache.put(identifier, functionUnitId);
        return functionUnitId;
    }
    
    private Optional<Long> tryResolveAsProcessKey(String processKey) {
        // Call Admin Center API: GET /function-units/by-process-key/{processKey}
        // Return the function unit ID if found
    }
}
```

## Data Models

### Process Definition ID Format

Flowable stores process definition IDs in the following format:
```
{processKey}:{version}:{uuid}
```

Examples:
- `Process_PurchaseRequest:1:12345678-abcd-1234-efgh-123456789012`
- `Process_PurchaseRequest:2:87654321-dcba-4321-hgfe-210987654321`
- `LeaveRequest:1:abcdef12-3456-7890-abcd-ef1234567890`

### BPMN Process ID Storage

The BPMN process ID is stored in the `dw_process_definitions.bpmn_xml` column as Base64-encoded XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions ...>
  <bpmn:process id="Process_PurchaseRequest" name="Purchase Request" isExecutable="true">
    <!-- Process content -->
  </bpmn:process>
</bpmn:definitions>
```

### Function Unit to Process Mapping

```
┌─────────────────────────┐       ┌─────────────────────────┐
│   admin_function_units  │       │  dw_process_definitions │
├─────────────────────────┤       ├─────────────────────────┤
│ id (PK)                 │◄──────│ function_unit_id (FK)   │
│ code                    │       │ bpmn_xml (Base64)       │
│ name                    │       │   └─ contains process id│
│ enabled                 │       └─────────────────────────┘
└─────────────────────────┘
```



## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Process Key Extraction Correctness

*For any* valid process definition ID in the format `{processKey}:{version}:{uuid}`, extracting the process key SHALL return exactly the substring before the first colon, and for any string without colons, the entire string SHALL be returned unchanged.

**Validates: Requirements 1.1, 1.2, 3.5**

### Property 2: BPMN Process ID Search Correctness

*For any* function unit with a valid BPMN XML containing a process ID, searching by that process ID SHALL return the correct function unit, regardless of whether the BPMN XML is Base64-encoded or contains additional XML elements.

**Validates: Requirements 2.2, 2.5, 2.6**

### Property 3: Cache Consistency

*For any* process key that has been successfully resolved to a function unit ID, subsequent resolution requests for the same process key SHALL return the same function unit ID from cache without making additional API calls.

**Validates: Requirements 3.3**

### Property 4: Multiple Function Unit Uniqueness

*For any* set of function units with distinct BPMN process IDs, searching by any of those process IDs SHALL return exactly one matching function unit, and no two function units SHALL share the same BPMN process ID.

**Validates: Requirements 5.3**

## Error Handling

### Process Key Extraction Errors

| Error Condition | Handling |
|-----------------|----------|
| Null input | Return null |
| Empty string | Return null |
| String with only colons | Return empty string (before first colon) |

### API Errors

| Error Condition | HTTP Status | Error Message |
|-----------------|-------------|---------------|
| Process key not found | 404 | "No function unit found for process key: {processKey}" |
| Invalid process key format | 400 | "Invalid process key format" |
| Database error | 500 | "Internal server error" |

### FunctionUnitAccessComponent Errors

| Error Condition | Exception | Message |
|-----------------|-----------|---------|
| Function unit not found | PortalException("404", ...) | "Function unit not found: {identifier}" |
| Admin Center unavailable | PortalException("503", ...) | "Admin Center service unavailable" |

## Testing Strategy

### Unit Tests

Unit tests should cover:
- `ProcessKeyExtractor.extractProcessKey()` with various input formats
- `FunctionUnitManagerComponent.findByProcessKey()` with mocked database
- `FunctionUnitAccessComponent.resolveFunctionUnitId()` with mocked Admin Center client

### Property-Based Tests

Property-based tests should use **jqwik** (Java) for the backend components:

1. **Process Key Extraction Property Test**
   - Generate random process definition IDs in format `{key}:{version}:{uuid}`
   - Verify extraction returns the correct key portion
   - Test with special characters (underscores, hyphens, numbers)

2. **BPMN Search Property Test**
   - Generate random BPMN XML with various process IDs
   - Encode to Base64 and store in database
   - Verify search finds the correct function unit

3. **Cache Consistency Property Test**
   - Generate random process keys
   - Resolve each key twice
   - Verify second resolution uses cache (no API call)

### Integration Tests

Integration tests should verify:
- End-to-end flow from task detail page to function unit resolution
- API endpoint `/function-units/by-process-key/{processKey}` returns correct data
- Error handling when function unit not found

### Test Configuration

- Property-based tests: minimum 100 iterations per property
- Each property test must reference its design document property
- Tag format: **Feature: process-key-function-unit-mapping, Property {number}: {property_text}**

## Implementation Notes

### BPMN XML Parsing

The BPMN XML is stored as Base64-encoded text in `dw_process_definitions.bpmn_xml`. To search for a process ID:

1. Decode the Base64 string to UTF-8 text
2. Parse the XML or use regex to find `<bpmn:process id="{processKey}"`
3. Handle both `bpmn:process` and `process` element names (with/without namespace prefix)

### Caching Strategy

The `FunctionUnitAccessComponent` uses a `ConcurrentHashMap` for thread-safe caching:
- Key: process definition key (String)
- Value: function unit ID (Long)
- Cache is not time-limited (function unit mappings are stable)
- Cache can be cleared on application restart

### Performance Considerations

- The BPMN search may be slow if there are many function units
- Consider adding a database index or denormalized column for process key
- Cache resolved mappings to avoid repeated database queries

## Related Files

### Admin Center
- `backend/admin-center/src/main/java/com/admin/controller/FunctionUnitController.java`
- `backend/admin-center/src/main/java/com/admin/component/FunctionUnitManagerComponent.java`

### User Portal
- `backend/user-portal/src/main/java/com/portal/component/FunctionUnitAccessComponent.java`
- `backend/user-portal/src/main/java/com/portal/util/ProcessKeyExtractor.java`

### Developer Workstation
- `backend/developer-workstation/src/main/java/com/developer/entity/ProcessDefinition.java`
- `backend/developer-workstation/src/main/resources/db/migration/V1__init_schema.sql`
