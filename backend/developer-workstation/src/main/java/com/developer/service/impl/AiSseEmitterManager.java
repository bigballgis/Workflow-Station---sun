package com.developer.service.impl;

import com.developer.dto.AiChatSseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE emitter 管理协作类。
 * 维护对话 SSE 与独立事件 SSE 的注册表，负责创建、发送、完成与清理 emitter。
 * 单一职责，无业务依赖。
 */
@Component
@Slf4j
class AiSseEmitterManager {

    private static final long EVENT_EMITTER_TIMEOUT = 300_000L; // 300 seconds

    /** Chat SSE emitters: key = "functionUnitId:userId" → SseEmitter */
    private final ConcurrentHashMap<String, SseEmitter> chatEmitters = new ConcurrentHashMap<>();

    /** Event SSE emitters: key = functionUnitId → list of (userId, SseEmitter) pairs */
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<EventEmitterEntry>> eventEmitters = new ConcurrentHashMap<>();

    /**
     * Create a chat SSE emitter. The timeout is computed by the caller (facade) so that it stays
     * aligned with the configured N8N timeout (including one retry).
     */
    SseEmitter createChatEmitter(Long functionUnitId, String userId, long chatEmitterTimeout) {
        String key = buildChatEmitterKey(functionUnitId, userId);
        SseEmitter emitter = new SseEmitter(chatEmitterTimeout);

        // If there's already an active emitter, complete it first to prevent overwriting
        SseEmitter existingEmitter = chatEmitters.get(key);
        if (existingEmitter != null) {
            log.warn("Existing chat SSE emitter found for key={}, completing it before creating new one", key);
            try {
                existingEmitter.complete();
            } catch (Exception e) {
                log.debug("Failed to complete existing emitter: {}", e.getMessage());
            }
            chatEmitters.remove(key);
        }

        emitter.onCompletion(() -> {
            chatEmitters.remove(key);
            log.debug("Chat SSE completed: functionUnitId={}, userId={}", functionUnitId, userId);
        });
        emitter.onTimeout(() -> {
            chatEmitters.remove(key);
            log.debug("Chat SSE timed out: functionUnitId={}, userId={}", functionUnitId, userId);
            safeComplete(emitter);
        });
        emitter.onError(ex -> {
            log.warn("Chat SSE error for functionUnit {}, userId {}: {}", functionUnitId, userId, ex.getMessage());
            chatEmitters.remove(key);
            safeComplete(emitter);
        });

        chatEmitters.put(key, emitter);
        log.info("Created chat SSE emitter: functionUnitId={}, userId={}", functionUnitId, userId);
        return emitter;
    }

    SseEmitter createEventEmitter(Long functionUnitId, String userId) {
        SseEmitter emitter = new SseEmitter(EVENT_EMITTER_TIMEOUT);
        EventEmitterEntry entry = new EventEmitterEntry(userId, emitter);

        CopyOnWriteArrayList<EventEmitterEntry> entries = eventEmitters.computeIfAbsent(
                functionUnitId, k -> new CopyOnWriteArrayList<>());

        emitter.onCompletion(() -> {
            entries.remove(entry);
            if (entries.isEmpty()) {
                eventEmitters.remove(functionUnitId, entries);
            }
            log.debug("Event SSE completed: functionUnitId={}, userId={}", functionUnitId, userId);
        });
        emitter.onTimeout(() -> {
            entries.remove(entry);
            if (entries.isEmpty()) {
                eventEmitters.remove(functionUnitId, entries);
            }
            log.debug("Event SSE timed out: functionUnitId={}, userId={}", functionUnitId, userId);
            safeComplete(emitter);
        });
        emitter.onError(ex -> {
            log.warn("Event SSE error for functionUnit {}, userId {}: {}", functionUnitId, userId, ex.getMessage());
            entries.remove(entry);
            if (entries.isEmpty()) {
                eventEmitters.remove(functionUnitId, entries);
            }
            safeComplete(emitter);
        });

        entries.add(entry);
        log.info("Created event SSE emitter: functionUnitId={}, userId={}", functionUnitId, userId);
        return emitter;
    }

