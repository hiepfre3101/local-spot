package com.localspot.service;

import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.PublicProfileResponse;
import com.localspot.dto.response.UserSummary;
import com.localspot.entity.Follow;
import com.localspot.entity.User;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.mapper.CommunityMapper;
import com.localspot.repository.ContributionQueries;
import com.localspot.repository.FollowRepository;
import com.localspot.repository.UserBadgeRepository;
import com.localspot.repository.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Theo dõi người dùng (UC20, FR-28) và hồ sơ công khai (P06). Danh sách theo dõi công khai (sitemap P06). Không thông
 * báo khi có người theo dõi — làm cùng mục thông báo.
 */
@Service
public class CommunityService {

    private final FollowRepository follows;
    private final UserRepository users;
    private final UserBadgeRepository userBadges;
    private final ContributionQueries contributions;
    private final CommunityMapper mapper;
    private final Clock clock;

    public CommunityService(
            FollowRepository follows,
            UserRepository users,
            UserBadgeRepository userBadges,
            ContributionQueries contributions,
            CommunityMapper mapper,
            Clock clock) {
        this.follows = follows;
        this.users = users;
        this.userBadges = userBadges;
        this.contributions = contributions;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Idempotent. Tự theo dõi mình → 409 như các thao tác tự-mình khác (vote review của mình); CSDL có CHECK phòng hờ. */
    @Transactional
    public void follow(Long followerId, Long targetId) {
        if (followerId.equals(targetId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ErrorCode.SELF_ACTION_FORBIDDEN, "Không thể tự theo dõi chính mình.");
        }
        if (!users.existsById(targetId)) {
            throw userNotFound();
        }
        follows.follow(followerId, targetId, clock.instant());
    }

    /** Idempotent — kể cả khi người kia đã bị xóa. */
    @Transactional
    public void unfollow(Long followerId, Long targetId) {
        follows.unfollow(followerId, targetId);
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummary> followers(Long userId, String cursor, Integer limit) {
        return page(userId, cursor, limit, true);
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummary> following(Long userId, String cursor, Integer limit) {
        return page(userId, cursor, limit, false);
    }

    /** {@code GET /users/{id}} — không có email (NFR-11); điểm là bộ nhớ đệm do {@link ContributionService} ghi. */
    @Transactional(readOnly = true)
    public PublicProfileResponse profile(Long userId, Long viewerId) {
        User user = users.findById(userId).orElseThrow(CommunityService::userNotFound);
        UserSummary summary = mapper.toUserSummary(user);
        return new PublicProfileResponse(
                summary.id(),
                summary.displayName(),
                summary.avatarUrl(),
                user.getBio(),
                user.getCreatedAt(),
                contributions.badgeStatsOf(userId).getOrDefault("reviews", 0),
                follows.countFollowers(userId),
                follows.countFollowing(userId),
                user.getContributionPoints(),
                userBadges.findByUser(userId).stream().map(mapper::toBadge).toList(),
                viewerId != null && follows.existsByIdFollowerIdAndIdFollowingId(viewerId, userId));
    }

    private CursorPage<UserSummary> page(Long userId, String cursor, Integer limit, boolean followersOf) {
        if (!users.existsById(userId)) {
            throw userNotFound();
        }
        int pageSize = KeysetCursor.limit(limit);
        FollowCursor.Position after = FollowCursor.decode(cursor);
        Limit rows = Limit.of(pageSize + 1);
        List<Follow> found = followersOf
                ? follows.findFollowers(
                        userId, after == null ? null : after.at(), after == null ? null : after.otherId(), rows)
                : follows.findFollowing(
                        userId, after == null ? null : after.at(), after == null ? null : after.otherId(), rows);
        Function<Follow, User> other = followersOf ? Follow::getFollower : Follow::getFollowing;
        boolean hasMore = found.size() > pageSize;
        List<Follow> pageRows = hasMore ? found.subList(0, pageSize) : found;
        String next = null;
        if (hasMore) {
            Follow last = pageRows.getLast();
            next = FollowCursor.encode(new FollowCursor.Position(
                    last.getCreatedAt(), other.apply(last).getId()));
        }
        return new CursorPage<>(
                pageRows.stream().map(other).map(mapper::toUserSummary).toList(), next);
    }

    private static ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng.");
    }
}
