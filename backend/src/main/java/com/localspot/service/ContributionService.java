package com.localspot.service;

import com.localspot.config.CacheConfig;
import com.localspot.dto.response.LeaderboardEntryResponse;
import com.localspot.dto.response.UserSummary;
import com.localspot.entity.User;
import com.localspot.mapper.CommunityMapper;
import com.localspot.repository.ContributionQueries;
import com.localspot.repository.ContributionQueries.Ranked;
import com.localspot.repository.UserBadgeRepository;
import com.localspot.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Điểm đóng góp, huy hiệu, bảng xếp hạng (FR-29, O3 — chốt 2026-10-12). Công thức điểm ở {@link ContributionQueries}
 * (tính từ dữ liệu gốc); tiêu chí huy hiệu ở {@link BadgeRule}.
 *
 * <ul>
 *   <li>{@code users.contribution_points} là <b>bộ nhớ đệm</b> của tổng toàn thời gian cho hồ sơ — {@link #refresh}
 *       đếm lại từ đầu sau mỗi sự kiện (listener sau commit) và job đêm đối soát, như rating (E9): idempotent, mất sự
 *       kiện thì lần sau tự đúng.
 *   <li>Bảng xếp hạng luôn tính từ dữ liệu gốc (tuần / tháng / thành phố không có cột lưu sẵn), cache Redis 5 phút.
 *   <li>Tuần / tháng là cửa sổ <b>trượt</b> 7 / 30 ngày — không về rỗng vào đầu tuần / tháng.
 * </ul>
 */
@Service
public class ContributionService {

    public enum Period {
        WEEK(Duration.ofDays(7)),
        MONTH(Duration.ofDays(30)),
        ALL(null);

        private final Duration window;

        Period(Duration window) {
            this.window = window;
        }

        Instant from(Instant now) {
            return window == null ? ContributionQueries.BEGINNING : now.minus(window);
        }
    }

    private final ContributionQueries queries;
    private final UserRepository users;
    private final UserBadgeRepository userBadges;
    private final CommunityMapper mapper;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ContributionService(
            ContributionQueries queries,
            UserRepository users,
            UserBadgeRepository userBadges,
            CommunityMapper mapper,
            TransactionTemplate tx,
            Clock clock) {
        this.queries = queries;
        this.users = users;
        this.userBadges = userBadges;
        this.mapper = mapper;
        this.tx = tx;
        this.clock = clock;
    }

    /** Tính lại điểm và trao huy hiệu còn thiếu cho một người — trả số huy hiệu mới. */
    public int refresh(long userId) {
        Integer awarded = tx.execute(status -> {
            users.updateContributionPoints(userId, queries.totalOf(userId));
            List<String> reached = BadgeRule.reached(queries.badgeStatsOf(userId));
            return reached.isEmpty() ? 0 : userBadges.award(userId, reached, clock.instant());
        });
        return awarded == null ? 0 : awarded;
    }

    /** Job đêm / khởi động demo: mỗi người một transaction ngắn. Trả tổng số huy hiệu mới. */
    public int refreshAll() {
        int awarded = 0;
        for (Long userId : users.findAllActiveIds()) {
            awarded += refresh(userId);
        }
        return awarded;
    }

    /** {@code city} rỗng = toàn quốc. Cache theo (kỳ, thành phố, số dòng); hết hạn sau 5 phút (CacheConfig). */
    @Cacheable(cacheNames = CacheConfig.LEADERBOARD, key = "#period.name() + '|' + (#city ?: '') + '|' + #limit")
    public List<LeaderboardEntryResponse> leaderboard(Period period, String city, int limit) {
        List<Ranked> ranked = queries.leaderboard(period.from(clock.instant()), city, limit);
        Map<Long, UserSummary> people = users
                .findAllById(ranked.stream().map(Ranked::userId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, mapper::toUserSummary));
        return ranked.stream()
                .filter(r -> people.containsKey(r.userId()))
                .map(r -> new LeaderboardEntryResponse(r.rank(), people.get(r.userId()), r.points()))
                .toList();
    }
}
