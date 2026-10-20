package com.localspot.service;

import com.localspot.config.TrustProperties;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.ModerationQueueItemResponse;
import com.localspot.dto.response.ModerationQueueItemResponse.Flag;
import com.localspot.entity.ModerationItemType;
import com.localspot.entity.ReportTargetType;
import com.localspot.entity.User;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.ModerationQueueKey;
import com.localspot.repository.ModerationQueueQueries;
import com.localspot.repository.ModerationQueueRow;
import com.localspot.repository.PlaceClaimRepository;
import com.localspot.repository.UserRepository;
import com.localspot.security.Permissions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hàng chờ gộp {@code /moderation/queue} — bốn loại việc kiểm duyệt (UC27–UC30) trong một danh sách FIFO, chỉ gồm loại
 * người gọi có quyền. Chỉ đọc: xử lý từng mục vẫn qua {@code decision} của loại đó, nên mọi luật (khóa dòng, không tự
 * duyệt, vi phạm, thông báo) giữ nguyên một chỗ.
 */
@Service
public class ModerationQueueService {

    /** Permission cho phép thấy từng loại — đúng permission của endpoint {@code decision} tương ứng. */
    static final Map<ModerationItemType, String> PERMISSION_OF = new EnumMap<>(Map.of(
            ModerationItemType.PLACE, Permissions.PLACE_APPROVE,
            ModerationItemType.REVIEW, Permissions.REVIEW_MODERATE,
            ModerationItemType.REPORT, Permissions.REPORT_HANDLE,
            ModerationItemType.CLAIM, Permissions.CLAIM_APPROVE));

    private final ModerationQueueQueries queue;
    private final ReportTargets targets;
    private final PlaceClaimRepository claims;
    private final UserRepository users;
    private final TrustScoreService trust;
    private final TrustProperties trustProperties;

    public ModerationQueueService(
            ModerationQueueQueries queue,
            ReportTargets targets,
            PlaceClaimRepository claims,
            UserRepository users,
            TrustScoreService trust,
            TrustProperties trustProperties) {
        this.queue = queue;
        this.targets = targets;
        this.claims = claims;
        this.users = users;
        this.trust = trust;
        this.trustProperties = trustProperties;
    }

    /** Các loại {@code authorities} cho phép thấy. */
    public static Set<ModerationItemType> visibleTypes(Collection<String> authorities) {
        Set<ModerationItemType> types = EnumSet.noneOf(ModerationItemType.class);
        PERMISSION_OF.forEach((type, permission) -> {
            if (authorities.contains(permission)) {
                types.add(type);
            }
        });
        return types;
    }

