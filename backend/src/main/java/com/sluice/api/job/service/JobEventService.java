package com.sluice.api.job.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.Instant;

@Service
public class JobEventService {

    private final ConcurrentHashMap<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribeToJobEvents(UUID jobId) {
        SseEmitter emitter = new SseEmitter(3600000L); // 1 hour timeout
        
        emitters.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        
        emitter.onCompletion(() -> removeEmitter(jobId, emitter));
        emitter.onTimeout(() -> removeEmitter(jobId, emitter));
        emitter.onError(e -> removeEmitter(jobId, emitter));
        
        return emitter;
    }
    
    private void removeEmitter(UUID jobId, SseEmitter emitter) {
        List<SseEmitter> jobEmitters = emitters.get(jobId);
        if (jobEmitters != null) {
            jobEmitters.remove(emitter);
            if (jobEmitters.isEmpty()) {
                emitters.remove(jobId);
            }
        }
    }
    
    public void broadcastJobStatusChange(UUID jobId, com.sluice.api.job.domain.JobStatus status) {
        List<SseEmitter> jobEmitters = emitters.get(jobId);
        if (jobEmitters != null) {
            for (SseEmitter emitter : jobEmitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .id(UUID.randomUUID().toString())
                            .name("JobStatusChanged")
                            .data(new JobStatusUpdate(jobId, status.name(), Instant.now())));
                            
                    if (status == com.sluice.api.job.domain.JobStatus.COMPLETED || status == com.sluice.api.job.domain.JobStatus.FAILED) {
                        emitter.complete();
                        removeEmitter(jobId, emitter);
                    }
                } catch (IOException e) {
                    emitter.completeWithError(e);
                    removeEmitter(jobId, emitter);
                }
            }
        }
    }

    public record JobStatusUpdate(UUID jobId, String status, Instant timestamp) {}
}
