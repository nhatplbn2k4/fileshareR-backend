package com.example.fileshareR.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "quota_bytes", nullable = false)
    private Long quotaBytes;

    @Column(name = "price_vnd", nullable = false)
    @Builder.Default
    private Long priceVnd = 0L;

    @Column(columnDefinition = "TEXT")
    private String description;
}
