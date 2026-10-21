/**
 * Kiểu dữ liệu API — bám docs/api/openapi.yaml (schema cùng tên). Chỉ khai báo phần frontend đang dùng; thêm dần theo
 * từng module.
 */

export type RoleName = 'USER' | 'OWNER' | 'MODERATOR' | 'ADMIN'

/** openapi `MeResponse`. `permissions` gộp từ các role — dùng để ẩn / chặn theo đúng quyền của backend. */
export interface MeResponse {
  id: number
  displayName: string
  avatarUrl: string | null
  email: string
  bio: string | null
  emailVerified: boolean
  roles: RoleName[]
  permissions: string[]
  trustScore: number
  ownedPlaceIds: number[]
}

/** openapi `AuthResponse`. Refresh token không có ở đây — nằm trong cookie HttpOnly (S1). */
export interface AuthResponse {
  accessToken: string
  expiresIn: number
  user: MeResponse
}

/** Danh sách phân trang cursor: `nextCursor = null` là hết. */
export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
}

/** openapi `AdminUser` (UC31). */
export interface AdminUser {
  id: number
  displayName: string
  avatarUrl: string | null
  email: string
  emailVerified: boolean
  roles: RoleName[]
  trustScore: number
  lockedUntil: string | null
  createdAt: string
}

/** openapi `Problem` — RFC 7807. */
export interface ProblemBody {
  type?: string
  title?: string
  status?: number
  detail?: string
  code?: string
  errors?: { field: string; message: string }[]
  errorId?: string
}

// ─── Địa điểm, danh mục, review (module khám phá: trang chủ, P02, P03) ───

/** openapi `UserSummary` — thông tin công khai tối thiểu. */
export interface UserSummary {
  id: number
  displayName: string
  avatarUrl: string | null
}

export type PhotoStatus = 'PROCESSING' | 'READY' | 'FAILED'

/** openapi `Photo` — URL chỉ có khi `READY` (thumb ≤ 320 px, medium ≤ 960 px, large ≤ 1920 px). */
export interface Photo {
  id: number
  status: PhotoStatus
  thumbUrl: string | null
  mediumUrl: string | null
  largeUrl: string | null
}

export interface GeoPoint {
  lat: number
  lng: number
}

/** openapi `CategoryNode` — cây ≤ 2 cấp, danh mục con có `children` rỗng. */
export interface CategoryNode {
  id: number
  name: string
  slug: string
  icon: string | null
  children: CategoryNode[]
}

export interface CategoryRef {
  id: number
  name: string
  slug: string
}

/** openapi `Amenity` — dùng chung cho mọi danh mục (O7). */
export interface Amenity {
  id: number
  name: string
  slug: string
  icon: string | null
}

/** openapi `OpeningHour`: `dayOfWeek` 1 = Thứ Hai … 7 = Chủ nhật; `closeTime < openTime` = mở qua đêm. */
export interface OpeningHour {
  dayOfWeek: number
  openTime: string
  closeTime: string
}

export type PlaceStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'HIDDEN'

/**
 * openapi `PlaceSummary`. `bayesianScore` là điểm hiển thị / xếp hạng (0 = chưa có review PUBLISHED); `avgRating` là
 * trung bình thô — không dùng để xếp hạng. `distanceM` chỉ có ở `/places/nearby`.
 */
export interface PlaceSummary {
  id: number
  slug: string
  name: string
  category: CategoryRef
  address: string
  /** Thành phố (thêm 2026-10-21) — hiện cùng địa chỉ qua `fullAddress`. */
  city: string
  location: GeoPoint
  coverPhoto: Photo | null
  priceMin: number | null
  priceMax: number | null
  bayesianScore: number
  avgRating: number
  reviewCount: number
  status: PlaceStatus
  distanceM: number | null
  tags: string[]
}

/** openapi `PlaceDetail` = `PlaceSummary` + chi tiết; `myReviewId`, `claimable` theo người xem. */
export interface PlaceDetail extends PlaceSummary {
  description: string | null
  phone: string | null
  website: string | null
  photos: Photo[]
  openingHours: OpeningHour[]
  amenities: Amenity[]
  ratingDistribution: Record<'1' | '2' | '3' | '4' | '5', number>
  checkinCount: number
  owner: UserSummary | null
  claimable: boolean
  myReviewId: number | null
  /** Người xem đã check-in hôm nay (giờ VN) — nút Check-in P12. Khách = `false`. */
  checkedInToday: boolean
  rejectReason: string | null
  version: number
}

