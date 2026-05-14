package com.example.fileshareR.repository;

import com.example.fileshareR.entity.UserPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPurchaseRepository extends JpaRepository<UserPurchase, Long> {
    List<UserPurchase> findByUserIdOrderByPurchasedAtDesc(Long userId);
}
