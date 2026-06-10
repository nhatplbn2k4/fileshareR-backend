package com.example.fileshareR.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một gợi ý autocomplete khi gõ tìm kiếm. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionResponse {
    private String text;   // chuỗi gợi ý
    private String type;   // DOCUMENT | GROUP
}