/** `GET /search`: `degraded = true` khi Meilisearch lỗi và đang dùng tìm theo tên (U9). */
export interface SearchPage extends CursorPage<PlaceSummary> {
  degraded: boolean
}

/** openapi `City` (`GET /cities`) — `name` khớp đúng `places.city`. */
export interface City {
  name: string
  placeCount: number
}

export type LeaderboardPeriod = 'WEEK' | 'MONTH' | 'ALL'

/** openapi `LeaderboardEntry` — hạng kiểu thi đấu (cùng điểm cùng hạng). */
export interface LeaderboardEntry {
  rank: number
  user: UserSummary
  points: number
}

export interface PlaceRef {
  id: number
  slug: string
  name: string
}

/** openapi `HomeReview` — đánh giá nổi bật trên trang chủ (ReviewCard), chỉ có đoạn trích ≤ 180 ký tự. */
export interface HomeReview {
  id: number
  excerpt: string
  rating: number
  helpfulCount: number
  author: UserSummary
  place: PlaceRef
  createdAt: string
}

/** openapi `HomeResponse` (FR-08). */
export interface HomeResponse {
  featured: PlaceSummary[]
  latest: PlaceSummary[]
  categories: CategoryNode[]
  featuredReview: HomeReview | null
  /** Ảnh đang bật theo ô (UC34); ô không có → hình minh hoạ mặc định. */
  banners: HomeBanner[]
}

/** Ô ảnh cố định trên trang chủ (UC34). */
export type BannerSlot = 'HERO' | 'SIGNUP_BAND' | 'COMMUNITY'

/** openapi `HomeBanner` — `altText` rỗng = ảnh trang trí. */
export interface HomeBanner {
  slot: BannerSlot
  image: Photo
  altText: string
}

/** openapi `Banner` — màn quản trị; ô chưa có ảnh: `id`, `image`, `version`… là `null`. */
export interface Banner {
  slot: BannerSlot
  id: number | null
  image: Photo | null
  altText: string
  active: boolean
  version: number | null
  updatedAt: string | null
  updatedBy: UserSummary | null
}

export type ReviewStatus = 'PENDING' | 'PUBLISHED' | 'REJECTED' | 'HIDDEN'

export interface OwnerReply {
  id: number
  owner: UserSummary
  content: string
  createdAt: string
  updatedAt: string
}

/** Một ngày (giờ Việt Nam) — chuỗi luôn đủ mọi ngày trong khoảng, ngày trống = 0. */
export interface DailyCount {
  date: string
  count: number
}

/**
 * openapi `PlaceStats` (UC26). Chuỗi theo ngày trong `[from, to]`; `ratingDistribution`, `bayesianScore`,
 * `reviewCount` là hiện trạng (không theo khoảng).
 */
export interface PlaceStats {
  from: string
  to: string
  viewsByDay: DailyCount[]
  checkinsByDay: DailyCount[]
  ratingDistribution: Record<string, number>
  bayesianScore: number
  reviewCount: number
}

export type ClaimStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

/** Tổng trong một kỳ: tài khoản mới, review được gửi (mọi trạng thái), báo cáo nhận được. */
export interface DashboardTotals {
  newUsers: number
  reviews: number
  reports: number
}

/**
 * openapi `Dashboard` (UC33, FR-41). `previousTotals` = kỳ liền trước cùng số ngày (▲▼); `pendingCounts` là việc tồn
 * **hiện tại** (không theo khoảng), cùng định nghĩa với hàng chờ gộp.
 */
export interface Dashboard {
  from: string
  to: string
  generatedAt: string
  totals: DashboardTotals
  previousTotals: DashboardTotals
  newUsersByDay: DailyCount[]
  reviewsByDay: DailyCount[]
  reviewsByCity: { city: string; reviewCount: number }[]
  hotPlaces: { place: PlaceSummary; newReviewCount: number }[]
  pendingCounts: {
    places: number
    reviews: number
    reports: number
    claims: number
    oldestSubmittedAt: string | null
  }
}

