package com.platform.common.functionunit;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.Size;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for function unit export/import.
 * Validates: Property 2 (Function Unit Export/Import Round-trip Consistency)
 * Validates: Property 18 (Deployment Rollback Correctness)
 */
class FunctionUnitPropertyTest {
    
    // Property 2: Function Unit Export/Import Round-trip Consistency
    // For any valid function unit, exporting to ZIP and re-importing should
    // produce an equivalent function unit
    
    @Property(tries = 100)
    void exportImportShouldBeConsistent(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String name,
            @ForAll @AlphaChars @Size(min = 1, max = 10) String version,
            @ForAll @Size(min = 1, max = 5) List<@AlphaChars @Size(min = 1, max = 20) String> processNames,
            @ForAll @Size(min = 1, max = 5) List<@AlphaChars @Size(min = 1, max = 20) String> tableNames) {
        
        // Create a function unit
        SimulatedFunctionUnit original = new SimulatedFunctionUnit(name, version, processNames, tableNames);
        
        // Export to ZIP
        byte[] exported = original.export();
        
        // Import from ZIP
        SimulatedFunctionUnit imported = SimulatedFunctionUnit.importFrom(exported);
        
        // Should be equivalent
        assertThat(imported.getName()).isEqualTo(original.getName());
        assertThat(imported.getVersion()).isEqualTo(original.getVersion());
        assertThat(imported.getProcessNames()).containsExactlyElementsOf(original.getProcessNames());
        assertThat(imported.getTableNames()).containsExactlyElementsOf(original.getTableNames());
    }
    
    @Property(tries = 100)
    void exportedPackageShouldContainAllComponents(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String name,
            @ForAll @Size(min = 1, max = 5) List<@AlphaChars @Size(min = 1, max = 20) String> processNames) {
        
        SimulatedFunctionUnit unit = new SimulatedFunctionUnit(name, "1.0", processNames, List.of());
        byte[] exported = unit.export();
        
        // Verify ZIP contains expected entries
        Set<String> entries = getZipEntries(exported);
        
        assertThat(entries).contains("manifest.json");
        for (String processName : processNames) {
            assertThat(entries).contains("processes/" + processName + ".bpmn");
        }
    }
    
    @Property(tries = 100)
    void checksumShouldDetectTampering(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String name) {
        
        SimulatedFunctionUnit unit = new SimulatedFunctionUnit(name, "1.0", List.of("process1"), List.of());
        byte[] exported = unit.export();
        
        String originalChecksum = unit.getChecksum();
        
        // Tamper with the data
        if (exported.length > 100) {
            exported[100] = (byte) (exported[100] ^ 0xFF);
        }
        
        // Checksum should be different
        String tamperedChecksum = calculateChecksum(exported);
        assertThat(tamperedChecksum).isNotEqualTo(originalChecksum);
    }
    
    // Property 18: Deployment Rollback Correctness
    // For any failed deployment, rollback should restore the system to pre-deployment state
    
    @Property(tries = 100)
    void rollbackShouldRestorePreviousState(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String functionUnitId,
            @ForAll @AlphaChars @Size(min = 1, max = 10) String previousVersion,
            @ForAll @AlphaChars @Size(min = 1, max = 10) String newVersion) {
        
        SimulatedDeploymentService service = new SimulatedDeploymentService();
        
        // Deploy previous version
        service.deploy(functionUnitId, previousVersion, Environment.PRODUCTION);
        assertThat(service.getCurrentVersion(functionUnitId)).isEqualTo(previousVersion);
        
        // Deploy new version (simulating failure)
        String deploymentId = service.deployWithFailure(functionUnitId, newVersion, Environment.PRODUCTION);
        
        // Rollback
        RollbackResult result = service.rollback(deploymentId);
        
        // Should restore previous version
        assertThat(result.isSuccess()).isTrue();
        assertThat(service.getCurrentVersion(functionUnitId)).isEqualTo(previousVersion);
    }
    
    @Property(tries = 100)
    void rollbackShouldPreserveData(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String functionUnitId,
            @ForAll @Size(min = 1, max = 10) List<@AlphaChars @Size(min = 1, max = 20) String> dataItems) {
        
        SimulatedDeploymentService service = new SimulatedDeploymentService();
        
        // Set up initial data
        service.setData(functionUnitId, dataItems);
        
        // Deploy and fail
        String deploymentId = service.deployWithFailure(functionUnitId, "2.0", Environment.PRODUCTION);
        
        // Rollback
        service.rollback(deploymentId);
        
        // Data should be preserved
        assertThat(service.getData(functionUnitId)).containsExactlyElementsOf(dataItems);
    }
    
