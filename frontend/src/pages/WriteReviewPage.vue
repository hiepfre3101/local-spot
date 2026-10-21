<script setup lang="ts">
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { toTypedSchema } from '@vee-validate/zod'
import { isAxiosError } from 'axios'
import { useField, useForm } from 'vee-validate'
import { computed, onBeforeUnmount, ref, useId, watch } from 'vue'
import { useRouter } from 'vue-router'

import { placesApi } from '@/api/places'
import { problemMessage, toProblem, type Problem } from '@/api/problem'
import { reviewsApi, type PhotoUpload } from '@/api/reviews'
import LsRating from '@/components/place/LsRating.vue'
import PlacePhoto from '@/components/place/PlacePhoto.vue'
import PhotoPicker from '@/components/review/PhotoPicker.vue'
import RatingInput from '@/components/review/RatingInput.vue'
import { releaseDraft, type DraftPhoto } from '@/components/review/draftPhoto'
import FormError from '@/components/ui/FormError.vue'
import LsButton from '@/components/ui/LsButton.vue'
import { splitServerErrors } from '@/composables/serverErrors'
import NotFoundPage from '@/pages/errors/NotFoundPage.vue'
import { useNotify } from '@/plugins/toast'
import { useAuthStore } from '@/stores/auth'
import type { Review, ReviewStatus } from '@/types/api'
import { fullAddress } from '@/utils/format'
import {
  REVIEW_CONTENT_MAX,
  reviewSchema,
  todayInVietnam,
  type ReviewForm,
} from '@/validation/review'

/**
 * P04 Viết / sửa đánh giá (UC12, UC13, FR-19). Một trang cho cả hai chế độ: địa điểm đã có `myReviewId` thì nạp review
 * đó để sửa (1 đánh giá / người / địa điểm — D2). Kết quả gửi theo `status` backend trả về (DECISIONS 29/09): PUBLISHED
 * "Đã đăng", PENDING "Đang chờ duyệt" — trust < 30 / cờ IP vào hàng chờ FIFO (UC28).
 *
 * Không có bước gắn thẻ của bản thiết kế (supervisor chốt 2026-10-17): chưa có API cho thành viên gắn thẻ địa điểm có
 * sẵn; UC22 làm ở form đề xuất (P05).
 */
const props = defineProps<{ slug: string }>()

const FIELDS = ['rating', 'content', 'visitedAt'] as const
const auth = useAuthStore()
const router = useRouter()
const queryClient = useQueryClient()
const notify = useNotify()
const contentId = useId()
const visitedId = useId()
const today = todayInVietnam()

// ─── Nạp địa điểm + review của tôi ─────────────────────────────────────────
const detail = useQuery({
  queryKey: computed(() => ['place', props.slug]),
  queryFn: () => placesApi.detail(props.slug),
})
const place = computed(() => detail.data.value)
const myReviewId = computed(() => place.value?.myReviewId ?? null)
const existing = useQuery({
  queryKey: computed(() => ['review', myReviewId.value]),
  queryFn: () => reviewsApi.get(myReviewId.value!),
  enabled: computed(() => myReviewId.value !== null),
  retry: (count, error) => count < 1 && !isStatus(error, 404),
})
const review = computed(() => existing.data.value ?? null)
const editing = computed(() => myReviewId.value !== null)

const placeMissing = computed(() => isStatus(detail.error.value, 404))
/** Review đã xóa vẫn chiếm chỗ (D2) — `myReviewId` có nhưng không đọc được nữa. */
const reviewDeleted = computed(() => editing.value && isStatus(existing.error.value, 404))

/** Lý do không cho viết / sửa, theo đúng thứ tự backend kiểm (UC12). `null` = được. */
const blocked = computed<string | null>(() => {
  const p = place.value
  if (!p) return null
  if (reviewDeleted.value) {
    return 'Bạn đã xoá đánh giá cho địa điểm này. Mỗi người chỉ viết một đánh giá cho một địa điểm, nên không thể viết lại.'
  }
  if (review.value?.status === 'HIDDEN') {
    return 'Đánh giá của bạn đã bị ẩn sau khi bị báo cáo, nên không sửa được nữa.'
  }
  if (!editing.value && p.status !== 'APPROVED') {
    return 'Địa điểm này chưa được duyệt nên chưa nhận đánh giá.'
  }
  if (!editing.value && auth.user?.ownedPlaceIds.includes(p.id)) {
    return 'Bạn là chủ địa điểm này nên không thể tự đánh giá.'
  }
  return null
})
const needsVerification = computed(() => auth.user !== null && !auth.user.emailVerified)

