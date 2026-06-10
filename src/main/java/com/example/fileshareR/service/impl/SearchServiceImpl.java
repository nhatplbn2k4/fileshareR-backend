package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.response.SearchHistoryResponse;
import com.example.fileshareR.dto.response.SuggestionResponse;
import com.example.fileshareR.entity.SearchHistory;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.repository.SearchHistoryRepository;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SearchServiceImpl implements SearchService {

    private static final int SUGGEST_DOC_LIMIT = 6;
    private static final int SUGGEST_GROUP_LIMIT = 4;
    private static final int SUGGEST_TOTAL_LIMIT = 8;

    private final SearchHistoryRepository searchHistoryRepository;
    private final DocumentRepository documentRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    @Override
    public void recordSearch(Long userId, String keyword, int resultCount) {
        if (userId == null || keyword == null) return;
        String kw = keyword.trim();
        if (kw.isEmpty()) return;
        if (kw.length() > 255) kw = kw.substring(0, 255);

        // Upsert: xóa bản ghi cùng từ khóa (nếu có) rồi chèn mới để đẩy lên đầu danh sách.
        searchHistoryRepository.findFirstByUserIdAndKeywordIgnoreCase(userId, kw)
                .ifPresent(searchHistoryRepository::delete);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        searchHistoryRepository.save(SearchHistory.builder()
                .user(user)
                .keyword(kw)
                .resultCount(Math.max(0, resultCount))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHistoryResponse> getRecentSearches(Long userId) {
        return searchHistoryRepository.findRecentSearches(userId).stream()
                .map(sh -> SearchHistoryResponse.builder()
                        .id(sh.getId())
                        .keyword(sh.getKeyword())
                        .build())
                .toList();
    }

    @Override
    public void deleteHistoryItem(Long userId, Long id) {
        searchHistoryRepository.deleteByUserIdAndId(userId, id);
    }

    @Override
    public void clearHistory(Long userId) {
        searchHistoryRepository.deleteAllByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SuggestionResponse> suggest(Long userId, String query) {
        if (query == null) return List.of();
        String q = query.trim();
        if (q.isEmpty()) return List.of();

        List<SuggestionResponse> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>(); // tránh trùng text (không phân biệt hoa thường)

        for (String title : documentRepository.suggestTitles(userId, q, PageRequest.of(0, SUGGEST_DOC_LIMIT))) {
            if (title != null && seen.add(title.toLowerCase())) {
                result.add(SuggestionResponse.builder().text(title).type("DOCUMENT").build());
            }
        }
        for (String name : groupRepository.suggestNames(userId, q, PageRequest.of(0, SUGGEST_GROUP_LIMIT))) {
            if (name != null && seen.add(name.toLowerCase())) {
                result.add(SuggestionResponse.builder().text(name).type("GROUP").build());
            }
        }

        return result.size() > SUGGEST_TOTAL_LIMIT ? result.subList(0, SUGGEST_TOTAL_LIMIT) : result;
    }
}
