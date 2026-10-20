<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, useTemplateRef } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { moderationApi } from '@/api/moderation'
import { problemMessage, toProblem } from '@/api/problem'
import PlaceMap from '@/components/place/PlaceMap.vue'
import PlacePhoto from '@/components/place/PlacePhoto.vue'
import LsAdminButton from '@/components/ui/LsAdminButton.vue'
import LsBadge from '@/components/ui/LsBadge.vue'
import { useAuthStore } from '@/stores/auth'
import type { PlaceStatus } from '@/types/api'
import { formatDateTime, formatNumber, formatPriceRange, tagLabel } from '@/utils/format'
import { weekSchedule } from '@/utils/openingHours'

import ModerationHead from './ModerationHead.vue'
import RejectReason from './RejectReason.vue'
import { useDecision } from './useDecision'

/**
 * Duyệt địa điểm đề xuất (UC27, AdminForm): đầu trang có trạng thái, người đề xuất, thời gian, Từ chối / Duyệt & đăng;
 * bên dưới là các section hai cột. **Chỉ đọc**: API chỉ nhận duyệt / từ chối — bản mẫu cho sửa trường, giữ / bỏ ảnh
 * trước khi duyệt nhưng backend không có thao tác đó (chốt 2026-10-19). Có thêm section "Có thể trùng": hai người dùng
 * không thấy đề xuất chờ của nhau, kiểm duyệt viên là nơi bắt trùng (U7).
 */
const route = useRoute()
const auth = useAuthStore()
const placeId = computed(() => Number(route.params.id))

const detail = useQuery({
  queryKey: computed(() => ['moderation', 'place', placeId.value]),
  queryFn: () => moderationApi.place(placeId.value),
  staleTime: 0,
})
const data = computed(() => detail.data.value ?? null)
const place = computed(() => data.value?.place ?? null)
const loadError = computed(() =>
  detail.error.value ? problemMessage(toProblem(detail.error.value)) : null,
)

const STATUS: Record<
  PlaceStatus,
  { tone: 'warning' | 'success' | 'danger' | 'neutral'; text: string }
> = {
  PENDING: { tone: 'warning', text: '● Chờ duyệt' },
  APPROVED: { tone: 'success', text: '✓ Đã duyệt' },
  REJECTED: { tone: 'danger', text: '✕ Đã từ chối' },
  HIDDEN: { tone: 'neutral', text: '✕ Đã ẩn' },
}

const pending = computed(() => place.value?.status === 'PENDING')
const ownProposal = computed(() => data.value?.proposer.id === auth.user?.id)
const schedule = computed(() => (place.value ? weekSchedule(place.value.openingHours) : []))
const price = computed(() =>
  place.value ? formatPriceRange(place.value.priceMin, place.value.priceMax) : null,
)

const reason = ref('')
const reasonError = ref<string | null>(null)
const reasonSection = useTemplateRef<HTMLElement>('reasonSection')
const { submitting, error, submit } = useDecision('PLACE', () => detail.refetch())

async function approve(): Promise<void> {
  if (!place.value) return
  const version = place.value.version
  await submit(
    () => moderationApi.decidePlace(placeId.value, { decision: 'APPROVE', version }),
    'Đã duyệt và đăng địa điểm.',
  )
}

async function reject(): Promise<void> {
  if (!place.value) return
  if (!reason.value.trim()) {
    reasonError.value = 'Chọn lý do (hoặc ghi rõ lý do khác) trước khi từ chối.'
    reasonSection.value?.querySelector('select')?.focus()
    return
  }
  reasonError.value = null
  const version = place.value.version
  await submit(
    () =>
      moderationApi.decidePlace(placeId.value, {
        decision: 'REJECT',
        reason: reason.value.trim(),
        version,
      }),
    'Đã từ chối đề xuất.',
  )
}
</script>

