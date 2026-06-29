package com.workflow.component;

import com.workflow.dto.request.ProcessDefinitionRequest;
import com.workflow.dto.response.DeploymentResult;
import com.workflow.dto.response.ProcessDefinitionResult;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.util.BpmnDeployEnhancer;

import lombok.extern.slf4j.Slf4j;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Process Deployment Manager
 * Handles BPMN deployment, process-definition lifecycle (delete/suspend/activate),
 * process-definition queries, and BPMN file validation/normalization.
 *
 * Extracted from {@link ProcessEngineComponent}; behavior is preserved verbatim.
 */
@Slf4j
@Component
@Transactional
public class ProcessDeploymentManager {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    /**
     * Deploy process definition
     * Supports BPMN 2.0 file validation and version management
     */
    public DeploymentResult deployProcess(ProcessDefinitionRequest request) {
        try {
            // Validate request parameters
            validateDeploymentRequest(request);

            // Normalize known legacy BPMN serialization issues before validation/deploy
            String normalizedBpmnXml = normalizeBpmnXml(request.getBpmnXml());
            String enhancedBpmnXml = BpmnDeployEnhancer.enhance(normalizedBpmnXml);

            // Validate BPMN file format
            validateBpmnFile(enhancedBpmnXml);

            // Create deployment
            Deployment deployment = repositoryService.createDeployment()
                .name(request.getName())
                .category(request.getCategory())
                .key(request.getKey())
                .addString(request.getKey() + ".bpmn", enhancedBpmnXml)
                .deploy();

            // Get deployed process definitions
            List<ProcessDefinition> processDefinitions = repositoryService
                .createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .list();

            if (!processDefinitions.isEmpty()) {
                ProcessDefinition processDefinition = processDefinitions.get(0);
                // Ensure the process definition ID is always in key:version:uuid format.
                // In some Flowable versions getId() may return only the raw UUID part;
                // explicitly construct the composite format when that happens.
                String rawId = processDefinition.getId();
                String compositeId = rawId.contains(":")
                        ? rawId
                        : String.format("%s:%d:%s",
                                processDefinition.getKey(),
                                processDefinition.getVersion(),
                                rawId);
                return DeploymentResult.success(
                    deployment.getId(),
                    compositeId,
                    processDefinition.getKey(),
                    processDefinition.getName(),
                    processDefinition.getVersion()
                );
            } else {
                return DeploymentResult.failure("Deployment succeeded but no process definition found");
            }

        } catch (WorkflowValidationException e) {
            // Re-throw validation exceptions so they can be caught by tests
            throw e;
        } catch (Exception e) {
            return DeploymentResult.failure("Failed to deploy process definition: " + e.getMessage());
        }
    }

    /**
     * Query process definition list
     */
    public List<ProcessDefinitionResult> getProcessDefinitions(String category, String key) {
        try {
            var query = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .active();

            if (StringUtils.hasText(key)) {
                query.processDefinitionKey(key);
            }

            List<ProcessDefinition> processDefinitions = query.list();

            List<String> deploymentIds = processDefinitions.stream()
                .map(ProcessDefinition::getDeploymentId)
                .distinct()
                .toList();

            Map<String, Deployment> deploymentMap = new HashMap<>();
            if (!deploymentIds.isEmpty()) {
                List<Deployment> deployments = repositoryService.createDeploymentQuery()
                    .list();
                for (Deployment d : deployments) {
                    deploymentMap.put(d.getId(), d);
                }
            }

            if (StringUtils.hasText(category)) {
                processDefinitions = processDefinitions.stream()
                    .filter(pd -> {
                        Deployment deployment = deploymentMap.get(pd.getDeploymentId());
                        return deployment != null && category.equals(deployment.getCategory());
                    })
                    .collect(Collectors.toList());
            }

            return processDefinitions.stream()
                .map(pd -> convertToProcessDefinitionResult(pd, deploymentMap.get(pd.getDeploymentId())))
                .collect(Collectors.toList());

        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_QUERY_ERROR", "Failed to query process definitions: " + e.getMessage(), e);
        }
    }

