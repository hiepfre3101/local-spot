package com.localspot.service;

import com.localspot.config.CheckInProperties;
import com.localspot.dto.request.CheckInRequest;
import com.localspot.dto.response.CheckInResponse;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.entity.CheckIn;
import com.localspot.entity.GeoPoints;
import com.localspot.entity.Place;
import com.localspot.entity.PlaceStatus;
import com.localspot.entity.User;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.CheckInRepository;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Check-in (UC16, FR-24, U5). Thứ tự kiểm tra: email đã xác thực (403) → độ chính xác GPS (422 {@code accuracyM}) →
 * địa điểm đã duyệt (404) → không phải chủ địa điểm (409) → chưa check-in hôm nay (409) → trong bán kính (422 kèm
 * {@code distanceM}). Lỗi rẻ và lỗi của dữ liệu gửi lên trước, phép tính khoảng cách cuối cùng.
 *
 * <p>Chủ không check-in địa điểm của mình và cần email đã xác thực (chốt 2026-10-12): {@code checkin_count} vào thống
 * kê của chủ (FR-34) và nhãn "đã check-in" trên review — không để chủ hay tài khoản rác thổi phồng.
 *
 * <p>GPS có thể bị giả lập phía client (UC16 — hạn chế ghi trong Chương 5): check-in là một lớp tín hiệu, không phải bằng
 * chứng tuyệt đối.
 */
@Service
public class CheckInService {

    private final CheckInRepository checkIns;
    private final PlaceRepository places;
    private final UserRepository users;
    private final PlaceSummaries summaries;
    private final CheckInProperties properties;
    private final Clock clock;

    public CheckInService(
            CheckInRepository checkIns,
            PlaceRepository places,
            UserRepository users,
            PlaceSummaries summaries,
            CheckInProperties properties,
            Clock clock) {
        this.checkIns = checkIns;
        this.places = places;
        this.users = users;
        this.summaries = summaries;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public CheckInResponse checkIn(Long userId, Long placeId, CheckInRequest request) {
        // Khóa dòng người dùng: hai lần bấm gần nhau xếp hàng ở đây → lần sau thấy lần trước và nhận 409 có mã riêng,
        // không chạm UNIQUE của CSDL (DUPLICATE_RESOURCE chung chung)
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng."));
        if (!user.isEmailVerified()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, ErrorCode.EMAIL_NOT_VERIFIED, "Hãy xác thực email trước khi check-in.");
        }
        if (request.accuracyM() > properties.maxAccuracyM()) {
            throw ApiException.fieldError(
                    ErrorCode.GPS_INACCURATE,
                    "accuracyM",
                    "Vị trí chưa đủ chính xác (sai số " + (int) Math.ceil(request.accuracyM())
                            + " m). Hãy bật GPS hoặc ra nơi thoáng rồi thử lại.");
        }
        Place place = places.findById(placeId)
                .filter(p -> p.getStatus() == PlaceStatus.APPROVED)
                .orElseThrow(PlaceService::placeNotFound);
        if (place.getOwner() != null && place.getOwner().getId().equals(userId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.SELF_ACTION_FORBIDDEN,
                    "Chủ địa điểm không thể check-in địa điểm của mình.");
        }
        LocalDate today = LocalDate.now(clock.withZone(PlaceService.VIEW_DATE_ZONE));
        if (checkIns.existsByUserIdAndPlaceIdAndCheckinDate(userId, placeId, today)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.CHECKIN_ALREADY_TODAY,
                    "Bạn đã check-in địa điểm này hôm nay. Hãy quay lại vào ngày mai.");
        }
        double distance = places.distanceMeters(placeId, wkt(request.lat(), request.lng()));
        if (distance > properties.radiusM()) {
            int shown = (int) Math.ceil(distance); // làm tròn lên: 200.4 m không hiện thành "200 m" mà vẫn bị từ chối
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    ErrorCode.CHECKIN_TOO_FAR,
                    "Bạn đang ở quá xa địa điểm (" + shown + " m). Check-in cần ở trong phạm vi " + properties.radiusM()
                            + " m.",
                    List.of(),
                    Map.of("distanceM", shown));
        }
        CheckIn checkIn = checkIns.save(new CheckIn(
                user,
                place,
                GeoPoints.of(request.lat(), request.lng()),
                (int) Math.ceil(request.accuracyM()),
                (int) Math.round(distance),
                today));
        places.incrementCheckinCount(placeId);
        return toResponses(List.of(checkIn)).getFirst();
    }

    /** {@code GET /me/check-ins} — chỉ chính chủ (chốt 2026-10-12: lịch sử check-in lộ nơi và lúc một người có mặt). */
    @Transactional(readOnly = true)
    public CursorPage<CheckInResponse> mine(Long userId, String cursor, Integer limit) {
        int pageSize = KeysetCursor.limit(limit);
        List<CheckIn> rows = checkIns.findByUser(userId, KeysetCursor.decode(cursor), Limit.of(pageSize + 1));
        return KeysetPages.byId(rows, pageSize, CheckIn::getId, this::toResponses);
    }

    private List<CheckInResponse> toResponses(List<CheckIn> page) {
        List<PlaceSummaryResponse> placeCards =
                summaries.of(page.stream().map(CheckIn::getPlace).toList());
        return IntStream.range(0, page.size())
                .mapToObj(i -> {
                    CheckIn c = page.get(i);
                    return new CheckInResponse(
                            c.getId(),
                            c.getPlace().getId(),
                            placeCards.get(i),
                            c.getDistanceM(),
                            c.getCheckinDate(),
                            c.getCreatedAt());
                })
                .toList();
    }

    /** Thứ tự trục (lat lng) của SRID 4326 trong MySQL 8 — như {@code PlaceSearchRepositoryImpl}. */
    private static String wkt(double lat, double lng) {
        return String.format(Locale.ROOT, "POINT(%.7f %.7f)", lat, lng);
    }
}
