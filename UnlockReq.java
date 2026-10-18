package com.astrax.elementer;

/** Điều kiện mở khóa một nguyên tố mới: cấp tối thiểu và số điểm kỹ năng phải trả. */
public record UnlockReq(int level, int points) {
}
