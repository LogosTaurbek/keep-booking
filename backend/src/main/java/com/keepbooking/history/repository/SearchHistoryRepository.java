package com.keepbooking.history.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.history.model.SearchHistory;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    Page<SearchHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
