package com.keepbooking.history.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.keepbooking.history.model.SearchHistory;
import com.keepbooking.history.repository.SearchHistoryRepository;
import com.keepbooking.user.model.User;
import com.keepbooking.user.repository.UserRepository;

/**
 * record() is a silent no-op (not an error) for anonymous searches and for searches with
 * no filters at all — only saves when there's a logged-in user AND at least one filter.
 */
@ExtendWith(MockitoExtension.class)
class SearchHistoryServiceTest {

    @Mock
    private SearchHistoryRepository searchHistoryRepository;
    @Mock
    private UserRepository userRepository;

    private SearchHistoryService searchHistoryService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        searchHistoryService = new SearchHistoryService(searchHistoryRepository, userRepository);
    }

    @Test
    void recordSkipsSilentlyWhenUserIdIsNull() {
        searchHistoryService.record(null, "Pizza Place", null, null, null);

        verify(searchHistoryRepository, never()).save(any());
    }

    @Test
    void recordSkipsSilentlyWhenNoFiltersProvided() {
        searchHistoryService.record(USER_ID, null, null, null, null);

        verify(searchHistoryRepository, never()).save(any());
    }

    @Test
    void recordSkipsSilentlyWhenNameIsBlank() {
        searchHistoryService.record(USER_ID, "   ", null, null, null);

        verify(searchHistoryRepository, never()).save(any());
    }

    @Test
    void recordSavesWhenNameFilterProvided() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(User.builder().id(USER_ID).build());

        searchHistoryService.record(USER_ID, "Pizza Place", null, null, null);

        verify(searchHistoryRepository).save(any(SearchHistory.class));
    }

    @Test
    void recordSavesWhenOnlyCityIdFilterProvided() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(User.builder().id(USER_ID).build());

        searchHistoryService.record(USER_ID, null, null, 5L, null);

        verify(searchHistoryRepository).save(any(SearchHistory.class));
    }

    @Test
    void recordSavesWhenOnlyMinRatingFilterProvided() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(User.builder().id(USER_ID).build());

        searchHistoryService.record(USER_ID, null, null, null, new BigDecimal("4.0"));

        verify(searchHistoryRepository).save(any(SearchHistory.class));
    }
}