/** openapi `MyClaim` — yêu cầu sở hữu nhìn từ người gửi (`GET /me/claims`). */
export interface MyClaim {
  id: number
  place: PlaceRef
  status: ClaimStatus
  rejectReason: string | null
  createdAt: string
  reviewedAt: string | null
}

/** openapi `Review`. `authorCheckedIn`: tác giả check-in vào ngày viết hoặc trước đó (nhãn "✓ Đã check-in"). */
export interface Review {
  id: number
  placeId: number
  /** Tên + slug — danh sách ngoài trang địa điểm (trang cá nhân P06). */
  place: PlaceRef
  author: UserSummary
  rating: number
  content: string
  visitedAt: string
  status: ReviewStatus
  rejectReason: string | null
  photos: Photo[]
  helpfulCount: number
  votedByMe: boolean
  authorCheckedIn: boolean
  commentCount: number
  ownerReply: OwnerReply | null
  createdAt: string
  /** Lần cuối tác giả sửa số sao / nội dung / ngày đến; `null` = chưa sửa (cập nhật theo lần ghé mới — 2026-10-19). */
  revisedAt: string | null
  updatedAt: string
  version: number
}

/** openapi `VoteResult` — trạng thái sau khi vote / bỏ vote (idempotent). */
export interface VoteResult {
  helpfulCount: number
  votedByMe: boolean
}

/**
 * openapi `Comment` — 2 cấp. Gốc đã xoá / bị ẩn nhưng còn trả lời → placeholder `deleted` (không `author` /
 * `content`). `replies` chỉ có ở gốc (trả lời: `null`).
 */
export interface Comment {
  id: number
  author: UserSummary | null
  content: string | null
  parentId: number | null
  deleted: boolean
  replies: Comment[] | null
  createdAt: string
}

export type ReportTargetType = 'PLACE' | 'REVIEW' | 'COMMENT' | 'USER'
export type ReportReason = 'SPAM' | 'FAKE' | 'OFFENSIVE' | 'WRONG_INFO' | 'OTHER'

/** openapi `Badge` — huy hiệu đã đạt (đã trao không thu hồi). `icon` là khóa trung tính. */
export interface Badge {
  code: string
  name: string
  description: string | null
  icon: string
  awardedAt: string
}

/** openapi `PublicProfile` — không có email (NFR-11). */
export interface PublicProfile extends UserSummary {
  bio: string | null
  joinedAt: string
  reviewCount: number
  followerCount: number
  followingCount: number
  contributionPoints: number
  badges: Badge[]
  followedByMe: boolean
}

/** openapi `CollectionSummary` — `placeCount` theo người xem (người khác chỉ đếm địa điểm đang hiện). */
export interface CollectionSummary {
  id: number
  name: string
  isPublic: boolean
  shareSlug: string | null
  placeCount: number
  coverPhoto: Photo | null
  /** Chỉ có ở `GET /me/collections?placeId=` — bộ đã có địa điểm đó (hộp "Lưu" trên P03). */
  containsPlace?: boolean | null
}

/**
 * openapi `CollectionDetail`. Chủ thấy cả địa điểm đã bị ẩn sau khi thêm (`place.status` khác APPROVED) để tự bỏ ra;
 * người khác chỉ thấy địa điểm đang hiện.
 */
export interface CollectionDetail extends CollectionSummary {
  description: string | null
  owner: UserSummary
  places: { place: PlaceSummary; note: string | null; addedAt: string }[]
}

/** openapi `CheckInResponse` — chỉ chính chủ xem (lịch sử vị trí là dữ liệu riêng tư). */
export interface CheckIn {
  id: number
  placeId: number
  place: PlaceSummary
  distanceM: number
  checkinDate: string
  createdAt: string
}

/** openapi `Notification.type` (chốt 2026-10-16 — chỉ các sự kiện trong UC). */
export type NotificationType =
  | 'PLACE_APPROVED'
  | 'PLACE_REJECTED'
  | 'REVIEW_PUBLISHED'
  | 'REVIEW_REJECTED'
  | 'NEW_REVIEW_ON_MY_PLACE'
  | 'OWNER_REPLIED'
  | 'REPORT_ACTION_TAKEN'
  | 'REPORT_RESOLVED'
  | 'CLAIM_SUBMITTED'
  | 'CLAIM_APPROVED'
  | 'CLAIM_REJECTED'

