package dev.status.application;

import dev.status.dto.StatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds this instance's live SSE clients, keyed by environment, and forwards
 * transitions to the clients of the matching env.
 */
@Component
public class SseBroker {

    private static final Logger log = LoggerFactory.getLogger(SseBroker.class);

    private final Map<String, Set<SseEmitter>> emittersByEnv = new ConcurrentHashMap<>();

    public SseEmitter register(String env, SseEmitter emitter) {
        emittersByEnv.computeIfAbsent(env, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(env, emitter));
        emitter.onTimeout(() -> remove(env, emitter));
        emitter.onError(e -> remove(env, emitter));
        return emitter;
    }

    public void broadcast(StatusEvent event) {
        Set<SseEmitter> emitters = emittersByEnv.get(event.service().env());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("status.changed").data(event));
            } catch (Exception ex) {
                log.warn("failed to broadcast SSE event: {}", ex.getMessage());
                remove(event.service().env(), emitter);
            }
        }
    }

    private void remove(String env, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByEnv.get(env);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }
}
