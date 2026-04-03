package com.developer.service.impl;

import com.developer.dto.DeployResponse;
import com.developer.entity.DeploymentJob;
import com.developer.repository.DeploymentJobRepository;
import com.developer.service.DeploymentJobService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeploymentJobServiceImpl implements DeploymentJobService {

    private static final ZoneId UI_ZONE = ZoneId.of("Asia/Shanghai");

    private final DeploymentJobRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistNew(String deploymentId, Long functionUnitId, String targetAdminUrl, DeployResponse snapshot) {
        DeploymentJob row = DeploymentJob.builder()
                .id(deploymentId)
                .functionUnitId(functionUnitId)
                .targetAdminUrl(targetAdminUrl)
                .status(snapshot.getStatus() != null ? snapshot.getStatus().name() : DeployResponse.DeployStatus.DEPLOYING.name())
                .progress(snapshot.getProgress())
                .message(snapshot.getMessage())
                .versionNumber(snapshot.getVersionNumber())
                .changeLog(snapshot.getChangeLog())
                .stepsJson(serializeSteps(snapshot))
                .startedAt(Instant.now())
                .build();
        repository.save(row);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistUpdate(Long functionUnitId, String targetAdminUrl, DeployResponse snapshot) {
        if (snapshot.getDeploymentId() == null) {
            return;
        }
        DeploymentJob row = repository.findById(snapshot.getDeploymentId()).orElse(null);
        if (row == null) {
            log.warn("Deployment job not found for update: {}", snapshot.getDeploymentId());
            return;
        }
        if (!functionUnitId.equals(row.getFunctionUnitId())) {
            log.warn("Deployment job {} functionUnitId mismatch", snapshot.getDeploymentId());
            return;
        }
        row.setTargetAdminUrl(targetAdminUrl);
        row.setStatus(snapshot.getStatus() != null ? snapshot.getStatus().name() : row.getStatus());
        row.setProgress(snapshot.getProgress());
        row.setMessage(snapshot.getMessage());
        row.setVersionNumber(snapshot.getVersionNumber());
        row.setChangeLog(snapshot.getChangeLog());
        row.setStepsJson(serializeSteps(snapshot));
        if (snapshot.getStatus() == DeployResponse.DeployStatus.SUCCESS
                || snapshot.getStatus() == DeployResponse.DeployStatus.FAILED
                || snapshot.getStatus() == DeployResponse.DeployStatus.ROLLED_BACK) {
            if (row.getCompletedAt() == null) {
                row.setCompletedAt(Instant.now());
            }
        }
        repository.save(row);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeployResponse> findResponseById(String deploymentId) {
        return repository.findById(deploymentId).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeployResponse> findResponsesByFunctionUnitId(Long functionUnitId) {
        return repository.findByFunctionUnitIdOrderByStartedAtDesc(functionUnitId).stream()
                .map(this::toResponse)
                .toList();
    }

    private String serializeSteps(DeployResponse snapshot) {
        try {
            if (snapshot.getSteps() == null) {
                return objectMapper.writeValueAsString(new ArrayList<DeployResponse.DeployStep>());
            }
            return objectMapper.writeValueAsString(snapshot.getSteps());
        } catch (Exception e) {
            log.warn("Failed to serialize deployment steps: {}", e.getMessage());
            return "[]";
        }
    }

    private DeployResponse toResponse(DeploymentJob row) {
        List<DeployResponse.DeployStep> steps = new ArrayList<>();
        if (row.getStepsJson() != null && !row.getStepsJson().isBlank()) {
            try {
                steps = objectMapper.readValue(row.getStepsJson(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize deployment steps for {}: {}", row.getId(), e.getMessage());
            }
        }
        DeployResponse.DeployStatus st;
        try {
            st = DeployResponse.DeployStatus.valueOf(row.getStatus());
        } catch (Exception e) {
            st = DeployResponse.DeployStatus.DEPLOYING;
        }
        return DeployResponse.builder()
                .deploymentId(row.getId())
                .status(st)
                .message(row.getMessage())
                .progress(row.getProgress())
                .steps(steps)
                .deployedAt(row.getStartedAt() != null
                        ? java.time.LocalDateTime.ofInstant(row.getStartedAt(), UI_ZONE)
                        : null)
                .versionNumber(row.getVersionNumber())
                .changeLog(row.getChangeLog())
                .build();
    }
}
