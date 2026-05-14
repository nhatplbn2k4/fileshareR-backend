package com.example.fileshareR.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "storage_addons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageAddon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "extra_bytes", nullable = false)
    private Long extraBytes;

    @Column(name = "price_vnd", nullable = false)
    private Long priceVnd;

    @Column(columnDefinition = "TEXT")
    private String description;
}
