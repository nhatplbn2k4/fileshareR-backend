package com.example.fileshareR.service;

import com.example.fileshareR.dto.response.SearchHistoryResponse;
import com.example.fileshareR.dto.response.SuggestionResponse;

import java.util.List;

public interface SearchService {

    /** Ghi (hoặc cập nhật) lịch sử tìm kiếm của user. Bỏ qua nếu keyword rỗng. */
    void recordSearch(Long userId, String keyword, int resultCount);

    /** 10 lần tìm gần nhất (mỗi từ khóa 1 lần). */
    List<SearchHistoryResponse> getRecentSearches(Long userId);

    /** Xóa 1 mục lịch sử theo id (chỉ của user đó). */
    void deleteHistoryItem(Long userId, Long id);

    /** Xóa toàn bộ lịch sử của user. */
    void clearHistory(Long userId);

    /** Gợi ý autocomplete khi gõ: tiêu đề tài liệu + tên nhóm khớp. */
    List<SuggestionResponse> suggest(Long userId, String query);
}
