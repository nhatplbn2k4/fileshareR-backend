package com.example.fileshareR.repository;

import com.example.fileshareR.entity.Group;
import com.example.fileshareR.enums.GroupVisibilityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    List<Group> findByVisibility(GroupVisibilityType visibility);

    List<Group> findByNameContainingIgnoreCaseAndVisibility(String keyword, GroupVisibilityType visibility);

    List<Group> findByNameContainingIgnoreCase(String keyword);

    List<Group> findByOwnerId(Long ownerId);

    Optional<Group> findByShareToken(String shareToken);
}
