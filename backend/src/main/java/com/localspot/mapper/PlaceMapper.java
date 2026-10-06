package com.localspot.mapper;

import com.localspot.dto.response.AmenityResponse;
import com.localspot.dto.response.CategoryRef;
import com.localspot.dto.response.GeoPoint;
import com.localspot.dto.response.OpeningHourResponse;
import com.localspot.dto.response.PhotoResponse;
import com.localspot.dto.response.PlaceDetailResponse;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.dto.response.UserSummary;
import com.localspot.entity.Amenity;
import com.localspot.entity.Category;
import com.localspot.entity.GeoPoints;
import com.localspot.entity.OpeningHour;
import com.localspot.entity.Place;
import com.localspot.entity.User;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Point;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Place ↔ DTO. Phần phụ thuộc người xem / bảng khác (thẻ, phân bố sao, ảnh — URL ảnh cần cấu hình kho lưu trữ) do service
 * tính rồi truyền vào ({@link DetailExtras}); ảnh bìa của thẻ gắn ở {@code PlaceSummaries} — mapper không truy vấn.
 */
@Mapper
public interface PlaceMapper {

    DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @Mapping(target = "coverPhoto", ignore = true)
    @Mapping(target = "distanceM", ignore = true)
    PlaceSummaryResponse toSummary(Place place);

    List<PlaceSummaryResponse> toSummaries(List<Place> places);

    CategoryRef toCategoryRef(Category category);

    UserSummary toUserSummary(User user);

    AmenityResponse toAmenity(Amenity amenity);

    default GeoPoint toGeoPoint(Point point) {
        return point == null ? null : new GeoPoint(GeoPoints.lat(point), GeoPoints.lng(point));
    }

    default OpeningHourResponse toOpeningHour(OpeningHour hour) {
        return new OpeningHourResponse(
                hour.getDayOfWeek().getValue(),
                hour.getOpenTime().format(HH_MM),
                hour.getCloseTime().format(HH_MM));
    }

    default PlaceDetailResponse toDetail(Place place, DetailExtras extras) {
        return new PlaceDetailResponse(
                place.getId(),
                place.getSlug(),
                place.getName(),
                toCategoryRef(place.getCategory()),
                place.getAddress(),
                toGeoPoint(place.getLocation()),
                extras.coverPhoto(),
                place.getPriceMin(),
                place.getPriceMax(),
                place.getBayesianScore(),
                place.getAvgRating(),
                place.getReviewCount(),
                place.getStatus().name(),
                null,
                place.getDescription(),
                place.getCity(),
                place.getPhone(),
                place.getWebsite(),
                extras.photos(),
                place.getOpeningHours().stream().map(this::toOpeningHour).toList(),
                place.getAmenities().stream()
                        .sorted(Comparator.comparing(Amenity::getId))
                        .map(this::toAmenity)
                        .toList(),
                extras.tags(),
                extras.ratingDistribution(),
                place.getCheckinCount(),
                place.getOwner() == null ? null : toUserSummary(place.getOwner()),
                extras.claimable(),
                extras.myReviewId(),
                extras.showRejectReason() ? place.getRejectReason() : null,
                place.getVersion());
    }

    /**
     * Phần chi tiết không đọc được từ entity {@link Place}.
     *
     * @param showRejectReason lý do từ chối chỉ hiện cho người đề xuất / kiểm duyệt viên
     * @param coverPhoto ảnh bìa đã xử lý xong, {@code null} nếu chưa có
     * @param photos gallery đã lọc theo người xem
     */
    record DetailExtras(
            List<String> tags,
            Map<Integer, Long> ratingDistribution,
            boolean claimable,
            Long myReviewId,
            boolean showRejectReason,
            PhotoResponse coverPhoto,
            List<PhotoResponse> photos) {

        public DetailExtras {
            tags = List.copyOf(tags);
            ratingDistribution = Map.copyOf(ratingDistribution);
            photos = List.copyOf(photos);
        }
    }
}