    /**
     * Delete process definition
     */
    public void deleteProcessDefinition(String deploymentId, boolean cascade) {
        try {
            // Check for running process instances
            if (!cascade) {
                long runningInstances = runtimeService.createProcessInstanceQuery()
                    .deploymentId(deploymentId)
                    .count();

                if (runningInstances > 0) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                            "deploymentId",
                            "Cannot delete process definition, there are " + runningInstances + " running process instances",
                            deploymentId)));
                }
            }

            repositoryService.deleteDeployment(deploymentId, cascade);

        } catch (WorkflowValidationException e) {
            // Re-throw validation exceptions as-is
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_DELETE_ERROR", "Failed to delete process definition: " + e.getMessage(), e);
        }
    }

    /**
     * Suspend process definition
     */
    public void suspendProcessDefinition(String processDefinitionId) {
        try {
            repositoryService.suspendProcessDefinitionById(processDefinitionId);
        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_SUSPEND_ERROR", "Failed to suspend process definition: " + e.getMessage(), e);
        }
    }

    /**
     * Activate process definition
     */
    public void activateProcessDefinition(String processDefinitionId) {
        try {
            repositoryService.activateProcessDefinitionById(processDefinitionId);
        } catch (Exception e) {
            throw new WorkflowBusinessException("PROCESS_ACTIVATE_ERROR", "Failed to activate process definition: " + e.getMessage(), e);
        }
    }

    /**
     * Load latest BPMN XML for a process definition key, or null if missing.
     * @param processDefinitionKey process definition key
     * @return BPMN XML string
     */
    public String getBpmnXml(String processDefinitionKey) {
        try {
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey(processDefinitionKey)
                    .latestVersion()
                    .singleResult();
            if (pd == null) {
                log.warn("Process definition not found: {}", processDefinitionKey);
                return null;
            }
            try (var in = repositoryService.getResourceAsStream(pd.getDeploymentId(), pd.getResourceName())) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Failed to get BPMN XML for processDefinitionKey={}: {}", processDefinitionKey, e.getMessage());
            return null;
        }
    }

    // ==================== Validation / normalization helpers ====================

    private void validateDeploymentRequest(ProcessDefinitionRequest request) {
        if (!StringUtils.hasText(request.getName())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("name", "Deployment name must not be empty", request.getName())));
        }

        if (!StringUtils.hasText(request.getKey())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("key", "Process definition key must not be empty", request.getKey())));
        }

        if (!StringUtils.hasText(request.getBpmnXml())) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN content must not be empty", request.getBpmnXml())));
        }
    }

    private void validateBpmnFile(String bpmnContent) {
        // First perform basic content checks
        if (bpmnContent == null || bpmnContent.trim().isEmpty()) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN content must not be empty", bpmnContent)));
        }

        // Check for whitespace-only content
        if (bpmnContent.trim().matches("^\\s*$")) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: content contains only whitespace", bpmnContent)));
        }

        // Check for valid XML format
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();

            // Set error handler to capture XML parsing errors
            builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(org.xml.sax.SAXParseException exception) throws org.xml.sax.SAXException {
                    throw exception;
                }

                @Override
                public void error(org.xml.sax.SAXParseException exception) throws org.xml.sax.SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(org.xml.sax.SAXParseException exception) throws org.xml.sax.SAXException {
                    throw exception;
                }
            });

            org.w3c.dom.Document document = builder.parse(new ByteArrayInputStream(bpmnContent.getBytes()));

            // Check if root element is BPMN definitions
            org.w3c.dom.Element rootElement = document.getDocumentElement();
            if (rootElement == null || !"definitions".equals(rootElement.getLocalName())) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: root element must be definitions", bpmnContent)));
            }

            // Check for BPMN namespace
            String namespaceURI = rootElement.getNamespaceURI();
            if (namespaceURI == null || (!namespaceURI.contains("BPMN") && !namespaceURI.contains("bpmn"))) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: missing BPMN namespace", bpmnContent)));
            }

            // Check for at least one process element
            org.w3c.dom.NodeList processNodes = document.getElementsByTagNameNS("*", "process");
            if (processNodes.getLength() == 0) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: must contain at least one process element", bpmnContent)));
            }

            // Check if process elements have id attribute
            for (int i = 0; i < processNodes.getLength(); i++) {
                org.w3c.dom.Element processElement = (org.w3c.dom.Element) processNodes.item(i);
                if (!processElement.hasAttribute("id") || processElement.getAttribute("id").trim().isEmpty()) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: process element must have id attribute", bpmnContent)));
                }
            }

        } catch (WorkflowValidationException e) {
            // Re-throw validation exceptions as-is
            throw e;
        } catch (org.xml.sax.SAXParseException e) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: XML parsing error - " + e.getMessage(), bpmnContent)));
        } catch (Exception e) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: " + e.getMessage(), bpmnContent)));
        }

        // Finally use Flowable for deeper validation (only after basic validation passes)
        try {
            String normalizedBpmnContent = normalizeBpmnXml(bpmnContent);
            String enhancedBpmnContent = BpmnDeployEnhancer.enhance(normalizedBpmnContent);
            Deployment tempDeployment = repositoryService.createDeployment()
                .name("temp-validation")
                .addInputStream("temp.bpmn", new ByteArrayInputStream(enhancedBpmnContent.getBytes()))
                .deploy();

            // Validation succeeded, delete temp deployment
            repositoryService.deleteDeployment(tempDeployment.getId(), true);

        } catch (Exception e) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError("bpmnXml", "BPMN file format validation failed: " + e.getMessage(), bpmnContent)));
        }
    }

    /**
     * Normalize BPMN XML to tolerate known legacy serialization mistakes.
     *
     * IMPORTANT: Keep this minimal and targeted. We only normalize casing of BPMN element names
     * that must match the BPMN 2.0 XSD exactly, otherwise Flowable deployment validation fails.
     */
    private String normalizeBpmnXml(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return bpmnXml;
        }

        // Legacy bug: Some exports used <*:MultiInstanceLoopCharacteristics> (capital M),
        // but BPMN 2.0 requires <*:multiInstanceLoopCharacteristics>.
        // Accept any prefix (bpmn:, bpmn2:, etc.) and also the no-prefix variant.
        String normalized = bpmnXml
                .replaceAll("(<\\s*[^\\s:>]+:)MultiInstanceLoopCharacteristics(\\b)", "$1multiInstanceLoopCharacteristics$2")
                .replaceAll("(<\\s*)MultiInstanceLoopCharacteristics(\\b)", "$1multiInstanceLoopCharacteristics$2")
                .replaceAll("(</\\s*[^\\s:>]+:)MultiInstanceLoopCharacteristics(\\s*>)", "$1multiInstanceLoopCharacteristics$2")
                .replaceAll("(</\\s*)MultiInstanceLoopCharacteristics(\\s*>)", "$1multiInstanceLoopCharacteristics$2");

        // Bind Activepieces service tasks to the apTaskExecutor delegate (see method doc).
        return bindActivepiecesServiceTasks(normalized);
    }

    /**
     * Bind BPMN Service Tasks that target Activepieces to the {@code ${apTaskExecutor}} delegate.
     *
     * <p>The visual designer marks an AP service task with a {@code serviceType=ap} (and
     * {@code ap:flowId}) extension property but does not emit a Flowable implementation, so the
     * deployed task would never invoke the executor. Here we use Flowable's own converter to set
     * {@code flowable:delegateExpression="${apTaskExecutor}"} on those tasks at deploy time. This is
     * environment-independent and leaves non-AP processes byte-for-byte untouched (we only
     * re-serialize when at least one AP task is rebound).
     */
    private String bindActivepiecesServiceTasks(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank() || !bpmnXml.contains("ap:flowId")) {
            // Fast path: nothing to bind when the AP marker is absent.
            return bpmnXml;
        }
        try {
            BpmnXMLConverter converter = new BpmnXMLConverter();
            XMLInputFactory xif = XMLInputFactory.newInstance();
            xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            xif.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            XMLStreamReader reader = xif.createXMLStreamReader(new StringReader(bpmnXml));
            BpmnModel model = converter.convertToBpmnModel(reader);

            boolean changed = false;
            for (Process process : model.getProcesses()) {
                for (ServiceTask st : process.findFlowElementsOfType(ServiceTask.class, true)) {
                    if (isActivepiecesServiceTask(st)
                            && (st.getImplementation() == null || st.getImplementation().isBlank())) {
                        st.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
                        st.setImplementation("${apTaskExecutor}");
                        changed = true;
                        log.info("Bound AP service task '{}' to ${{}}", st.getId(), "apTaskExecutor");
                    }
                }
            }

            if (!changed) {
                return bpmnXml;
            }
            return new String(converter.convertToXML(model), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("AP service-task delegate binding skipped (deploy continues with original XML): {}",
                    e.getMessage());
            return bpmnXml;
        }
    }

    /**
     * An AP service task is identified by a {@code serviceType=ap} extension property, or by the
     * presence of an {@code ap:flowId} extension property.
     */
    private boolean isActivepiecesServiceTask(ServiceTask serviceTask) {
        Map<String, List<ExtensionElement>> extensionElements = serviceTask.getExtensionElements();
        if (extensionElements == null || extensionElements.isEmpty()) {
            return false;
        }
        List<ExtensionElement> propertiesElements = extensionElements.get("properties");
        if (propertiesElements == null) {
            return false;
        }
        for (ExtensionElement propertiesElement : propertiesElements) {
            List<ExtensionElement> propertyElements = propertiesElement.getChildElements().get("property");
            if (propertyElements == null) {
                continue;
            }
            for (ExtensionElement propertyElement : propertyElements) {
                String name = propertyElement.getAttributeValue(null, "name");
                String value = propertyElement.getAttributeValue(null, "value");
                if ("ap:flowId".equals(name) && value != null && !value.isBlank()) {
                    return true;
                }
                if ("serviceType".equals(name) && "ap".equalsIgnoreCase(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private ProcessDefinitionResult convertToProcessDefinitionResult(ProcessDefinition processDefinition, Deployment deployment) {
        String deploymentCategory = processDefinition.getCategory();
        String deploymentName = null;
        if (deployment != null) {
            deploymentCategory = deployment.getCategory();
            deploymentName = deployment.getName();
        }

        String finalName = (deploymentName != null && !deploymentName.equals("temp-validation"))
            ? deploymentName
            : processDefinition.getName();

        return ProcessDefinitionResult.builder()
            .id(processDefinition.getId())
            .key(processDefinition.getKey())
            .name(finalName)
            .version(processDefinition.getVersion())
            .category(deploymentCategory)
            .deploymentId(processDefinition.getDeploymentId())
            .resourceName(processDefinition.getResourceName())
            .diagramResourceName(processDefinition.getDiagramResourceName())
            .description(processDefinition.getDescription())
            .hasStartFormKey(processDefinition.hasStartFormKey())
            .hasGraphicalNotation(processDefinition.hasGraphicalNotation())
            .suspended(processDefinition.isSuspended())
            .tenantId(processDefinition.getTenantId())
            .build();
    }
}