watch(
  place,
  (p) => {
    if (p) document.title = `${editing.value ? 'Sửa' : 'Viết'} đánh giá · ${p.name} · LocalSpot`
  },
  { immediate: true },
)

// ─── Form ─────────────────────────────────────────────────────────────────
const { handleSubmit, isSubmitting, setFieldError, resetForm } = useForm<ReviewForm>({
  validationSchema: toTypedSchema(reviewSchema(today)),
  initialValues: { content: '', visitedAt: today },
})
const content = useField<string>('content', undefined, { validateOnValueUpdate: false })
const visitedAt = useField<string>('visitedAt', undefined, { validateOnValueUpdate: false })
const contentLength = computed(() => (content.value.value ?? '').trim().length)
/**
 * Quay lại quán (chốt 2026-10-19, phương án A): đã có đánh giá + hôm nay đã check-in → gợi ý cập nhật theo lần ghé này.
 * Nhãn "đã check-in" tính theo lần sửa nội dung gần nhất, nên lưu thay đổi hôm nay là được nhãn.
 */
const revisit = computed(
  () =>
    editing.value &&
    !!place.value?.checkedInToday &&
    !!review.value &&
    !review.value.authorCheckedIn,
)

const drafts = ref<DraftPhoto[]>([])
const removedPhotoIds = ref<number[]>([])
const keptPhotos = computed(() =>
  (review.value?.photos ?? []).filter((p) => !removedPhotoIds.value.includes(p.id)),
)

// Màn sửa: điền form bằng review đã có (một lần — không ghi đè khi người dùng đang gõ và query nạp lại)
watch(
  review,
  (r, previous) => {
    if (r && !previous) {
      resetForm({ values: { rating: r.rating, content: r.content, visitedAt: r.visitedAt } })
    }
  },
  { immediate: true },
)

const formError = ref<string | null>(null)
const result = ref<{ status: ReviewStatus; edited: boolean } | null>(null)

function uploads(): PhotoUpload[] {
  return drafts.value.map((d) => ({ blob: d.file, fileName: d.file.name }))
}

const submit = handleSubmit(async (values) => {
  const p = place.value
  if (!p) return
  formError.value = null
  const input = { rating: values.rating, content: values.content, visitedAt: values.visitedAt }
  try {
    let saved: Review
    if (review.value) {
      saved = await reviewsApi.update(review.value.id, { ...input, version: review.value.version })
      // Giữ `version` mới ngay: bước ảnh lỗi thì lần gửi lại không vấp 409 CONCURRENT_MODIFICATION
      queryClient.setQueryData(['review', saved.id], saved)
      for (const photoId of [...removedPhotoIds.value]) {
        await reviewsApi.deletePhoto(saved.id, photoId)
        removedPhotoIds.value = removedPhotoIds.value.filter((id) => id !== photoId)
      }
      if (drafts.value.length) {
        await reviewsApi.addPhotos(saved.id, uploads())
      }
    } else {
      saved = await reviewsApi.create(p.id, input, uploads())
    }
    drafts.value.forEach(releaseDraft)
    drafts.value = []
    removedPhotoIds.value = []
    result.value = { status: saved.status, edited: editing.value }
    await queryClient.invalidateQueries({ queryKey: ['place'] })
    queryClient.setQueryData(['review', saved.id], saved)
  } catch (error) {
    showServerError(toProblem(error))
  }
})

/** Lỗi trường gắn vào ô; lỗi ảnh (`photos[i]`) nói rõ ảnh thứ mấy; còn lại thành lỗi chung. */
function showServerError(problem: Problem): void {
  const photoErrors: Record<string, string> = {}
  for (const [field, message] of Object.entries(problem.fieldErrors)) {
    const match = /^photos\[(\d+)]$/.exec(field)
    photoErrors[field] = match ? `Ảnh thứ ${Number(match[1]) + 1}: ${message}` : message
  }
  const { fieldErrors, formError: message } = splitServerErrors(
    { ...problem, fieldErrors: photoErrors },
    FIELDS,
  )
  Object.entries(fieldErrors).forEach(([field, msg]) =>
    setFieldError(field as (typeof FIELDS)[number], msg),
  )
  formError.value = message ?? (Object.keys(fieldErrors).length ? null : problemMessage(problem))
}

// ─── Xoá (UC13) — xác nhận ngay tại chỗ ───────────────────────────────────
const confirmingDelete = ref(false)
const deleting = ref(false)

