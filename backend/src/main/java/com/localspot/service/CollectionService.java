package com.localspot.service;

import com.localspot.config.CollectionProperties;
import com.localspot.dto.request.CollectionPlaceRequest;
import com.localspot.dto.request.CollectionRequest;
import com.localspot.dto.response.CollectionDetailResponse;
import com.localspot.dto.response.CollectionSummaryResponse;
import com.localspot.dto.response.PhotoResponse;
import com.localspot.dto.response.PlaceSummaryResponse;
import com.localspot.entity.CollectionPlace;
import com.localspot.entity.Place;
import com.localspot.entity.PlaceCollection;
import com.localspot.entity.PlaceStatus;
import com.localspot.entity.User;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.mapper.CollectionMapper;
import com.localspot.repository.CollectionPlaceRepository;
import com.localspot.repository.PlaceCollectionRepository;
import com.localspot.repository.PlaceRepository;
import com.localspot.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bộ sưu tập (UC18, FR-26 — chốt 2026-10-12).
 *
 * <ul>
 *   <li><b>Công khai</b> = hiện trên trang cá nhân của chủ ({@code GET /users/{id}/collections}) và mở được qua link
 *       chia sẻ {@code /shared/collections/{shareSlug}}. Riêng tư = chỉ chủ. Chuyển về riêng tư thu hồi slug; công khai
 *       lại sinh slug mới → link cũ đã chia sẻ không mở lại được sau khi chủ đã rút lại.
 *   <li><b>Không tiết lộ tồn tại</b>: bộ riêng tư của người khác, và mọi thao tác ghi lên bộ của người khác → 404 như
 *       không tồn tại. Không có quyền vượt cho nhân sự — bộ sưu tập là dữ liệu cá nhân, không có luồng kiểm duyệt.
 *   <li><b>Địa điểm</b>: chỉ thêm được địa điểm đang hiện ({@code APPROVED}). Địa điểm bị ẩn sau khi thêm: người khác
 *       không thấy và không tính vào {@code placeCount}; chủ vẫn thấy kèm trạng thái để tự bỏ ra.
 *   <li><b>Trần</b> 50 bộ / người, 100 địa điểm / bộ — chi tiết trả mọi địa điểm trong một mảng (openapi). Khóa dòng
 *       người dùng (tạo bộ) / dòng bộ sưu tập (thêm địa điểm) để request chen nhau không vượt trần.
 * </ul>
 */
@Service
public class CollectionService {

    /** Phần đầu slug theo tên (dễ đọc khi chia sẻ) + hậu tố ngẫu nhiên chống trùng; tổng ≤ 37 < cột 40. */
    private static final int SLUG_NAME_LENGTH = 30;

    private static final int SLUG_SUFFIX_LENGTH = 6;
    private static final String SLUG_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlaceCollectionRepository collections;
    private final CollectionPlaceRepository items;
    private final PlaceRepository places;
    private final UserRepository users;
    private final PlaceSummaries summaries;
    private final PlacePhotoService photos;
    private final CollectionMapper mapper;
    private final CollectionProperties properties;
    private final Clock clock;

