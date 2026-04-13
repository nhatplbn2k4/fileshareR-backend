package com.example.fileshareR.repository;

import com.example.fileshareR.entity.Folder;
import com.example.fileshareR.enums.FolderVisibilityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByUserId(Long userId);
    List<Folder> findByUserIdAndParentId(Long userId, Long parentId);
    List<Folder> findByUserIdAndParentIsNull(Long userId);
    Optional<Folder> findByShareToken(String shareToken);
    List<Folder> findByParentId(Long parentId);
    long countByUserIdAndVisibility(Long userId, FolderVisibilityType visibility);
    List<Folder> findByUserIdAndVisibility(Long userId, FolderVisibilityType visibility);
}