async function deleteReview(): Promise<void> {
  const r = review.value
  const p = place.value
  if (!r || !p) return
  deleting.value = true
  try {
    await reviewsApi.remove(r.id)
    await queryClient.invalidateQueries({ queryKey: ['place'] })
    notify.success('Đã xoá đánh giá.')
    await router.push({ name: 'place', params: { slug: p.slug } })
  } catch (error) {
    formError.value = problemMessage(toProblem(error))
    confirmingDelete.value = false
  } finally {
    deleting.value = false
  }
}

onBeforeUnmount(() => drafts.value.forEach(releaseDraft))

function isStatus(error: unknown, status: number): boolean {
  return isAxiosError(error) && error.response?.status === status
}
</script>

<template>
  <NotFoundPage v-if="placeMissing" />

  <section v-else-if="detail.isError.value" class="py-16" role="alert">
    <h1 class="ls-h1">Chưa tải được địa điểm</h1>
    <p class="mt-4 text-ink-muted">Kiểm tra kết nối mạng rồi thử lại.</p>
    <LsButton class="mt-4" @click="detail.refetch()">Thử lại</LsButton>
  </section>

  <div
    v-else-if="!place || (editing && existing.isPending.value && !reviewDeleted)"
    aria-busy="true"
    aria-label="Đang tải"
    class="py-8"
  >
    <span class="ls-skel mb-4 h-4 w-48"></span>
    <span class="ls-skel mb-8 h-11 w-2/3"></span>
    <span class="ls-skel mb-4 h-10 w-64"></span>
    <span class="ls-skel h-40 w-full"></span>
  </div>

  <article v-else class="ls-pub py-8">
    <nav aria-label="Vị trí trang" class="ls-crumb">
      <RouterLink :to="{ name: 'place', params: { slug: place.slug } }">{{
        place.name
      }}</RouterLink>
      / {{ editing ? 'Sửa đánh giá' : 'Viết đánh giá' }}
    </nav>

    <!-- Sau khi gửi: hai kết quả theo trạng thái backend trả về -->
    <section v-if="result" class="max-w-[640px] py-8" aria-live="polite">
      <template v-if="result.status === 'PUBLISHED'">
        <h1 class="ls-h1">{{ result.edited ? 'Đã lưu thay đổi' : 'Đã đăng đánh giá' }}</h1>
        <p class="mt-4">
          Đánh giá của bạn đang hiển thị trên trang địa điểm. Ảnh mới cần ít phút để xử lý trước khi
          hiện.
        </p>
      </template>
      <template v-else>
        <h1 class="ls-h1">Đã gửi — đang chờ duyệt</h1>
        <p class="mt-4">
          Đánh giá của bạn đang ở hàng chờ và được duyệt theo thứ tự gửi. Trong lúc chờ, chỉ bạn
          thấy nó (vẫn sửa hoặc xoá được). Bạn sẽ nhận thông báo khi đánh giá được duyệt hoặc bị từ
          chối kèm lý do.
        </p>
      </template>
      <div class="mt-6 flex flex-wrap gap-3">
        <LsButton :to="{ name: 'place', params: { slug: place.slug } }">Về trang địa điểm</LsButton>
        <LsButton variant="ghost" @click="result = null">Xem lại đánh giá</LsButton>
      </div>
    </section>

    <div v-else class="grid gap-12 pt-6 lg:grid-cols-[1fr_340px] lg:gap-16">
      <div class="min-w-0">
        <h1 class="ls-h1">{{ editing ? 'Sửa đánh giá' : 'Bạn thấy nơi này thế nào?' }}</h1>

        <p v-if="blocked" class="mt-6" role="alert">{{ blocked }}</p>

        <template v-else>
          <p v-if="review?.status === 'REJECTED'" class="mt-4" role="status">
            <b>Đánh giá bị từ chối:</b> {{ review.rejectReason }} Sửa rồi gửi lại để được duyệt lại.
          </p>
          <p v-else-if="review?.status === 'PENDING'" class="mt-4 text-ink-muted" role="status">
            Đánh giá đang chờ duyệt — chỉ bạn thấy cho tới khi được duyệt.
          </p>
          <p v-if="needsVerification" class="mt-4" role="alert">
            Xác thực email trước khi gửi đánh giá — liên kết ở băng nhắc phía trên.
          </p>
          <div v-if="revisit" class="mt-4 flex flex-wrap items-center gap-3" role="status">
            <span
              >✓ Bạn vừa check-in hôm nay. Cập nhật đánh giá theo lần ghé này — đánh giá sẽ có nhãn
              “Đã check-in”.</span
            >
            <LsButton
              v-if="visitedAt.value.value !== today"
              size="sm"
              variant="ghost"
              @click="visitedAt.value.value = today"
              >Đặt ngày đến là hôm nay</LsButton
            >
          </div>

          <form class="mt-8 flex flex-col gap-6" novalidate @submit="submit">
            <RatingInput name="rating" label="Điểm của bạn *" />

            <div class="ls-field">
              <label :for="contentId">Trải nghiệm của bạn *</label>
              <textarea
                :id="contentId"
                v-model="content.value.value"
                class="ls-textarea"
                name="content"
                rows="7"
                :maxlength="REVIEW_CONTENT_MAX"
                :aria-invalid="content.errorMessage.value ? 'true' : undefined"
                :aria-describedby="`${contentId}-hint ${contentId}-count`"
                @blur="content.handleBlur($event, true)"
              ></textarea>
              <span class="flex justify-between gap-4">
                <span :id="`${contentId}-hint`" class="ls-field__hint"
                  >Kể điều cụ thể: món nên thử, giờ đẹp, mẹo gửi xe.</span
                >
                <span :id="`${contentId}-count`" class="ls-field__hint shrink-0"
                  >{{ contentLength }} / {{ REVIEW_CONTENT_MAX.toLocaleString('vi-VN') }}</span
                >
              </span>
              <span v-if="content.errorMessage.value" class="ls-field__error"
                >✕ {{ content.errorMessage.value }}</span
              >
            </div>

            <div class="ls-field max-w-[220px]">
              <label :for="visitedId">Ngày bạn đến *</label>
              <input
                :id="visitedId"
                v-model="visitedAt.value.value"
                class="ls-input"
                type="date"
                name="visitedAt"
                :max="today"
                :aria-invalid="visitedAt.errorMessage.value ? 'true' : undefined"
                @blur="visitedAt.handleBlur($event, true)"
              />
              <span v-if="visitedAt.errorMessage.value" class="ls-field__error"
                >✕ {{ visitedAt.errorMessage.value }}</span
              >
            </div>

            <PhotoPicker
              v-model="drafts"
              :existing="keptPhotos"
              :disabled="isSubmitting"
              @remove-existing="(id) => removedPhotoIds.push(id)"
            />

            <FormError :message="formError" />

            <div class="flex flex-wrap items-center gap-4">
              <LsButton type="submit" :loading="isSubmitting" :disabled="needsVerification">{{
                editing ? 'Lưu thay đổi' : 'Gửi đánh giá'
              }}</LsButton>
              <RouterLink :to="{ name: 'place', params: { slug: place.slug } }">Huỷ</RouterLink>
            </div>
            <p class="m-0 text-caption text-ink-muted">
              Đánh giá từ tài khoản mới hoặc bị hệ thống cảnh báo được duyệt trước khi hiển thị.
            </p>
          </form>

          <section v-if="editing" class="mt-12 border-t border-line pt-6" aria-label="Xoá đánh giá">
            <LsButton v-if="!confirmingDelete" variant="ghost" @click="confirmingDelete = true"
              >Xoá đánh giá</LsButton
            >
            <div v-else role="alert" class="flex flex-col gap-3">
              <p class="m-0">
                <b>Xoá đánh giá này?</b> Sau khi xoá, bạn không thể viết lại đánh giá cho địa điểm
                này.
              </p>
              <div class="flex flex-wrap gap-3">
                <LsButton variant="accent" :loading="deleting" @click="deleteReview"
                  >Xoá vĩnh viễn</LsButton
                >
                <LsButton variant="ghost" @click="confirmingDelete = false">Giữ lại</LsButton>
              </div>
            </div>
          </section>
        </template>
      </div>

      <aside class="flex flex-col gap-8">
        <div>
          <PlacePhoto
            :photo="place.coverPhoto"
            class="aspect-[4/3] w-full overflow-hidden rounded-md"
          />
          <h2 class="mt-3 mb-1 text-title">{{ place.name }}</h2>
          <LsRating :score="place.bayesianScore" :count="place.reviewCount" />
          <p class="mt-1 text-caption text-ink-muted">
            {{ fullAddress(place) }} · {{ place.category.name }}
          </p>
        </div>
        <div>
          <h2 class="mb-2 text-title">Quy tắc cộng đồng</h2>
          <p class="m-0 text-caption">
            Viết về trải nghiệm của chính bạn. Không quảng cáo, không thông tin cá nhân. Đánh giá vi
            phạm có thể bị báo cáo và gỡ.
          </p>
        </div>
      </aside>
    </div>
  </article>
</template>