    public CollectionService(
            PlaceCollectionRepository collections,
            CollectionPlaceRepository items,
            PlaceRepository places,
            UserRepository users,
            PlaceSummaries summaries,
            PlacePhotoService photos,
            CollectionMapper mapper,
            CollectionProperties properties,
            Clock clock) {
        this.collections = collections;
        this.items = items;
        this.places = places;
        this.users = users;
        this.summaries = summaries;
        this.photos = photos;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    // ─── Ghi ─────────────────────────────────────────────────────────────────

    @Transactional
    public CollectionDetailResponse create(Long userId, CollectionRequest request) {
        User owner = users.findByIdForUpdate(userId).orElseThrow(CollectionService::userNotFound);
        if (collections.countByUserId(userId) >= properties.maxPerUser()) {
            throw limitExceeded("Mỗi người tối đa " + properties.maxPerUser() + " bộ sưu tập.");
        }
        PlaceCollection collection = new PlaceCollection(owner, request.name().strip());
        apply(collection, request);
        return detail(collections.save(collection), true);
    }

    @Transactional
    public CollectionDetailResponse update(Long userId, Long collectionId, CollectionRequest request) {
        PlaceCollection collection = owned(userId, collectionId, true);
        collection.setName(request.name().strip());
        apply(collection, request);
        return detail(collection, true);
    }

    @Transactional
    public void delete(Long userId, Long collectionId) {
        // Xóa hẳn: bảng không có cột xóa mềm (dữ liệu cá nhân, không có luồng kiểm duyệt); collection_place xóa theo
        // ON DELETE CASCADE của CSDL
        collections.delete(owned(userId, collectionId, true));
    }

    /**
     * Idempotent: địa điểm đã có trong bộ → chỉ cập nhật ghi chú (khi có body). Địa điểm mới phải đang hiện (404 như
     * không tồn tại) và bộ chưa đủ 100.
     */
    @Transactional
    public void addPlace(Long userId, Long collectionId, Long placeId, CollectionPlaceRequest request) {
        PlaceCollection collection = owned(userId, collectionId, true);
        String note = request == null ? null : blankToNull(request.note());
        CollectionPlace existing =
                items.findById(new CollectionPlace.Id(collectionId, placeId)).orElse(null);
        if (existing != null) {
            if (request != null) {
                existing.setNote(note);
            }
            return;
        }
        Place place = places.findById(placeId)
                .filter(p -> p.getStatus() == PlaceStatus.APPROVED)
                .orElseThrow(PlaceService::placeNotFound);
        if (items.countByCollectionId(collectionId) >= properties.maxPlaces()) {
            throw limitExceeded("Mỗi bộ sưu tập tối đa " + properties.maxPlaces() + " địa điểm.");
        }
        items.save(new CollectionPlace(collection, place, note, clock.instant()));
    }

    /** Idempotent — bỏ được cả địa điểm đã bị ẩn (chủ dọn bộ của mình). */
    @Transactional
    public void removePlace(Long userId, Long collectionId, Long placeId) {
        owned(userId, collectionId, false);
        items.deleteItem(collectionId, placeId);
    }

    // ─── Đọc ─────────────────────────────────────────────────────────────────

    /** {@code GET /collections/{id}}: chủ, hoặc bất kỳ ai khi bộ đang công khai. */
    @Transactional(readOnly = true)
    public CollectionDetailResponse get(Long collectionId, Long viewerId) {
        PlaceCollection collection =
                collections.findWithOwnerById(collectionId).orElseThrow(CollectionService::collectionNotFound);
        boolean owner = collection.getUser().getId().equals(viewerId);
        if (!owner && !collection.isPublicCollection()) {
            throw collectionNotFound();
        }
        return detail(collection, owner);
    }

    /** Link chia sẻ: luôn là góc nhìn công khai, kể cả khi chủ tự mở link của mình (xem trước như người nhận). */
    @Transactional(readOnly = true)
    public CollectionDetailResponse shared(String shareSlug) {
        return detail(
                collections.findPublicBySlug(shareSlug).orElseThrow(CollectionService::collectionNotFound), false);
    }

    /**
     * {@code placeId} (tùy chọn): đánh dấu bộ đã có địa điểm đó — hộp "Lưu" trên P03 biết tick sẵn bộ nào. Một truy vấn
     * thay vì tải chi tiết từng bộ (≤ 50).
     */
    @Transactional(readOnly = true)
    public List<CollectionSummaryResponse> mine(Long userId, Long placeId) {
        Set<Long> containing =
                placeId == null ? null : new HashSet<>(items.findCollectionIdsContaining(userId, placeId));
        return summariesOf(collections.findByOwner(userId, false), true, containing);
    }

    /** Tab bộ sưu tập trên trang cá nhân (P06): chỉ bộ công khai, kể cả khi chủ tự xem — như người khác thấy. */
    @Transactional(readOnly = true)
    public List<CollectionSummaryResponse> publicOf(Long userId) {
        if (!users.existsById(userId)) {
            throw userNotFound();
        }
        return summariesOf(collections.findByOwner(userId, true), false, null);
    }

    // ─── Dựng response ──────────────────────────────────────────────────────

    private CollectionDetailResponse detail(PlaceCollection collection, boolean ownerView) {
        List<CollectionPlace> rows = items.findItems(collection.getId());
        List<CollectionPlace> visible = rows.stream()
                .filter(row -> ownerView || row.getPlace().getStatus() == PlaceStatus.APPROVED)
                .toList();
        List<PlaceSummaryResponse> cards =
                summaries.of(visible.stream().map(CollectionPlace::getPlace).toList());
        List<CollectionDetailResponse.Item> itemList = new ArrayList<>(visible.size());
        for (int i = 0; i < visible.size(); i++) {
            itemList.add(new CollectionDetailResponse.Item(
                    cards.get(i), visible.get(i).getNote(), visible.get(i).getAddedAt()));
        }
        PhotoResponse cover = cards.isEmpty() ? null : cards.getFirst().coverPhoto();
        return new CollectionDetailResponse(
                collection.getId(),
                collection.getName(),
                collection.isPublicCollection(),
                collection.getShareSlug(),
                visible.size(),
                cover,
                collection.getDescription(),
                mapper.toUserSummary(collection.getUser()),
                itemList);
    }

    /** {@code containing} {@code null} = không hỏi về địa điểm nào ({@code containsPlace} để trống). */
    private List<CollectionSummaryResponse> summariesOf(
            List<PlaceCollection> list, boolean ownerView, Set<Long> containing) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        Map<Long, Long> coverPlaceIds = new HashMap<>();
        for (Object[] row :
                collections.placeStats(list.stream().map(PlaceCollection::getId).toList(), !ownerView)) {
            long collectionId = ((Number) row[0]).longValue();
            counts.put(collectionId, ((Number) row[1]).longValue());
            coverPlaceIds.put(collectionId, ((Number) row[2]).longValue());
        }
        Map<Long, PhotoResponse> covers = photos.covers(coverPlaceIds.values());
        return list.stream()
                .map(c -> new CollectionSummaryResponse(
                        c.getId(),
                        c.getName(),
                        c.isPublicCollection(),
                        c.getShareSlug(),
                        counts.getOrDefault(c.getId(), 0L),
                        coverOf(covers, coverPlaceIds.get(c.getId())),
                        containing == null ? null : containing.contains(c.getId())))
                .toList();
    }

