package com.localspot.amqp;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Message trên queue {@code photo.process}: loại ảnh + id — ảnh gốc nằm ở kho object ({@code incoming/…}), không đưa vài
 * MB nhị phân qua RabbitMQ. Ảnh địa điểm (E2) và ảnh review (E7) dùng chung pipeline.
 *
 * @param target {@code null} = message cũ trước E7 (chỉ có {@code placePhotoId}) → ảnh địa điểm
 */
public record PhotoProcessMessage(
        Target target, @JsonAlias("placePhotoId") long photoId) {

    public enum Target {
        PLACE,
        REVIEW
    }

    public PhotoProcessMessage {
        target = target == null ? Target.PLACE : target;
    }
}
