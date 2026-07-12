package com.keepbooking.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    INVALID_CREDENTIALS("AUTH_001", "Invalid email or password", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("AUTH_002", "Token has expired", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID("AUTH_003", "Token is invalid", HttpStatus.UNAUTHORIZED),
    EMAIL_NOT_VERIFIED("AUTH_004", "Email is not verified", HttpStatus.FORBIDDEN),
    EMAIL_ALREADY_EXISTS("AUTH_005", "Email already registered", HttpStatus.CONFLICT),

    // User
    USER_NOT_FOUND("USER_001", "User not found", HttpStatus.NOT_FOUND),
    USER_BLOCKED("USER_002", "User is blocked", HttpStatus.FORBIDDEN),

    // Company
    COMPANY_NOT_FOUND("COMPANY_001", "Company not found", HttpStatus.NOT_FOUND),
    ACCESS_DENIED("COMPANY_002", "Access denied to this resource", HttpStatus.FORBIDDEN),

    // Restaurant
    RESTAURANT_NOT_FOUND("REST_001", "Restaurant not found", HttpStatus.NOT_FOUND),
    RESTAURANT_NOT_ACTIVE("REST_002", "Restaurant is not active", HttpStatus.UNPROCESSABLE_ENTITY),

    // Hall / Table
    HALL_NOT_FOUND("HALL_001", "Hall not found", HttpStatus.NOT_FOUND),
    TABLE_NOT_FOUND("TABLE_001", "Table not found", HttpStatus.NOT_FOUND),
    TABLE_NOT_AVAILABLE("TABLE_002", "Table is not available for the requested time slot", HttpStatus.CONFLICT),

    // Menu
    MENU_ITEM_NOT_FOUND("MENU_001", "Menu item not found", HttpStatus.NOT_FOUND),

    // Restaurant photos
    RESTAURANT_PHOTO_NOT_FOUND("PHOTO_001", "Restaurant photo not found", HttpStatus.NOT_FOUND),

    // Reviews
    REVIEW_ALREADY_EXISTS("REVIEW_001", "A review already exists for this booking", HttpStatus.CONFLICT),
    REVIEW_BOOKING_NOT_COMPLETED("REVIEW_002", "Booking must be completed before leaving a review", HttpStatus.UNPROCESSABLE_ENTITY),
    REVIEW_NOT_FOUND("REVIEW_003", "Review not found", HttpStatus.NOT_FOUND),

    // Booking
    BOOKING_NOT_FOUND("BOOK_001", "Booking not found", HttpStatus.NOT_FOUND),
    BOOKING_INVALID_TIME("BOOK_002", "Booking time is invalid or in the past", HttpStatus.UNPROCESSABLE_ENTITY),
    BOOKING_GUEST_COUNT("BOOK_003", "Guest count exceeds table capacity", HttpStatus.UNPROCESSABLE_ENTITY),
    BOOKING_STATUS_TRANSITION("BOOK_004", "Invalid booking status transition", HttpStatus.UNPROCESSABLE_ENTITY),
    BOOKING_RESTAURANT_CLOSED("BOOK_005", "Restaurant is closed at the requested time", HttpStatus.UNPROCESSABLE_ENTITY),

    // File storage
    FILE_TOO_LARGE("FILE_001", "File exceeds the maximum allowed size", HttpStatus.PAYLOAD_TOO_LARGE),
    FILE_TYPE_NOT_ALLOWED("FILE_002", "File type is not allowed", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_UPLOAD_FAILED("FILE_003", "Failed to upload file", HttpStatus.INTERNAL_SERVER_ERROR),

    // Notifications
    NOTIFICATION_NOT_FOUND("NOTIF_001", "Notification not found", HttpStatus.NOT_FOUND),

    // Waitlist
    WAITLIST_ENTRY_NOT_FOUND("WAIT_001", "Waitlist entry not found", HttpStatus.NOT_FOUND),

    // Rate limiting
    RATE_LIMIT_EXCEEDED("RATE_001", "Too many requests, please try again later", HttpStatus.TOO_MANY_REQUESTS),

    // Generic
    VALIDATION_ERROR("VAL_001", "Validation failed", HttpStatus.BAD_REQUEST),
    NOT_FOUND("GEN_001", "Resource not found", HttpStatus.NOT_FOUND),
    INTERNAL_ERROR("GEN_002", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    DATA_CONFLICT("GEN_003", "A conflicting record already exists", HttpStatus.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    /**
     * RFC 7807 "type" - a stable, deterministic URI derived from the enum constant name
     * (e.g. TABLE_NOT_AVAILABLE -> .../errors/table-not-available), not a real dereferenceable
     * document, but a documented, code-stable identifier for the error kind.
     */
    public String getTypeUri() {
        return "https://keepbooking.dev/errors/" + name().toLowerCase().replace('_', '-');
    }
}