/**
 * `Notification.data` — ảnh chụp lúc gửi, không join lại khi đọc (địa điểm có thể đã đổi tên / bị ẩn). Trường có mặt tuỳ
 * loại (bảng trong openapi); mọi loại về địa điểm có `placeId`, `placeName`, `placeSlug`.
 */
export interface NotificationData {
  placeId?: number
  placeName?: string
  placeSlug?: string
  reviewId?: number
  rating?: number
  authorName?: string
  reason?: string | null
  targetType?: ReportTargetType
  targetId?: number
  preview?: string
  confirmedReason?: string
  violation?: boolean
  claimId?: number
  hiddenReviewId?: number
}

/** openapi `Notification` (FR-27) — cùng một dạng cho `GET /me/notifications` và khung STOMP. */
export interface AppNotification {
  id: number
  type: NotificationType
  data: NotificationData
  readAt: string | null
  createdAt: string
}

// ── Kiểm duyệt (UC27–UC30) ──

export type ModerationItemType = 'PLACE' | 'REVIEW' | 'REPORT' | 'CLAIM'

/** openapi `ModerationQueueItem` — một dòng hàng chờ gộp FIFO. Với REPORT, `id` là id đối tượng bị báo cáo. */
export interface ModerationQueueItem {
  position: number
  type: ModerationItemType
  id: number
  reportTargetType?: ReportTargetType | null
  title: string | null
  submittedBy: string | null
  submittedAt: string
  flags: ('IP_FLAGGED' | 'LOW_TRUST')[]
  reportCount?: number | null
}

/** openapi `ModerationDecision` — `reason` bắt buộc khi REJECT; `version` lệch → 409. */
export interface ModerationDecision {
  decision: 'APPROVE' | 'REJECT'
  reason?: string
  version?: number
}

/** openapi `ModerationPlaceDetail` (thêm 2026-10-19). `possibleDuplicates` rỗng khi đã xử lý. */
export interface ModerationPlaceDetail {
  place: PlaceDetail
  proposer: UserSummary
  submittedAt: string
  possibleDuplicates: PlaceSummary[]
}

/** openapi `ModerationReview`. */
export interface ModerationReview {
  review: Review
  place: PlaceSummary
  authorTrustScore: number
  authorActiveViolations: number
  ipFlagged: boolean
  sameSubnetReviewCount24h: number
}

/** openapi `ModerationReviewDetail` — `authorReviewCounts` đủ 4 khóa trạng thái. */
export interface ModerationReviewDetail {
  item: ModerationReview
  authorJoinedAt: string
  authorReviewCounts: Record<ReviewStatus, number>
  sameSubnetReviews: Review[]
}

/** openapi `ReportGroup`. `targetPreview` null khi đối tượng đã bị xoá (`targetStatus = DELETED`). */
export interface ReportGroup {
  targetType: ReportTargetType
  targetId: number
  targetPreview: string | null
  reportCount: number
  reasons: Partial<Record<ReportReason, number>>
  firstReportedAt: string
  targetStatus: string
}

export interface ReportItem {
  id: number
  reporter: UserSummary
  reason: ReportReason
  detail: string | null
  createdAt: string
}

/** openapi `ReportGroupDetail` — `author` là người sẽ bị ghi vi phạm khi ẩn (luật O9). */
export interface ReportGroupDetail {
  group: ReportGroup
  author: UserSummary | null
  reports: ReportItem[]
}

/** openapi `ReportDecision` — `confirmedReason` bắt buộc khi ẩn địa điểm, phải là lý do có trong các báo cáo đang mở. */
export interface ReportDecision {
  targetType: ReportTargetType
  targetId: number
  decision: 'HIDE' | 'DISMISS'
  confirmedReason?: ReportReason
  note?: string
}

/** openapi `Claim` — màn duyệt yêu cầu sở hữu (UC30). */
export interface ModerationClaim {
  id: number
  place: PlaceSummary
  claimant: UserSummary
  contactPhone: string
  note: string | null
  evidenceCount: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  rejectReason: string | null
  otherPendingClaimCount: number
  createdAt: string
  version: number
}

/** openapi `EvidenceLink` — link presigned 5 phút tới file minh chứng riêng tư. */
export interface EvidenceLink {
  url: string
  expiresAt: string
}
