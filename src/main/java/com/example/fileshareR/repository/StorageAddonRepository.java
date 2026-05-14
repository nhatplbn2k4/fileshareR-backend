package com.example.fileshareR.repository;

import com.example.fileshareR.entity.StorageAddon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StorageAddonRepository extends JpaRepository<StorageAddon, Long> {
    Optional<StorageAddon> findByCode(String code);
}
