package com.example.fileshareR.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một mục lịch sử tìm kiếm. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryResponse {
    private Long id;
    private String keyword;
}