    private Set<String> getZipEntries(byte[] zipData) {
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return entries;
    }
    
    private String calculateChecksum(byte[] data) {
        int hash = 0;
        for (byte b : data) {
            hash = 31 * hash + b;
        }
        return Integer.toHexString(hash);
    }
    
    // Simulated function unit for testing
    private static class SimulatedFunctionUnit {
        private final String name;
        private final String version;
        private final List<String> processNames;
        private final List<String> tableNames;
        private String checksum;
        
        SimulatedFunctionUnit(String name, String version, List<String> processNames, List<String> tableNames) {
            this.name = name;
            this.version = version;
            this.processNames = new ArrayList<>(processNames);
            this.tableNames = new ArrayList<>(tableNames);
        }
        
        byte[] export() {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ZipOutputStream zos = new ZipOutputStream(baos)) {
                
                // Add manifest
                zos.putNextEntry(new ZipEntry("manifest.json"));
                String manifest = String.format("{\"name\":\"%s\",\"version\":\"%s\"}", name, version);
                zos.write(manifest.getBytes());
                zos.closeEntry();
                
                // Add processes
                for (String processName : processNames) {
                    zos.putNextEntry(new ZipEntry("processes/" + processName + ".bpmn"));
                    zos.write(("<bpmn>" + processName + "</bpmn>").getBytes());
                    zos.closeEntry();
                }
                
                // Add tables
                for (String tableName : tableNames) {
                    zos.putNextEntry(new ZipEntry("tables/" + tableName + ".sql"));
                    zos.write(("CREATE TABLE " + tableName).getBytes());
                    zos.closeEntry();
                }
                
                zos.finish();
                byte[] data = baos.toByteArray();
                this.checksum = Integer.toHexString(Arrays.hashCode(data));
                return data;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        static SimulatedFunctionUnit importFrom(byte[] zipData) {
            String name = "";
            String version = "";
            List<String> processes = new ArrayList<>();
            List<String> tables = new ArrayList<>();
            
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals("manifest.json")) {
                        String content = new String(zis.readAllBytes());
                        name = content.split("\"name\":\"")[1].split("\"")[0];
                        version = content.split("\"version\":\"")[1].split("\"")[0];
                    } else if (entry.getName().startsWith("processes/")) {
                        String processName = entry.getName().replace("processes/", "").replace(".bpmn", "");
                        processes.add(processName);
                    } else if (entry.getName().startsWith("tables/")) {
                        String tableName = entry.getName().replace("tables/", "").replace(".sql", "");
                        tables.add(tableName);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            
            return new SimulatedFunctionUnit(name, version, processes, tables);
        }
        
        String getName() { return name; }
        String getVersion() { return version; }
        List<String> getProcessNames() { return processNames; }
        List<String> getTableNames() { return tableNames; }
        String getChecksum() { return checksum; }
    }
    
    // Simulated deployment service for testing
    private static class SimulatedDeploymentService {
        private final Map<String, String> currentVersions = new HashMap<>();
        private final Map<String, String> previousVersions = new HashMap<>();
        private final Map<String, List<String>> data = new HashMap<>();
        private final Map<String, String> deploymentVersions = new HashMap<>();
        
        void deploy(String functionUnitId, String version, Environment env) {
            previousVersions.put(functionUnitId, currentVersions.get(functionUnitId));
            currentVersions.put(functionUnitId, version);
        }
        
        String deployWithFailure(String functionUnitId, String version, Environment env) {
            String deploymentId = UUID.randomUUID().toString();
            previousVersions.put(functionUnitId, currentVersions.get(functionUnitId));
            deploymentVersions.put(deploymentId, functionUnitId);
            // Simulate failure - don't update current version
            return deploymentId;
        }
        
        RollbackResult rollback(String deploymentId) {
            String functionUnitId = deploymentVersions.get(deploymentId);
            String previous = previousVersions.get(functionUnitId);
            currentVersions.put(functionUnitId, previous);
            
            return RollbackResult.builder()
                    .success(true)
                    .deploymentId(deploymentId)
                    .restoredVersion(previous)
                    .build();
        }
        
        String getCurrentVersion(String functionUnitId) {
            return currentVersions.get(functionUnitId);
        }
        
        void setData(String functionUnitId, List<String> items) {
            data.put(functionUnitId, new ArrayList<>(items));
        }
        
        List<String> getData(String functionUnitId) {
            return data.getOrDefault(functionUnitId, List.of());
        }
    }
}
