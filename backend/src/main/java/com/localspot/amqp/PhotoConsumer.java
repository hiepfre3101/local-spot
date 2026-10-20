package com.localspot.amqp;

import com.localspot.config.RabbitConfig;
import com.localspot.service.PlacePhotoService;
import com.localspot.service.ReviewPhotoService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Xử lý ảnh từ queue {@code photo.process} (FR-16). Lỗi kho object → ném ra để listener retry, hết lượt sang DLQ
 * (NFR-13); ảnh hỏng được đánh dấu FAILED, không retry. Ảnh địa điểm và ảnh review dùng chung queue, phân theo
 * {@link PhotoProcessMessage#target()}.
 */
@Component
public class PhotoConsumer {

    private final PlacePhotoService placePhotos;
    private final ReviewPhotoService reviewPhotos;

    public PhotoConsumer(PlacePhotoService placePhotos, ReviewPhotoService reviewPhotos) {
        this.placePhotos = placePhotos;
        this.reviewPhotos = reviewPhotos;
    }

    @RabbitListener(queues = RabbitConfig.PHOTO_QUEUE)
    public void process(PhotoProcessMessage message) {
        switch (message.target()) {
            case PLACE -> placePhotos.process(message.photoId());
            case REVIEW -> reviewPhotos.process(message.photoId());
        }
    }
}