    void sendChatEvent(Long functionUnitId, String userId, AiChatSseEvent event) {
        String key = buildChatEmitterKey(functionUnitId, userId);
        SseEmitter emitter = chatEmitters.get(key);
        if (emitter == null) {
            log.warn("No chat SSE emitter found for functionUnitId={}, userId={}", functionUnitId, userId);
            return;
        }
        try {
            SseEmitter.SseEventBuilder sseEvent = SseEmitter.event().name(event.getEventType());
            if (event.getData() != null) {
                Object data = event.getData();
                // Plain strings (token stream, phase name) as text — avoids JSON quoting edge cases
                // on very large markdown payloads when frontends concatenate raw data lines.
                if (data instanceof String str) {
                    sseEvent.data(str, MediaType.TEXT_PLAIN);
                } else {
                    sseEvent.data(data, MediaType.APPLICATION_JSON);
                }
            } else {
                sseEvent.data("", MediaType.TEXT_PLAIN);
            }
            emitter.send(sseEvent);
        } catch (IOException e) {
            log.warn("Failed to send chat SSE event: functionUnitId={}, userId={}, error={}",
                    functionUnitId, userId, e.getMessage());
            chatEmitters.remove(key);
        } catch (IllegalStateException e) {
            log.warn("Chat SSE emitter already completed: functionUnitId={}, userId={}, error={}",
                    functionUnitId, userId, e.getMessage());
            chatEmitters.remove(key);
        }
    }

    void sendEventNotification(Long functionUnitId, AiChatSseEvent event) {
        CopyOnWriteArrayList<EventEmitterEntry> entries = eventEmitters.get(functionUnitId);
        if (entries == null || entries.isEmpty()) {
            log.debug("No event SSE emitters for functionUnitId={}", functionUnitId);
            return;
        }

        Iterator<EventEmitterEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            EventEmitterEntry entry = iterator.next();
            try {
                entry.emitter().send(SseEmitter.event()
                        .name(event.getEventType())
                        .data(event.getData(), MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                log.warn("Failed to send event SSE notification to userId={}: {}", entry.userId(), e.getMessage());
                entries.remove(entry);
            }
        }

        if (entries.isEmpty()) {
            eventEmitters.remove(functionUnitId, entries);
        }
    }

    void completeChatEmitter(Long functionUnitId, String userId) {
        String key = buildChatEmitterKey(functionUnitId, userId);
        SseEmitter emitter = chatEmitters.remove(key);
        if (emitter != null) {
            safeComplete(emitter);
            log.debug("Completed chat SSE emitter: functionUnitId={}, userId={}", functionUnitId, userId);
        }
    }

    /**
     * Safely complete an SseEmitter, suppressing IllegalStateException caused by
     * Spring's async response finalization after the OutputStream was already committed by SSE.
     */
    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            log.debug("Emitter already completed or response committed: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("Failed to complete emitter: {}", e.getMessage());
        }
    }

    void removeChatEmitter(Long functionUnitId, String userId) {
        String key = buildChatEmitterKey(functionUnitId, userId);
        chatEmitters.remove(key);
        log.debug("Removed chat SSE emitter: functionUnitId={}, userId={}", functionUnitId, userId);
    }

    void removeEventEmitter(Long functionUnitId, String userId) {
        CopyOnWriteArrayList<EventEmitterEntry> entries = eventEmitters.get(functionUnitId);
        if (entries != null) {
            entries.removeIf(entry -> entry.userId().equals(userId));
            if (entries.isEmpty()) {
                eventEmitters.remove(functionUnitId, entries);
            }
            log.debug("Removed event SSE emitter: functionUnitId={}, userId={}", functionUnitId, userId);
        }
    }

    private String buildChatEmitterKey(Long functionUnitId, String userId) {
        return functionUnitId + ":" + userId;
    }

    /** Internal record to track event emitter ownership */
    private record EventEmitterEntry(String userId, SseEmitter emitter) {}
}
