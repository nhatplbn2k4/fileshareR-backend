package com.example.fileshareR.repository;

import com.example.fileshareR.entity.GroupPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupPurchaseRepository extends JpaRepository<GroupPurchase, Long> {
    List<GroupPurchase> findByGroupIdOrderByPurchasedAtDesc(Long groupId);
}
