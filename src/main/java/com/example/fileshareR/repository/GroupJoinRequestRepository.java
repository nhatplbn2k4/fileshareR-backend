package com.example.fileshareR.repository;

import com.example.fileshareR.entity.GroupJoinRequest;
import com.example.fileshareR.enums.JoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupJoinRequestRepository extends JpaRepository<GroupJoinRequest, Long> {
    List<GroupJoinRequest> findByGroupIdAndStatus(Long groupId, JoinRequestStatus status);
    Optional<GroupJoinRequest> findByGroupIdAndUserId(Long groupId, Long userId);
    Optional<GroupJoinRequest> findByGroupIdAndUserIdAndStatus(Long groupId, Long userId, JoinRequestStatus status);
    boolean existsByGroupIdAndUserIdAndStatus(Long groupId, Long userId, JoinRequestStatus status);
}
