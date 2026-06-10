package com.example.fileshareR.controller;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.response.SearchHistoryResponse;
import com.example.fileshareR.dto.response.SuggestionResponse;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.service.SearchService;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final UserService userService;

    /** Lịch sử tìm kiếm gần đây của user. */
    @GetMapping("/history")
    public ResponseEntity<List<SearchHistoryResponse>> getHistory() {
        return ResponseEntity.ok(searchService.getRecentSearches(getCurrentUserId()));
    }

    /** Xóa 1 mục lịch sử. */
    @DeleteMapping("/history/{id}")
    public ResponseEntity<Void> deleteHistoryItem(@PathVariable Long id) {
        searchService.deleteHistoryItem(getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Xóa toàn bộ lịch sử. */
    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory() {
        searchService.clearHistory(getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    /** Gợi ý autocomplete khi gõ. */
    @GetMapping("/suggest")
    public ResponseEntity<List<SuggestionResponse>> suggest(@RequestParam("q") String q) {
        return ResponseEntity.ok(searchService.suggest(getCurrentUserId(), q));
    }

    private Long getCurrentUserId() {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));
        User user = userService.getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return user.getId();
    }
}
