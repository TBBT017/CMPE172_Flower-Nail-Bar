package com.flowernailbar.service;

/**
 * ConcurrentBookingException — thrown when optimistic locking detects
 * that a slot was booked by another transaction before ours could complete.
 *
 * This prevents double-booking in race conditions (M4 concurrency design).
 */
public class ConcurrentBookingException extends RuntimeException {
    public ConcurrentBookingException(String message) {
        super(message);
    }
}
