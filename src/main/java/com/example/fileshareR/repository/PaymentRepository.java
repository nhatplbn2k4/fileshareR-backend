package com.example.fileshareR.repository;

import com.example.fileshareR.entity.Payment;
import com.example.fileshareR.enums.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTxnRef(String txnRef);

    Optional<Payment> findByProviderAndProviderTxnId(PaymentProvider provider, String providerTxnId);

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);
}
