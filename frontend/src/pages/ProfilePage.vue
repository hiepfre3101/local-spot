<script setup lang="ts">
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/vue-query'
import { isAxiosError } from 'axios'
import { computed, ref, useTemplateRef, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { problemMessage, toProblem } from '@/api/problem'
import { usersApi } from '@/api/users'
import PlacePhoto from '@/components/place/PlacePhoto.vue'
import BadgeMedal from '@/components/profile/BadgeMedal.vue'
import FollowListDialog from '@/components/profile/FollowListDialog.vue'
import ProfileReviewItem from '@/components/profile/ProfileReviewItem.vue'
import LsAvatar from '@/components/ui/LsAvatar.vue'
import LsBadge from '@/components/ui/LsBadge.vue'
import LsButton from '@/components/ui/LsButton.vue'
import { useRequireLogin } from '@/composables/useRequireLogin'
import NotFoundPage from '@/pages/errors/NotFoundPage.vue'
import { useNotify } from '@/plugins/toast'
import { useAuthStore } from '@/stores/auth'
import type { PlaceStatus, PublicProfile } from '@/types/api'
import { formatDate, formatNumber } from '@/utils/format'

/**
 * P06 Trang cá nhân (UC18, UC20, UC21; UC11 — theo dõi đề xuất). Đầu trang: ảnh đại diện, tên, ngày tham gia, giới
 * thiệu, dải số liệu (đánh giá · điểm đóng góp · người theo dõi · đang theo dõi — hai ô sau mở danh sách công khai),
 * nút Theo dõi; huy hiệu đã đạt. Tab qua `?tab=` (sitemap): Đánh giá · Bộ sưu tập · Huy hiệu, và trên trang của chính
 * mình thêm Check-in (riêng tư — chốt 2026-10-12) · Đề xuất (`/me/places`). Mỗi tab chỉ tải khi mở.
 *
 * Chốt 2026-10-18 (supervisor): phạm vi chỉ trang cá nhân — trang bộ sưu tập, chỉnh sửa hồ sơ, nút "Lưu" trên trang địa
 * điểm làm ở mục riêng (thẻ bộ sưu tập chưa bấm được, không có link chết); huy hiệu chỉ hiện cái đã đạt (API không có
 * tiến độ). Không hiện khu vực / số check-in / hạng tháng như bản mẫu: hồ sơ công khai không có các số này (check-in
 * là dữ liệu riêng tư).
 */
const props = defineProps<{ id: number }>()

type Tab = 'reviews' | 'collections' | 'badges' | 'checkins' | 'places'

const auth = useAuthStore()
const route = useRoute()
const queryClient = useQueryClient()
const notify = useNotify()
const requireLogin = useRequireLogin()
const followDialog = useTemplateRef<InstanceType<typeof FollowListDialog>>('followDialog')

const profileKey = computed(() => ['user', props.id, 'profile'])
const profile = useQuery({
  queryKey: profileKey,
  queryFn: () => usersApi.profile(props.id),
})
const user = computed(() => profile.data.value)
const notFound = computed(
  () =>
    profile.isError.value &&
    isAxiosError(profile.error.value) &&
    profile.error.value.response?.status === 404,
)
const isMe = computed(() => auth.user?.id === props.id)

watch(
  user,
  (u) => {
    if (u) document.title = `${u.displayName} · LocalSpot`
  },
  { immediate: true },
)

const joined = computed(() => {
  const at = user.value?.joinedAt
  if (!at) return null
  const d = new Date(at)
  return `${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`
})

// ─── Tabs ───────────────────────────────────────────────────────────────────
const tabs = computed<{ key: Tab; label: string; count?: number }[]>(() => [
  { key: 'reviews', label: 'Đánh giá', count: user.value?.reviewCount },
  { key: 'collections', label: 'Bộ sưu tập' },
  { key: 'badges', label: 'Huy hiệu', count: user.value?.badges.length },
  ...(isMe.value
    ? [
        { key: 'checkins' as const, label: 'Check-in' },
        { key: 'places' as const, label: 'Đề xuất của tôi' },
      ]
    : []),
])
const tab = computed<Tab>(() => {
  const requested = route.query.tab
  return tabs.value.some((t) => t.key === requested) ? (requested as Tab) : 'reviews'
})

const reviews = useInfiniteQuery({
  queryKey: computed(() => ['user', props.id, 'reviews', isMe.value]),
  queryFn: ({ pageParam }) =>
    isMe.value ? usersApi.myReviews(pageParam) : usersApi.reviews(props.id, pageParam),
  initialPageParam: null as string | null,
  getNextPageParam: (last) => last.nextCursor,
  enabled: computed(() => tab.value === 'reviews' && !!user.value),
})
const reviewItems = computed(() => reviews.data.value?.pages.flatMap((p) => p.items) ?? [])

const collections = useQuery({
  queryKey: computed(() => ['user', props.id, 'collections', isMe.value]),
  queryFn: () => (isMe.value ? usersApi.myCollections() : usersApi.collections(props.id)),
  enabled: computed(() => tab.value === 'collections' && !!user.value),
})

const checkins = useInfiniteQuery({
  queryKey: computed(() => ['me', 'check-ins']),
  queryFn: ({ pageParam }) => usersApi.myCheckIns(pageParam),
  initialPageParam: null as string | null,
  getNextPageParam: (last) => last.nextCursor,
  enabled: computed(() => tab.value === 'checkins' && isMe.value),
})
const checkinItems = computed(() => checkins.data.value?.pages.flatMap((p) => p.items) ?? [])

const places = useInfiniteQuery({
  queryKey: computed(() => ['me', 'places']),
  queryFn: ({ pageParam }) => usersApi.myPlaces(pageParam),
  initialPageParam: null as string | null,
  getNextPageParam: (last) => last.nextCursor,
  enabled: computed(() => tab.value === 'places' && isMe.value),
})
const placeItems = computed(() => places.data.value?.pages.flatMap((p) => p.items) ?? [])

const PLACE_STATUS: Record<
  PlaceStatus,
  { tone: 'warning' | 'success' | 'danger' | 'neutral'; text: string }
> = {
  PENDING: { tone: 'warning', text: '● Chờ duyệt' },
  APPROVED: { tone: 'success', text: '✓ Đã duyệt' },
  REJECTED: { tone: 'danger', text: '✕ Bị từ chối' },
  HIDDEN: { tone: 'neutral', text: '✕ Đã bị ẩn' },
}

// ─── Theo dõi (UC20) ────────────────────────────────────────────────────────
const following = ref(false)

async function toggleFollow(): Promise<void> {
  const u = user.value
  if (!u || !requireLogin()) return
  following.value = true
  const next = !u.followedByMe
  try {
    await (next ? usersApi.follow(u.id) : usersApi.unfollow(u.id))
    queryClient.setQueryData<PublicProfile>(profileKey.value, (old) =>
      old
        ? {
            ...old,
            followedByMe: next,
            followerCount: Math.max(0, old.followerCount + (next ? 1 : -1)),
          }
        : old,
    )
    void queryClient.invalidateQueries({ queryKey: ['user', u.id] })
  } catch (error) {
    notify.error(problemMessage(toProblem(error)))
  } finally {
    following.value = false
  }
}
</script>

<template>
  <NotFoundPage v-if="notFound" />

  <section v-else-if="profile.isError.value" class="py-16" role="alert">
    <h1 class="ls-h1">Chưa tải được trang cá nhân</h1>
    <p class="mt-4 text-ink-muted">Kiểm tra kết nối mạng rồi thử lại.</p>
    <LsButton class="mt-4" @click="profile.refetch()">Thử lại</LsButton>
  </section>

  <div v-else-if="!user" aria-busy="true" aria-label="Đang tải trang cá nhân" class="py-8">
    <span class="ls-skel mb-4 h-24 w-24 rounded-pill"></span>
    <span class="ls-skel mb-3 h-10 w-1/2"></span>
    <span class="ls-skel h-16 w-full"></span>
  </div>

  <article v-else class="ls-pub py-8">
    <!-- Đầu trang -->
    <header class="flex flex-wrap items-start justify-between gap-6">
      <div class="flex flex-wrap items-center gap-6">
        <LsAvatar
          :name="user.displayName"
          :src="user.avatarUrl"
          class="h-24! w-24! text-heading!"
        />
        <div>
          <h1 class="ls-h1 m-0">{{ user.displayName }}</h1>
          <p class="mt-2 mb-0 text-ink-muted">Thành viên từ {{ joined }}</p>
          <p v-if="user.bio" class="mt-2 mb-0 max-w-[60ch]">{{ user.bio }}</p>
        </div>
      </div>
      <LsButton v-if="isMe" variant="ghost" :to="{ name: 'settings-password' }"
        >Cài đặt tài khoản</LsButton
      >
      <LsButton
        v-else
        :variant="user.followedByMe ? 'ghost' : 'primary'"
        :aria-pressed="user.followedByMe"
        :loading="following"
        @click="toggleFollow"
        >{{ user.followedByMe ? '✓ Đang theo dõi' : '+ Theo dõi' }}</LsButton
      >
    </header>

    <div class="ls-stats mt-6">
      <div>
        <b>{{ formatNumber(user.reviewCount) }}</b
        ><span>Đánh giá</span>
      </div>
      <div>
        <b>{{ formatNumber(user.contributionPoints) }}</b
        ><span>Điểm đóng góp</span>
      </div>
      <button type="button" @click="followDialog?.open('followers')">
        <b>{{ formatNumber(user.followerCount) }}</b
        ><span>Người theo dõi</span>
      </button>
      <button type="button" @click="followDialog?.open('following')">
        <b>{{ formatNumber(user.followingCount) }}</b
        ><span>Đang theo dõi</span>
      </button>
    </div>

    <ul
      v-if="user.badges.length"
      class="mt-8 mb-0 flex list-none flex-wrap gap-4 p-0"
      aria-label="Huy hiệu"
    >
      <li v-for="badge in user.badges.slice(0, 4)" :key="badge.code">
        <BadgeMedal :badge="badge" />
      </li>
    </ul>

    <!-- Tabs -->
    <nav class="ls-tabs mt-10" aria-label="Nội dung trang cá nhân">
      <RouterLink
        v-for="t in tabs"
        :key="t.key"
        :to="{ query: { ...route.query, tab: t.key === 'reviews' ? undefined : t.key } }"
        :aria-current="tab === t.key ? 'page' : undefined"
        replace
      >
        {{ t.label }}<span v-if="t.count !== undefined" class="n">{{ formatNumber(t.count) }}</span>
      </RouterLink>
    </nav>

    <!-- Đánh giá -->
    <section v-if="tab === 'reviews'" class="max-w-[760px]" :aria-busy="reviews.isFetching.value">
      <p v-if="reviews.isError.value" role="alert">✕ Chưa tải được đánh giá.</p>
      <p v-else-if="!reviews.isPending.value && reviewItems.length === 0" class="text-ink-muted">
        {{ isMe ? 'Bạn chưa viết đánh giá nào.' : 'Chưa có đánh giá nào.' }}
      </p>
      <ProfileReviewItem v-for="review in reviewItems" :key="review.id" :review="review" />
      <div v-if="reviews.hasNextPage.value" class="pt-6">
        <LsButton
          variant="ghost"
          :loading="reviews.isFetchingNextPage.value"
          @click="reviews.fetchNextPage()"
          >Xem thêm đánh giá</LsButton
        >
      </div>
    </section>

    <!-- Bộ sưu tập -->
    <section v-else-if="tab === 'collections'">
      <p v-if="collections.isError.value" role="alert">✕ Chưa tải được bộ sưu tập.</p>
      <p
        v-else-if="!collections.isPending.value && !(collections.data.value ?? []).length"
        class="text-ink-muted"
      >
        {{ isMe ? 'Bạn chưa có bộ sưu tập nào.' : 'Chưa có bộ sưu tập công khai nào.' }}
      </p>
      <ul class="m-0 grid list-none gap-6 p-0 sm:grid-cols-2 lg:grid-cols-4">
        <li v-for="c in collections.data.value ?? []" :key="c.id">
          <PlacePhoto
            :photo="c.coverPhoto"
            sizes="280px"
            class="aspect-square overflow-hidden rounded-md"
          />
          <b class="mt-3 block">{{ c.name }}</b>
          <span class="text-caption text-ink-muted">
            {{ formatNumber(c.placeCount) }} địa điểm<template v-if="!c.isPublic">
              · Riêng tư</template
            >
          </span>
        </li>
      </ul>
    </section>

    <!-- Huy hiệu -->
    <section v-else-if="tab === 'badges'">
      <p v-if="!user.badges.length" class="text-ink-muted">
        {{
          isMe
            ? 'Bạn chưa có huy hiệu nào — viết đánh giá, check-in để nhận huy hiệu đầu tiên.'
            : 'Chưa có huy hiệu nào.'
        }}
      </p>
      <ul class="m-0 flex list-none flex-wrap gap-6 p-0">
        <li v-for="badge in user.badges" :key="badge.code" class="flex flex-col items-center">
          <BadgeMedal :badge="badge" show-date />
          <p
            v-if="badge.description"
            class="m-0 mt-1 max-w-[112px] text-center text-caption text-ink-muted"
          >
            {{ badge.description }}
          </p>
        </li>
      </ul>
    </section>

    <!-- Check-in (chỉ chính mình) -->
    <section v-else-if="tab === 'checkins'" class="max-w-[760px]">
      <p class="mt-0 text-caption text-ink-muted">Chỉ bạn thấy lịch sử check-in của mình.</p>
      <p v-if="checkins.isError.value" role="alert">✕ Chưa tải được lịch sử check-in.</p>
      <p v-else-if="!checkins.isPending.value && checkinItems.length === 0" class="text-ink-muted">
        Bạn chưa check-in ở đâu.
      </p>
      <ul class="m-0 list-none p-0">
        <li
          v-for="c in checkinItems"
          :key="c.id"
          class="flex flex-wrap justify-between gap-2 border-b border-line py-4"
        >
          <RouterLink
            class="font-semibold text-ink"
            :to="{ name: 'place', params: { slug: c.place.slug } }"
          >
            {{ c.place.name }}
          </RouterLink>
          <span class="text-caption text-ink-muted">{{ formatDate(c.checkinDate) }}</span>
        </li>
      </ul>
      <div v-if="checkins.hasNextPage.value" class="pt-6">
        <LsButton
          variant="ghost"
          :loading="checkins.isFetchingNextPage.value"
          @click="checkins.fetchNextPage()"
          >Xem thêm</LsButton
        >
      </div>
    </section>

    <!-- Đề xuất của tôi (UC11 — theo dõi kết quả duyệt) -->
    <section v-else-if="tab === 'places'" class="max-w-[760px]">
      <p v-if="places.isError.value" role="alert">✕ Chưa tải được đề xuất.</p>
      <p v-else-if="!places.isPending.value && placeItems.length === 0" class="text-ink-muted">
        Bạn chưa đề xuất địa điểm nào.
        <RouterLink v-if="$router.hasRoute('propose')" class="ls-link" :to="{ name: 'propose' }"
          >Đề xuất địa điểm</RouterLink
        >
      </p>
      <ul class="m-0 list-none p-0">
        <li
          v-for="p in placeItems"
          :key="p.id"
          class="flex flex-wrap items-center justify-between gap-3 border-b border-line py-4"
        >
          <div class="min-w-0">
            <RouterLink
              class="font-semibold text-ink"
              :to="{ name: 'place', params: { slug: p.slug } }"
            >
              {{ p.name }}
            </RouterLink>
            <p class="m-0 text-caption text-ink-muted">{{ p.address }}</p>
          </div>
          <LsBadge :tone="PLACE_STATUS[p.status].tone">{{ PLACE_STATUS[p.status].text }}</LsBadge>
        </li>
      </ul>
      <div v-if="places.hasNextPage.value" class="pt-6">
        <LsButton
          variant="ghost"
          :loading="places.isFetchingNextPage.value"
          @click="places.fetchNextPage()"
          >Xem thêm</LsButton
        >
      </div>
    </section>

    <FollowListDialog ref="followDialog" :user-id="user.id" />
  </article>
</template>
