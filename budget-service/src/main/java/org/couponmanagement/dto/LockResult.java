package org.couponmanagement.dto;

public record LockResult(String lockKey, String lockId, boolean acquired, String errorMessage) {

}
