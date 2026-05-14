package com.example.fileshareR.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageAddonResponse {
    private Long id;
    private String code;
    private String name;
    private Long extraBytes;
    private Long priceVnd;
    private String description;
}
