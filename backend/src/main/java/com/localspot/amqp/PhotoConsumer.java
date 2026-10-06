package com.localspot.amqp;

import com.localspot.config.RabbitConfig;
import com.localspot.service.PlacePhotoService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Xử lý ảnh từ queue {@code photo.process} (FR-16). Lỗi kho object → ném ra để listener retry, hết lượt sang DLQ
 * (NFR-13); ảnh hỏng được {@link PlacePhotoService#process} đánh dấu FAILED, không retry.
 */
@Component
public class PhotoConsumer {

    private final PlacePhotoService photos;

    public PhotoConsumer(PlacePhotoService photos) {
        this.photos = photos;
    }

    @RabbitListener(queues = RabbitConfig.PHOTO_QUEUE)
    public void process(PhotoProcessMessage message) {
        photos.process(message.placePhotoId());
    }
}