    /**
     * Một trang hàng chờ. {@code type} lọc một loại; lọc loại không có quyền → 403 (thay vì trả rỗng — giao diện sẽ hiểu
     * nhầm là hết việc).
     */
    @Transactional(readOnly = true)
    public CursorPage<ModerationQueueItemResponse> page(
            Set<ModerationItemType> allowed, ModerationItemType type, String cursor, Integer limit) {
        if (type != null && !allowed.contains(type)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Bạn không có quyền xem loại việc này.");
        }
        Set<ModerationItemType> types = type == null ? allowed : EnumSet.of(type);
        int pageSize = KeysetCursor.limit(limit);
        ModerationQueueKey after = ModerationQueueCursor.decode(cursor);
        List<ModerationQueueRow> rows = queue.page(types, after, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<ModerationQueueRow> page = hasMore ? rows.subList(0, pageSize) : rows;
        // Số thứ tự tính lại theo hàng chờ hiện tại: mục phía trước đã được xử lý thì trang sau tự đánh số lùi lên
        long before = after == null ? 0 : queue.countUpTo(types, after);
        String next = hasMore ? ModerationQueueCursor.encode(page.getLast().key()) : null;
        return new CursorPage<>(toItems(page, before), next);
    }

    /** Mỗi loại dữ liệu kèm theo một truy vấn cho cả trang (tiêu đề theo loại, người gửi, trust) — không N+1. */
    private List<ModerationQueueItemResponse> toItems(List<ModerationQueueRow> page, long before) {
        if (page.isEmpty()) {
            return List.of();
        }
        Map<Long, User> submitters =
                users
                        .findAllById(page.stream()
                                .map(ModerationQueueRow::submitterId)
                                .distinct()
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Integer> trustScores = trust.trustScoresOf(page.stream()
                .filter(row -> row.type() == ModerationItemType.REVIEW)
                .map(row -> submitters.get(row.submitterId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        Titles titles = titlesOf(page);

        List<ModerationQueueItemResponse> items = new ArrayList<>(page.size());
        for (int i = 0; i < page.size(); i++) {
            ModerationQueueRow row = page.get(i);
            User submitter = submitters.get(row.submitterId());
            boolean report = row.type() == ModerationItemType.REPORT;
            items.add(new ModerationQueueItemResponse(
                    before + i + 1,
                    row.type(),
                    row.itemId(),
                    row.reportTargetType(),
                    titles.of(row),
                    submitter == null ? null : submitter.getDisplayName(),
                    row.submittedAt(),
                    flagsOf(row, trustScores),
                    report ? row.reportCount() : null));
        }
        return items;
    }

    private List<Flag> flagsOf(ModerationQueueRow row, Map<Long, Integer> trustScores) {
        if (row.type() != ModerationItemType.REVIEW) {
            return List.of();
        }
        List<Flag> flags = new ArrayList<>(2);
        if (row.ipFlagged()) {
            flags.add(Flag.IP_FLAGGED);
        }
        Integer score = trustScores.get(row.submitterId());
        if (score != null && score < trustProperties.publishThreshold()) {
            flags.add(Flag.LOW_TRUST);
        }
        return flags;
    }

    private Titles titlesOf(List<ModerationQueueRow> page) {
        Map<ModerationItemType, List<Long>> idsByType = new EnumMap<>(ModerationItemType.class);
        Map<ReportTargetType, List<Long>> reportIdsByType = new EnumMap<>(ReportTargetType.class);
        for (ModerationQueueRow row : page) {
            if (row.type() == ModerationItemType.REPORT) {
                reportIdsByType
                        .computeIfAbsent(row.reportTargetType(), t -> new ArrayList<>())
                        .add(row.itemId());
            } else {
                idsByType.computeIfAbsent(row.type(), t -> new ArrayList<>()).add(row.itemId());
            }
        }
        // Địa điểm / review chờ duyệt mô tả giống hệt khi bị báo cáo — dùng chung ReportTargets
        Map<Long, String> places = previews(ReportTargetType.PLACE, idsByType.get(ModerationItemType.PLACE));
        Map<Long, String> reviews = previews(ReportTargetType.REVIEW, idsByType.get(ModerationItemType.REVIEW));
        Map<Long, String> claimPlaces = new HashMap<>();
        List<Long> claimIds = idsByType.get(ModerationItemType.CLAIM);
        if (claimIds != null) {
            for (Object[] row : claims.findPlaceNamesByIdIn(claimIds)) {
                claimPlaces.put((Long) row[0], (String) row[1]);
            }
        }
        Map<ReportTargetType, Map<Long, String>> reported = new EnumMap<>(ReportTargetType.class);
        reportIdsByType.forEach((targetType, ids) -> reported.put(targetType, previews(targetType, ids)));
        return row -> switch (row.type()) {
            case PLACE -> places.get(row.itemId());
            case REVIEW -> reviews.get(row.itemId());
            case CLAIM -> claimPlaces.get(row.itemId());
            case REPORT -> reported.get(row.reportTargetType()).get(row.itemId());
        };
    }

    private Map<Long, String> previews(ReportTargetType type, List<Long> ids) {
        if (ids == null) {
            return Map.of();
        }
        return targets.load(type, ids).values().stream()
                .collect(Collectors.toMap(ReportTargets.Target::id, ReportTargets.Target::preview));
    }

    @FunctionalInterface
    private interface Titles {
        String of(ModerationQueueRow row);
    }
}