<template>
  <div>
    <p v-if="detail.isPending.value" class="text-ink-muted">Đang tải…</p>
    <p v-else-if="loadError" role="alert" class="ls-field__error">✕ {{ loadError }}</p>

    <template v-else-if="data && place">
      <ModerationHead type="PLACE" :ref-label="`#${place.id}`" :title="place.name">
        <template #meta>
          <LsBadge :tone="STATUS[place.status].tone">{{ STATUS[place.status].text }}</LsBadge>
          <span
            >Đề xuất bởi <b>{{ data.proposer.displayName }}</b></span
          >
          <time :datetime="data.submittedAt">{{ formatDateTime(data.submittedAt) }}</time>
          <span>{{ formatNumber(place.photos.length) }} ảnh</span>
        </template>
        <template v-if="pending" #actions>
          <LsAdminButton
            variant="danger"
            :disabled="ownProposal"
            :loading="submitting"
            @click="reject"
          >
            Từ chối
          </LsAdminButton>
          <LsAdminButton
            variant="primary"
            :disabled="ownProposal"
            :loading="submitting"
            @click="approve"
          >
            Duyệt &amp; đăng
          </LsAdminButton>
        </template>
      </ModerationHead>

      <p v-if="error" role="alert" class="ls-field__error mt-4">✕ {{ error }}</p>
      <p v-if="pending && ownProposal" class="mt-4 text-caption text-ink-muted">
        ● Đây là đề xuất của chính bạn — một kiểm duyệt viên khác sẽ duyệt.
      </p>
      <p v-if="!pending" class="mt-4 mb-0">
        Đề xuất này đã được xử lý<template v-if="place.rejectReason">
          — lý do từ chối: <b>{{ place.rejectReason }}</b></template
        >.
        <RouterLink
          v-if="place.status === 'APPROVED'"
          class="ls-link"
          :to="{ name: 'place', params: { slug: place.slug } }"
          >Xem trang công khai</RouterLink
        >
      </p>

      <section class="ls-section fs">
        <div class="fs__intro">
          <h2>Thông tin đề xuất</h2>
          <p>Đối chiếu tên, danh mục và mô tả với ảnh đính kèm.</p>
        </div>
        <dl class="ls-dl fs__grid">
          <div>
            <dt>Danh mục</dt>
            <dd>{{ place.category.name }}</dd>
          </div>
          <div>
            <dt>Thẻ</dt>
            <dd>{{ place.tags.length ? place.tags.map(tagLabel).join(', ') : '—' }}</dd>
          </div>
          <div class="col-span-full">
            <dt>Mô tả</dt>
            <dd>{{ place.description || '—' }}</dd>
          </div>
          <div>
            <dt>Mức giá</dt>
            <dd>{{ price ?? 'Chưa khai' }}</dd>
          </div>
          <div>
            <dt>Tiện ích</dt>
            <dd>
              {{ place.amenities.length ? place.amenities.map((a) => a.name).join(', ') : '—' }}
            </dd>
          </div>
          <div class="col-span-full">
            <dt>Giờ mở cửa</dt>
            <dd>
              <template v-if="place.openingHours.length">
                <span v-for="day in schedule" :key="day.dayOfWeek" class="block">
                  {{ day.name }}: {{ day.slots || 'Nghỉ' }}
                </span>
              </template>
              <template v-else>Chưa khai</template>
            </dd>
          </div>
        </dl>
      </section>

      <section class="ls-section fs">
        <div class="fs__intro">
          <h2>Vị trí &amp; liên hệ</h2>
          <p>Đối chiếu ghim bản đồ với địa chỉ.</p>
        </div>
        <div class="grid max-w-[720px] gap-6">
          <dl class="ls-dl fs__grid">
            <div class="col-span-full">
              <dt>Địa chỉ</dt>
              <dd>{{ place.address }}</dd>
            </div>
            <div>
              <dt>Tỉnh / thành</dt>
              <dd>{{ place.city }}</dd>
            </div>
            <div>
              <dt>Số điện thoại</dt>
              <dd>{{ place.phone || '—' }}</dd>
            </div>
            <div class="col-span-full">
              <dt>Website</dt>
              <dd>{{ place.website || '—' }}</dd>
            </div>
          </dl>
          <PlaceMap class="h-[240px]" :places="[place]" compact />
        </div>
      </section>

      <section class="ls-section fs">
        <div class="fs__intro">
          <h2>Ảnh đính kèm</h2>
          <p>Ảnh đầu tiên là ảnh bìa.</p>
        </div>
        <ul v-if="place.photos.length" class="ls-thumbs m-0 list-none p-0">
          <li v-for="(photo, i) in place.photos" :key="photo.id" class="ls-thumb">
            <PlacePhoto :photo="photo" :alt="`Ảnh ${i + 1} — ${place.name}`" sizes="180px" />
            <span
              >{{ i === 0 ? 'Ảnh bìa' : `Ảnh ${i + 1}`
              }}{{ photo.status === 'READY' ? '' : ' · đang xử lý' }}</span
            >
          </li>
        </ul>
        <p v-else class="m-0 text-ink-muted">Không có ảnh đính kèm.</p>
      </section>

      <section v-if="data.possibleDuplicates.length" class="ls-section fs">
        <div class="fs__intro">
          <h2>Có thể trùng</h2>
          <p>Tên gần giống trong bán kính 50 m — gồm cả đề xuất đang chờ của người khác.</p>
        </div>
        <div class="max-w-[720px] overflow-x-auto">
          <table class="ls-table">
            <thead>
              <tr>
                <th scope="col">Địa điểm</th>
                <th scope="col">Trạng thái</th>
                <th scope="col" class="r">Cách</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="other in data.possibleDuplicates" :key="other.id">
                <td>
                  <RouterLink
                    class="ls-link"
                    :to="
                      other.status === 'APPROVED'
                        ? { name: 'place', params: { slug: other.slug } }
                        : { name: 'admin-moderation-place', params: { id: other.id } }
                    "
                    >{{ other.name }}</RouterLink
                  >
                  <div class="text-caption text-ink-muted">{{ other.address }}</div>
                </td>
                <td>
                  <LsBadge :tone="STATUS[other.status].tone">{{
                    STATUS[other.status].text
                  }}</LsBadge>
                </td>
                <td class="r num whitespace-nowrap">{{ formatNumber(other.distanceM ?? 0) }} m</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <div v-if="pending && !ownProposal" ref="reasonSection">
        <RejectReason
          v-model="reason"
          :presets="[
            'Trùng địa điểm đã có',
            'Thông tin không xác thực',
            'Nội dung vi phạm quy tắc',
          ]"
          :error="reasonError"
          hint="Người đề xuất nhận lý do này trong thông báo và thấy trên trang cá nhân."
        />
      </div>
    </template>
  </div>
</template>
