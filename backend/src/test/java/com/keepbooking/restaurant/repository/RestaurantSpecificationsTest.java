package com.keepbooking.restaurant.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.keepbooking.restaurant.model.Restaurant;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Each optional filter must resolve to a null Predicate (i.e. "no restriction") when its
 * input is null/blank, since these Specifications are AND-combined and Spring Data drops
 * null predicates — a non-null "match nothing" predicate here would silently break search.
 */
@SuppressWarnings("unchecked")
class RestaurantSpecificationsTest {

    @Test
    void hasCityIdReturnsNullPredicateWhenCityIdIsNull() {
        Root<Restaurant> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        Predicate predicate = RestaurantSpecifications.hasCityId(null).toPredicate((Root) root, (CriteriaQuery) query, cb);

        assertThat(predicate).isNull();
        verifyNoInteractions(cb);
    }

    @Test
    void hasCityIdBuildsEqualityPredicateWhenCityIdProvided() {
        Root<Restaurant> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> cityPath = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        Predicate expected = mock(Predicate.class);
        when(root.<Object>get("city")).thenReturn(cityPath);
        when(cityPath.get("id")).thenReturn(idPath);
        when(cb.equal(idPath, 5L)).thenReturn(expected);

        Predicate predicate = RestaurantSpecifications.hasCityId(5L).toPredicate((Root) root, (CriteriaQuery) query, cb);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void nameContainsReturnsNullPredicateWhenBlank() {
        Root<Restaurant> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        Predicate predicate = RestaurantSpecifications.nameContains("   ").toPredicate((Root) root, (CriteriaQuery) query, cb);

        assertThat(predicate).isNull();
        verifyNoInteractions(cb);
    }

    @Test
    void nameContainsBuildsCaseInsensitiveLikePredicateWhenProvided() {
        Root<Restaurant> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<String> namePath = mock(Path.class);
        jakarta.persistence.criteria.Expression<String> lowerExpr = mock(jakarta.persistence.criteria.Expression.class);
        Predicate expected = mock(Predicate.class);
        when(root.<String>get("name")).thenReturn(namePath);
        when(cb.lower(namePath)).thenReturn(lowerExpr);
        when(cb.like(lowerExpr, "%pizza%")).thenReturn(expected);

        Predicate predicate = RestaurantSpecifications.nameContains("Pizza").toPredicate((Root) root, (CriteriaQuery) query, cb);

        assertThat(predicate).isSameAs(expected);
        verify(cb).like(lowerExpr, "%pizza%");
    }

    @Test
    void hasCuisineSlugReturnsNullPredicateWhenBlank() {
        Root<Restaurant> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        Predicate predicate = RestaurantSpecifications.hasCuisineSlug("").toPredicate((Root) root, (CriteriaQuery) query, cb);

        assertThat(predicate).isNull();
        verifyNoInteractions(cb);
        verifyNoInteractions(query);
    }

    @Test
    void hasCuisineSlugJoinsAndMarksQueryDistinctWhenProvided() {
        Root<Restaurant> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Join<Object, Object> join = mock(Join.class);
        Path<Object> slugPath = mock(Path.class);
        Predicate expected = mock(Predicate.class);
        when(root.<Object, Object>join("cuisines")).thenReturn(join);
        when(join.get("slug")).thenReturn(slugPath);
        when(cb.equal(slugPath, "italian")).thenReturn(expected);

        Predicate predicate = RestaurantSpecifications.hasCuisineSlug("italian").toPredicate((Root) root, (CriteriaQuery) query, cb);

        assertThat(predicate).isSameAs(expected);
        verify(query).distinct(true);
    }

    @Test
    void hasMinRatingReturnsNullPredicateWhenMinRatingIsNull() {
        Root<Restaurant> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        Predicate predicate = RestaurantSpecifications.hasMinRating(null).toPredicate((Root) root, (CriteriaQuery) query, cb);

        assertThat(predicate).isNull();
        verifyNoInteractions(cb);
    }

    @Test
    void hasMinRatingBuildsGreaterThanOrEqualPredicateWhenProvided() {
        Root<Restaurant> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<BigDecimal> ratingPath = mock(Path.class);
        Predicate expected = mock(Predicate.class);
        BigDecimal minRating = new BigDecimal("4.0");
        when(root.<BigDecimal>get("rating")).thenReturn(ratingPath);
        when(cb.greaterThanOrEqualTo(ratingPath, minRating)).thenReturn(expected);

        Predicate predicate = RestaurantSpecifications.hasMinRating(minRating).toPredicate((Root) root, (CriteriaQuery) query, cb);

        assertThat(predicate).isSameAs(expected);
    }

    @Test
    void isActiveBuildsEqualityPredicateOnStatus() {
        Root<Restaurant> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> statusPath = mock(Path.class);
        Predicate expected = mock(Predicate.class);
        when(root.<Object>get("status")).thenReturn(statusPath);
        when(cb.equal(statusPath, com.keepbooking.restaurant.model.RestaurantStatus.ACTIVE)).thenReturn(expected);

        Predicate predicate = RestaurantSpecifications.isActive().toPredicate((Root) root, (CriteriaQuery) query, cb);

        assertThat(predicate).isSameAs(expected);
    }
}
