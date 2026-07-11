package com.keepbooking.history.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.history.dto.SearchHistoryDto;
import com.keepbooking.history.model.SearchHistory;
import com.keepbooking.history.repository.SearchHistoryRepository;
import com.keepbooking.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SearchHistoryService {

    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public void record(Long userId, String name, String cuisineSlug, Long cityId, BigDecimal minRating) {
        boolean hasAnyFilter = hasText(name) || hasText(cuisineSlug) || cityId != null || minRating != null;
        if (userId == null || !hasAnyFilter) {
            return;
        }

        SearchHistory entry = SearchHistory.builder()
                .user(userRepository.getReferenceById(userId))
                .name(name)
                .cuisineSlug(cuisineSlug)
                .cityId(cityId)
                .minRating(minRating)
                .build();
        searchHistoryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<SearchHistoryDto> getMyHistory(Long userId, Pageable pageable) {
        Page<SearchHistory> page = searchHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private SearchHistoryDto toDto(SearchHistory h) {
        return SearchHistoryDto.builder()
                .id(h.getId())
                .name(h.getName())
                .cuisineSlug(h.getCuisineSlug())
                .cityId(h.getCityId())
                .minRating(h.getMinRating())
                .createdAt(h.getCreatedAt())
                .build();
    }
}