    // ─── Hỗ trợ ─────────────────────────────────────────────────────────────

    /** Bộ rỗng (với người xem) không có địa điểm làm bìa; map của {@code covers} không nhận khóa {@code null}. */
    private static PhotoResponse coverOf(Map<Long, PhotoResponse> covers, Long placeId) {
        return placeId == null ? null : covers.get(placeId);
    }

    private void apply(PlaceCollection collection, CollectionRequest request) {
        collection.setDescription(blankToNull(request.description()));
        if (request.publicRequested() && !collection.isPublicCollection()) {
            collection.publish(newShareSlug(collection.getName()));
        } else if (!request.publicRequested() && collection.isPublicCollection()) {
            collection.makePrivate();
        }
    }

    /** Bộ của người khác → 404 như không tồn tại (không tiết lộ bộ riêng tư có tồn tại). */
    private PlaceCollection owned(Long userId, Long collectionId, boolean lock) {
        return (lock ? collections.findByIdForUpdate(collectionId) : collections.findById(collectionId))
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(CollectionService::collectionNotFound);
    }

    /** Vd. "Quán ăn Hà Nội nên thử" → {@code quan-an-ha-noi-nen-thu-x7k2qa}. */
    static String newShareSlug(String name) {
        String base = Slugs.slugify(name, SLUG_NAME_LENGTH);
        StringBuilder slug = new StringBuilder(base.isEmpty() ? "bo-suu-tap" : base).append('-');
        for (int i = 0; i < SLUG_SUFFIX_LENGTH; i++) {
            slug.append(SLUG_ALPHABET.charAt(RANDOM.nextInt(SLUG_ALPHABET.length())));
        }
        return slug.toString();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static ApiException limitExceeded(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.COLLECTION_LIMIT_EXCEEDED, detail);
    }

    static ApiException collectionNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.COLLECTION_NOT_FOUND, "Không tìm thấy bộ sưu tập.");
    }

    private static ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng.");
    }
}
