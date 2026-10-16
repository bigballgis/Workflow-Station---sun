package com.portal.recordnote;

import com.platform.common.recordnote.RecordNoteRuntimeContext;
import com.platform.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;

/** Bounded, authenticated batch read. Failures remain retryable, never an empty recipient list. */
@Component
public class RecordNoteEngineClient {
    private final RestTemplate http;
    private final String url;
    private final String token;

    public RecordNoteEngineClient(RestTemplateBuilder builder,
            @Value("${workflow-engine.url:http://localhost:8081}") String url,
            @Value("${service.internal-token:}") String token) {
        this.http=builder.setConnectTimeout(Duration.ofSeconds(2)).setReadTimeout(Duration.ofSeconds(3)).build();
        this.url=url; this.token=token;
    }

    public RecordNoteRuntimeContext resolve(String processId,List<String> nodes) {
        if (token.isBlank()) throw new IllegalStateException("NOTE_INTERNAL_AUTH_UNCONFIGURED");
        var uri=UriComponentsBuilder.fromHttpUrl(url).path("/api/v1/internal/record-note-targets")
                .queryParam("processInstanceId",processId).queryParam("nodes",nodes).build().encode().toUri();
        HttpHeaders headers=new HttpHeaders(); headers.set("X-Internal-Token",token);
        var response=http.exchange(uri,HttpMethod.GET,new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<RecordNoteRuntimeContext>>() {});
        if (response.getBody()==null || response.getBody().getData()==null) {
            throw new IllegalStateException("NOTE_ENGINE_CONTEXT_UNRESOLVED");
        }
        return response.getBody().getData();
    }
}
