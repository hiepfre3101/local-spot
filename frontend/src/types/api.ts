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
  city: string
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
  rejectReason: string | null
  version: number
}

/** `GET /search`: `degraded = true` khi Meilisearch lỗi và đang dùng tìm theo tên (U9). */
export interface SearchPage extends CursorPage<PlaceSummary> {
  degraded: boolean
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

/** openapi `Review`. `authorCheckedIn`: tác giả check-in vào ngày viết hoặc trước đó (nhãn "✓ Đã check-in"). */
export interface Review {
  id: number
  placeId: number
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
