package com.sluice.api.run.controller;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.run.dto.CreateRunRequest;
import com.sluice.api.run.dto.RunResponse;
import com.sluice.api.asset.dto.AssetResponse;
import com.sluice.api.run.service.RunService;
import com.sluice.api.job.service.JobEventService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/runs")
public class RunController {
    private final RunService runs;
    private final JobEventService events;

    public RunController(RunService runs, JobEventService events) {
        this.runs = runs;
        this.events = events;
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<RunResponse> create(@RequestBody CreateRunRequest request,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                              @AuthenticationPrincipal ProjectContext context) {
        var job = runs.create(request, key, context);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(runs.get(job.getId(), context).orElseThrow());
    }

    @GetMapping
    public Page<RunResponse> list(@AuthenticationPrincipal ProjectContext context,
                                  @org.springframework.data.web.PageableDefault(
                                          size = 20, sort = {"createdAt", "id"},
                                          direction = org.springframework.data.domain.Sort.Direction.DESC)
                                  Pageable pageable,
                                  @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
                                  @org.springframework.web.bind.annotation.RequestParam(required = false) String pipeline,
                                  @org.springframework.web.bind.annotation.RequestParam(name = "from", required = false) String from,
                                  @org.springframework.web.bind.annotation.RequestParam(name = "to", required = false) String to,
                                  @org.springframework.web.bind.annotation.RequestParam(required = false) String decision,
                                  @io.swagger.v3.oas.annotations.Parameter(
                                          description = "Return only runs with a persisted governance decision")
                                  @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean governanceOnly) {
        try {
            var parsedStatus = status == null || status.isBlank() ? null : com.sluice.api.job.domain.JobStatus.valueOf(status.trim().toUpperCase());
            var parsedDecision = decision == null || decision.isBlank() ? null : com.sluice.api.governance.GovernanceDecisionValue.valueOf(decision.trim().toUpperCase());
            var parsedFrom = from == null || from.isBlank() ? null : Instant.parse(from);
            var parsedTo = to == null || to.isBlank() ? null : Instant.parse(to);
            if (parsedFrom != null && parsedTo != null && !parsedFrom.isBefore(parsedTo)) {
                throw new IllegalArgumentException("from must be before to");
            }
            return runs.list(context, pageable, new RunService.RunFilters(parsedStatus,
                    pipeline == null || pipeline.isBlank() ? null : pipeline.trim(), parsedFrom, parsedTo,
                    parsedDecision, governanceOnly));
        } catch (IllegalArgumentException | java.time.DateTimeException ex) {
            throw new IllegalArgumentException("Invalid run filter");
        }
    }

    /** Source-compatible convenience for callers that do not provide filters. */
    public Page<RunResponse> list(ProjectContext context, Pageable pageable) {
        return runs.list(context, pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RunResponse> get(@PathVariable UUID id,
                                           @AuthenticationPrincipal ProjectContext context) {
        return runs.get(id, context).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/outputs")
    public ResponseEntity<List<AssetResponse>> outputs(@PathVariable UUID id,
                                                       @AuthenticationPrincipal ProjectContext context) {
        if (runs.get(id, context).isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(runs.outputs(id, context));
    }

    @GetMapping(value = "/{id}/events", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(@PathVariable UUID id,
                                              @AuthenticationPrincipal ProjectContext context) {
        if (runs.get(id, context).isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(events.subscribeToJobEvents(id));
    }
}
