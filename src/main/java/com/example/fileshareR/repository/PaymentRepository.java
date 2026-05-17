package com.example.fileshareR.repository;

import com.example.fileshareR.entity.Payment;
import com.example.fileshareR.enums.PaymentProvider;
import com.example.fileshareR.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTxnRef(String txnRef);

    Optional<Payment> findByProviderAndProviderTxnId(PaymentProvider provider, String providerTxnId);

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Admin paged + filtered list. Search matches txn_ref / providerTxnId /
     * owner email. Filters are AND-ed; null means "no filter".
     */
    @Query("""
        SELECT p FROM Payment p
        WHERE (:search IS NULL OR :search = ''
               OR LOWER(p.txnRef) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(COALESCE(p.providerTxnId, '')) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.user.email) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:provider IS NULL OR p.provider = :provider)
          AND (:status IS NULL OR p.status = :status)
        """)
    Page<Payment> findAllForAdmin(
            @Param("search") String search,
            @Param("provider") PaymentProvider provider,
            @Param("status") PaymentStatus status,
            Pageable pageable);
}
