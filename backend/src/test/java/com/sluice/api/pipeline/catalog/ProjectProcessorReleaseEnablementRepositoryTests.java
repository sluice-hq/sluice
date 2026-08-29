package com.sluice.api.pipeline.catalog;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.auth.domain.User;
import com.sluice.api.auth.repository.UserRepository;
import com.sluice.api.pipeline.catalog.service.ProcessorEnablementService;
import com.sluice.api.pipeline.catalog.repository.ProcessorVersionRepository;
import com.sluice.api.pipeline.catalog.repository.ProjectProcessorReleaseEnablementRepository;
import com.sluice.api.project.domain.Project;
import com.sluice.api.project.domain.ProjectMember;
import com.sluice.api.project.repository.ProjectMemberRepository;
import com.sluice.api.project.repository.ProjectRepository;
import com.sluice.api.support.SluiceIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SluiceIntegrationTest
class ProjectProcessorReleaseEnablementRepositoryTests {
    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProcessorVersionRepository processorVersionRepository;

    @Autowired
    private ProjectProcessorReleaseEnablementRepository enablementRepository;

    @Autowired
    private ProcessorEnablementService enablementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectMemberRepository memberRepository;

    @Test
    @Transactional
    void scopesExactReleaseEnablementToOneProject() {
        Instant now = Instant.now();
        Project enabledProject = projectRepository.save(new Project(UUID.randomUUID(), "Enabled", now));
        Project otherProject = projectRepository.save(new Project(UUID.randomUUID(), "Other", now));
        var release = processorVersionRepository
                .findByDefinitionSlugAndSemanticVersion("webp", "2.0.0")
                .orElseThrow();

        assertEquals(1, enablementRepository.insertIfAbsent(UUID.randomUUID(), enabledProject.getId(),
                release.getId(), now, now));
        assertEquals(0, enablementRepository.insertIfAbsent(UUID.randomUUID(), enabledProject.getId(),
                release.getId(), now, now));

        assertTrue(enablementRepository
                .existsByProject_IdAndProcessorVersion_Definition_SlugAndProcessorVersion_SemanticVersion(
                        enabledProject.getId(), "webp", "2.0.0"));
        assertFalse(enablementRepository
                .existsByProject_IdAndProcessorVersion_Definition_SlugAndProcessorVersion_SemanticVersion(
                        otherProject.getId(), "webp", "2.0.0"));

        var enabledReleases = enablementRepository
                .findByProject_IdOrderByProcessorVersion_Definition_SlugAsc(enabledProject.getId());
        assertEquals(1, enabledReleases.size());
        assertEquals("webp",
                enabledReleases.get(0).getProcessorVersion().getDefinition().getSlug());
        assertEquals("2.0.0", enabledReleases.get(0).getProcessorVersion().getSemanticVersion());
    }

    @Test
    void concurrentEnableRequestsAreIdempotentAndReturnLoadedState() throws Exception {
        Instant now = Instant.now();
        User owner = userRepository.save(new User(UUID.randomUUID(),
                "processor-owner-" + UUID.randomUUID() + "@example.com", "hash", now));
        Project project = projectRepository.save(new Project(UUID.randomUUID(), "Concurrent enable", now));
        memberRepository.save(new ProjectMember(owner.getId(), project.getId(), "OWNER", now));
        ProjectContext context = new ProjectContext(project.getId(), owner.getId(), false);
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try {
            var request = (java.util.concurrent.Callable<com.sluice.api.pipeline.catalog.domain.ProjectProcessorReleaseEnablement>) () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent enable did not start");
                return enablementService.enable(project.getId(), "webp", "2.0.0", context);
            };
            var firstFuture = executor.submit(request);
            var secondFuture = executor.submit(request);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            var first = firstFuture.get(20, TimeUnit.SECONDS);
            var second = secondFuture.get(20, TimeUnit.SECONDS);

            assertEquals(first.getId(), second.getId());
            assertEquals("webp", first.getProcessorVersion().getDefinition().getSlug());
            assertEquals(1, enablementRepository
                    .findByProject_IdOrderByProcessorVersion_Definition_SlugAsc(project.getId()).size());
        } finally {
            executor.shutdownNow();
            enablementRepository.deleteAll(enablementRepository
                    .findByProject_IdOrderByProcessorVersion_Definition_SlugAsc(project.getId()));
            memberRepository.deleteById(new com.sluice.api.project.domain.ProjectMemberId(
                    owner.getId(), project.getId()));
            projectRepository.deleteById(project.getId());
            userRepository.deleteById(owner.getId());
        }
    }
}
