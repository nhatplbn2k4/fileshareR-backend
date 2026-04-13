package com.example.fileshareR.repository;

import com.example.fileshareR.entity.GroupFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupFolderRepository extends JpaRepository<GroupFolder, Long> {

    List<GroupFolder> findByGroupIdAndParentIsNull(Long groupId);

    List<GroupFolder> findByGroupIdAndParentId(Long groupId, Long parentId);

    List<GroupFolder> findByGroupId(Long groupId);

    Optional<GroupFolder> findByShareToken(String shareToken);
}
