package com.developer.validation;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of injection detection analysis containing details about detected threats
 */
@Data
@NoArgsConstructor
public class InjectionDetectionResult {
    
    private boolean safe = true;
    private List<InjectionThreat> threats = new ArrayList<>();
    
    /**
     * Creates a safe result with no threats detected
     */
    public static InjectionDetectionResult safe() {
        return new InjectionDetectionResult();
    }
    
    /**
     * Adds a detected threat to the result
     * 
     * @param type The type of injection detected
     * @param description Description of the threat
     */
    public void addThreat(InjectionType type, String description) {
        this.safe = false;
        this.threats.add(new InjectionThreat(type, description));
    }
    
    /**
     * Checks if any threats were detected
     * 
     * @return true if threats were detected, false if input is safe
     */
    public boolean hasThreats() {
        return !safe;
    }
    
    /**
     * Gets the number of threats detected
     * 
     * @return number of threats
     */
    public int getThreatCount() {
        return threats.size();
    }
    
    /**
     * Checks if a specific type of injection was detected
     * 
     * @param type The injection type to check for
     * @return true if this type of injection was detected
     */
    public boolean hasInjectionType(InjectionType type) {
        return threats.stream()
                .anyMatch(threat -> threat.getType() == type);
    }
    
    /**
     * Gets all threats of a specific type
     * 
     * @param type The injection type to filter by
     * @return list of threats of the specified type
     */
    public List<InjectionThreat> getThreatsOfType(InjectionType type) {
        return threats.stream()
                .filter(threat -> threat.getType() == type)
                .toList();
    }
    
    /**
     * Gets a summary of all detected threats
     * 
     * @return formatted string describing all threats
     */
    public String getThreatSummary() {
        if (safe) {
            return "No threats detected";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("Detected ").append(threats.size()).append(" threat(s): ");
        
        for (int i = 0; i < threats.size(); i++) {
            if (i > 0) {
                summary.append(", ");
            }
            summary.append(threats.get(i).getType().getDisplayName());
        }
        
        return summary.toString();
    }
    
    /**
     * Represents a single injection threat
     */
    @Data
    @NoArgsConstructor
    public static class InjectionThreat {
        private InjectionType type;
        private String description;
        
        public InjectionThreat(InjectionType type, String description) {
            this.type = type;
            this.description = description;
        }
    }
}