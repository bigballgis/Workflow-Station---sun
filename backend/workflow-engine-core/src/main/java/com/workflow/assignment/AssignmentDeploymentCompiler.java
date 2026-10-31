package com.workflow.assignment;

/** Compiles portable node policies into an immutable, server-owned deployed contract. */
public interface AssignmentDeploymentCompiler {
    String CONTRACT = "assignmentRuntimeContract";
    String TRACKING = "assignmentRuntimeTracking";
    String USERS = "assignmentRuntimeUsers";
    String DEPENDENCIES = "assignmentRuntimeDefinitions";
    Compilation compileDeployment(String portableXml);
    default String compile(String portableXml) { return compileDeployment(portableXml).xml(); }
    record Compilation(String xml,boolean tracked) { }
}
