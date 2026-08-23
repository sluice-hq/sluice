package com.sluice.api.project.repository;

import com.sluice.api.project.domain.ProjectMember;
import com.sluice.api.project.domain.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {
    Optional<ProjectMember> findByUserIdAndProjectId(UUID userId, UUID projectId);
    List<ProjectMember> findByUserId(UUID userId);
}
