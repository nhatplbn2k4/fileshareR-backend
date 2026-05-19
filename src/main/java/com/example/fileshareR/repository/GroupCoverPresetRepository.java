package com.example.fileshareR.repository;

import com.example.fileshareR.entity.GroupCoverPreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupCoverPresetRepository extends JpaRepository<GroupCoverPreset, Long> {

    List<GroupCoverPreset> findByIsActiveTrueOrderByDisplayOrderAscIdAsc();

    List<GroupCoverPreset> findAllByOrderByDisplayOrderAscIdAsc();
}
