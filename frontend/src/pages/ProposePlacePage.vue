<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { toTypedSchema } from '@vee-validate/zod'
import { useField, useForm } from 'vee-validate'
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'

import { placesApi, type PlaceProposal } from '@/api/places'
import { problemMessage, toProblem, type Problem } from '@/api/problem'
import LocationPicker from '@/components/place/LocationPicker.vue'
import PhotoPicker from '@/components/review/PhotoPicker.vue'
import { releaseDraft, type DraftPhoto } from '@/components/review/draftPhoto'
import FormError from '@/components/ui/FormError.vue'
import FormInput from '@/components/ui/FormInput.vue'
import LsButton from '@/components/ui/LsButton.vue'
import LsSteps from '@/components/ui/LsSteps.vue'
import { useAmenities, useCategories } from '@/composables/catalogQueries'
import { splitServerErrors } from '@/composables/serverErrors'
import { useNotify } from '@/plugins/toast'
import { useAuthStore } from '@/stores/auth'
import type { GeoPoint, PlaceDetail } from '@/types/api'
import { clearDraft, draftKey, loadDraft, saveDraft } from '@/utils/draft'
import { formatDistance } from '@/utils/geo'
import { fullAddress } from '@/utils/format'
import {
  TAGS_MAX,
  WEEKDAYS,
  defaultWeek,
  parseTags,
  placeSchema,
  toOpeningHours,
  type DayHours,
  type PlaceForm,
} from '@/validation/place'

/**
 * P05 Đề xuất địa điểm (UC11, FR-15, FR-16, UC22). Một trang, thanh tiến độ theo phần đã điền (sitemap: "một trang có
 * thanh tiến độ"); section tiêu đề trái + trường phải như AdminForm để người đề xuất và kiểm duyệt viên nhìn cùng dữ
 * liệu. Trường theo luồng chính UC11: tên, danh mục, thẻ, mô tả · địa chỉ, thành phố, ghim bản đồ · giá, liên hệ, giờ
 * mở cửa, tiện ích · ảnh.
 *
 * Chốt 2026-10-17 (supervisor):
 * - **Vị trí**: ghim trên bản đồ hoặc "Vị trí của tôi" — không dùng dịch vụ đổi địa chỉ ra toạ độ.
 * - **Nháp tự lưu trong trình duyệt** (theo tài khoản, không gồm ảnh) — backend không có trạng thái nháp; "Lưu nháp"
 *   lưu ngay và báo, khôi phục khi quay lại.
 *
 * Nghi trùng (U7, UC11 6a): khi đã có tên + ghim, hỏi `/places/duplicates`; có ứng viên thì phải xác nhận "đây là địa
 * điểm khác" mới gửi được — không chặn hẳn (backend chỉ cảnh báo).
 */
const FIELDS = [
  'name',
  'categoryId',
  'tags',
  'description',
  'address',
  'city',
  'location',
  'priceMin',
  'priceMax',
  'phone',
  'website',
] as const
const DUPLICATE_DEBOUNCE_MS = 600
const DRAFT_DEBOUNCE_MS = 800

interface Draft {
  values: Partial<PlaceForm>
  week: DayHours[]
  amenityIds: number[]
  savedAt: string
}

const auth = useAuthStore()
const notify = useNotify()
const uid = useId()
const categories = useCategories()
const amenities = useAmenities()

const { handleSubmit, isSubmitting, setFieldError, setValues, resetForm, values } =
  useForm<PlaceForm>({
    validationSchema: toTypedSchema(placeSchema),
    initialValues: {
      name: '',
      tags: '',
      description: '',
      address: '',
      city: '',
      location: undefined,
      priceMin: null,
      priceMax: null,
      phone: '',
      website: '',
    },
  })
const categoryId = useField<number | undefined>('categoryId')
const description = useField<string>('description')
const location = useField<GeoPoint | null>('location')
const priceMin = useField<number | null>('priceMin')
const priceMax = useField<number | null>('priceMax')

const week = ref<DayHours[]>(defaultWeek())
const amenityIds = ref<number[]>([])
const drafts = ref<DraftPhoto[]>([])
const formError = ref<string | null>(null)
const result = ref<PlaceDetail | null>(null)

const tagsPreview = computed(() => parseTags(values.tags ?? ''))

// ─── Thanh tiến độ ──────────────────────────────────────────────────────────
const STEPS = ['1 · Thông tin', '2 · Vị trí', '3 · Chi tiết & ảnh', '4 · Gửi'] as const
const infoDone = computed(() => (values.name ?? '').trim().length >= 2 && values.categoryId != null)
const locationDone = computed(
  () => !!(values.address ?? '').trim() && !!(values.city ?? '').trim() && !!values.location,
)
const currentStep = computed(() => (!infoDone.value ? 0 : !locationDone.value ? 1 : 3))

// ─── Nghi trùng (U7) ────────────────────────────────────────────────────────
const duplicateQuery = ref<{ name: string; location: GeoPoint } | null>(null)
let duplicateTimer: ReturnType<typeof setTimeout> | undefined
watch(
  () => [values.name, values.location] as const,
  ([name, point]) => {
    clearTimeout(duplicateTimer)
    const trimmed = (name ?? '').trim()
    duplicateTimer = setTimeout(() => {
      duplicateQuery.value =
        trimmed.length >= 2 && point ? { name: trimmed, location: point } : null
    }, DUPLICATE_DEBOUNCE_MS)
  },
)
const duplicates = useQuery({
  queryKey: computed(() => ['place-duplicates', duplicateQuery.value]),
  queryFn: () => placesApi.duplicates(duplicateQuery.value!.name, duplicateQuery.value!.location),
  enabled: computed(() => duplicateQuery.value !== null),
  staleTime: 60_000,
})
const candidates = computed(() => (duplicateQuery.value ? (duplicates.data.value ?? []) : []))
const confirmedDifferent = ref(false)
watch(candidates, () => (confirmedDifferent.value = false))

// ─── Nháp trong trình duyệt ────────────────────────────────────────────────
const storageKey = computed(() => (auth.user ? draftKey('propose-place', auth.user.id) : null))
const restoredAt = ref<string | null>(null)
let draftTimer: ReturnType<typeof setTimeout> | undefined

function writeDraft(): boolean {
  if (!storageKey.value) return false
  const draft: Draft = {
    values: { ...values },
    week: week.value,
    amenityIds: amenityIds.value,
    savedAt: new Date().toISOString(),
  }
  return saveDraft(storageKey.value, draft)
}

onMounted(() => {
  const draft = storageKey.value ? loadDraft<Draft>(storageKey.value) : null
  if (!draft) return
  setValues(draft.values, false)
  week.value = draft.week?.length === 7 ? draft.week : defaultWeek()
  amenityIds.value = draft.amenityIds ?? []
  restoredAt.value = draft.savedAt
})

watch(
  [() => ({ ...values }), week, amenityIds],
  () => {
    if (result.value) return
    clearTimeout(draftTimer)
    draftTimer = setTimeout(writeDraft, DRAFT_DEBOUNCE_MS)
  },
  { deep: true },
)

function saveNow(): void {
  clearTimeout(draftTimer)
  if (writeDraft()) {
    notify.success('Đã lưu nháp trên trình duyệt này (không gồm ảnh).')
  } else {
    notify.error('Trình duyệt không cho lưu nháp — hãy gửi đề xuất trong phiên này.')
  }
}

function discardDraft(): void {
  if (storageKey.value) clearDraft(storageKey.value)
  restoredAt.value = null
  resetForm()
  week.value = defaultWeek()
  amenityIds.value = []
}

onBeforeUnmount(() => {
  clearTimeout(draftTimer)
  clearTimeout(duplicateTimer)
  drafts.value.forEach(releaseDraft)
})

// ─── Gửi ────────────────────────────────────────────────────────────────────
function parsePrice(event: Event): number | null {
  const raw = (event.target as HTMLInputElement).value.trim()
  return raw === '' ? null : Number(raw)
}

const submit = handleSubmit(async (v) => {
  formError.value = null
  if (candidates.value.length > 0 && !confirmedDifferent.value) {
    formError.value =
      'Có địa điểm tên gần giống ngay gần đây — hãy kiểm tra và xác nhận đây là địa điểm khác.'
    return
  }
  const proposal: PlaceProposal = {
    name: v.name,
    categoryId: v.categoryId,
    description: v.description || undefined,
    address: v.address,
    city: v.city,
    location: v.location,
    priceMin: v.priceMin ?? undefined,
    priceMax: v.priceMax ?? undefined,
    phone: v.phone || undefined,
    website: v.website || undefined,
    openingHours: toOpeningHours(week.value),
    amenityIds: amenityIds.value,
    tags: parseTags(v.tags ?? ''),
  }
  try {
    const created = await placesApi.propose(
      proposal,
      drafts.value.map((d) => ({ blob: d.file, fileName: d.file.name })),
    )
    clearTimeout(draftTimer)
    if (storageKey.value) clearDraft(storageKey.value)
    drafts.value.forEach(releaseDraft)
    drafts.value = []
    result.value = created
    window.scrollTo({ top: 0 })
  } catch (error) {
    showServerError(toProblem(error))
  }
})

/** Lỗi trường gắn vào ô (`location.lat` → ô bản đồ); ảnh nói rõ ảnh thứ mấy; giờ / tiện ích / thẻ thành lỗi chung. */
function showServerError(problem: Problem): void {
  const mapped: Record<string, string> = {}
  for (const [field, message] of Object.entries(problem.fieldErrors)) {
    const photo = /^photos\[(\d+)]$/.exec(field)
    if (photo) mapped[field] = `Ảnh thứ ${Number(photo[1]) + 1}: ${message}`
    else if (field.startsWith('location'))
      mapped.location = 'Vị trí chưa hợp lệ — hãy đặt lại ghim.'
    else if (field.startsWith('tags')) mapped.tags = message
    else if (field.startsWith('openingHours')) mapped[field] = `Giờ mở cửa: ${message}`
    else mapped[field] = message
  }
  const { fieldErrors, formError: message } = splitServerErrors(
    { ...problem, fieldErrors: mapped },
    FIELDS,
  )
  Object.entries(fieldErrors).forEach(([field, msg]) =>
    setFieldError(field as (typeof FIELDS)[number], msg),
  )
  formError.value = message ?? (Object.keys(fieldErrors).length ? null : problemMessage(problem))
}

function proposeAnother(): void {
  result.value = null
  restoredAt.value = null
  resetForm()
  week.value = defaultWeek()
  amenityIds.value = []
}
</script>

<template>
  <!-- Sau khi gửi -->
  <section v-if="result" class="ls-pub max-w-[640px] py-12" aria-live="polite">
    <span class="ls-kick">Đóng góp</span>
    <h1 class="ls-h1 mt-2">Đã gửi — chờ kiểm duyệt</h1>
    <p class="mt-4">
      <b>{{ result.name }}</b> sẽ hiển thị công khai sau khi kiểm duyệt viên xác nhận. Trong lúc
      chờ, chỉ bạn xem được trang của địa điểm này. Bạn sẽ nhận thông báo khi có kết quả.
    </p>
    <div class="mt-6 flex flex-wrap gap-3">
      <LsButton :to="{ name: 'place', params: { slug: result.slug } }">Xem đề xuất</LsButton>
      <LsButton variant="ghost" @click="proposeAnother">Đề xuất địa điểm khác</LsButton>
    </div>
  </section>

  <article v-else class="ls-pub py-8">
    <span class="ls-kick">Đóng góp</span>
    <h1 class="ls-h1 mt-2">Đề xuất địa điểm mới</h1>
    <p class="mt-3 max-w-[640px] text-ink-muted">
      Địa điểm sẽ hiển thị sau khi kiểm duyệt viên xác nhận. Bạn nhận thông báo khi có kết quả.
    </p>
    <LsSteps class="mt-6" :steps="STEPS" :current="currentStep" />

    <p v-if="restoredAt" class="mt-6 flex flex-wrap items-center gap-3" role="status">
      ● Đã khôi phục bản nháp (ảnh không được lưu — hãy chọn lại).
      <button
        type="button"
        class="ls-link cursor-pointer border-0 bg-transparent p-0"
        @click="discardDraft"
      >
        Bỏ nháp, điền lại từ đầu
      </button>
    </p>

    <form class="mt-6" novalidate @submit="submit">
      <!-- Thông tin -->
      <section
        class="grid gap-6 border-t border-line py-8 md:grid-cols-[200px_1fr]"
        :aria-labelledby="`${uid}-info`"
      >
        <h2 :id="`${uid}-info`" class="m-0 text-title">Thông tin</h2>
        <div class="grid max-w-[640px] gap-5">
          <FormInput name="name" label="Tên địa điểm *" autocomplete="off" />

          <div v-if="candidates.length" class="flex flex-col gap-2" role="alert">
            <p class="m-0"><b>Đã có địa điểm tên gần giống ngay gần đây.</b> Có phải nơi này?</p>
            <ul class="m-0 flex list-disc flex-col gap-1 pl-5">
              <li v-for="c in candidates" :key="c.id">
                <RouterLink
                  class="ls-link"
                  :to="{ name: 'place', params: { slug: c.slug } }"
                  target="_blank"
                  >{{ c.name }}</RouterLink
                >
                — {{ fullAddress(c)
                }}<template v-if="c.distanceM != null">
                  · cách {{ formatDistance(c.distanceM) }}</template
                ><template v-if="c.status === 'PENDING'">
                  · đề xuất của bạn đang chờ duyệt</template
                >
              </li>
            </ul>
            <label class="ls-check">
              <input v-model="confirmedDifferent" type="checkbox" />
              Đây là địa điểm khác, tôi vẫn muốn đề xuất
            </label>
          </div>

          <div class="ls-field">
            <label :for="`${uid}-category`">Danh mục *</label>
            <span class="ls-selectwrap">
              <select
                :id="`${uid}-category`"
                v-model.number="categoryId.value.value"
                class="ls-select"
                :aria-invalid="categoryId.errorMessage.value ? 'true' : undefined"
              >
                <option :value="undefined" disabled>Chọn danh mục</option>
                <template v-for="root in categories.data.value ?? []" :key="root.id">
                  <optgroup v-if="root.children.length" :label="root.name">
                    <option v-for="child in root.children" :key="child.id" :value="child.id">
                      {{ child.name }}
                    </option>
                  </optgroup>
                  <option v-else :value="root.id">{{ root.name }}</option>
                </template>
              </select>
            </span>
            <span v-if="categoryId.errorMessage.value" class="ls-field__error"
              >✕ {{ categoryId.errorMessage.value }}</span
            >
          </div>

          <div>
            <FormInput
              name="tags"
              label="Thẻ"
              :hint="`Cách nhau bằng dấu phẩy, tối đa ${TAGS_MAX} thẻ — vd. view đẹp, hợp gia đình.`"
            />
            <ul
              v-if="tagsPreview.length"
              class="ls-tags mt-2 text-caption!"
              aria-label="Thẻ sẽ gắn"
            >
              <li v-for="tag in tagsPreview" :key="tag">#{{ tag }}</li>
            </ul>
          </div>

          <div class="ls-field">
            <label :for="`${uid}-description`">Mô tả</label>
            <textarea
              :id="`${uid}-description`"
              v-model="description.value.value"
              class="ls-textarea"
              rows="4"
              maxlength="5000"
              :aria-invalid="description.errorMessage.value ? 'true' : undefined"
            ></textarea>
            <span v-if="description.errorMessage.value" class="ls-field__error"
              >✕ {{ description.errorMessage.value }}</span
            >
          </div>
        </div>
      </section>

      <!-- Vị trí -->
      <section
        class="grid gap-6 border-t border-line py-8 md:grid-cols-[200px_1fr]"
        :aria-labelledby="`${uid}-location`"
      >
        <h2 :id="`${uid}-location`" class="m-0 text-title">Vị trí</h2>
        <div class="grid max-w-[640px] gap-5">
          <div class="grid gap-5 sm:grid-cols-[2fr_1fr]">
            <FormInput name="address" label="Địa chỉ *" autocomplete="street-address" />
            <FormInput name="city" label="Thành phố *" autocomplete="address-level2" />
          </div>
          <div class="ls-field">
            <span class="ls-field__label">Vị trí trên bản đồ *</span>
            <LocationPicker v-model="location.value.value" />
            <span v-if="location.errorMessage.value" class="ls-field__error"
              >✕ {{ location.errorMessage.value }}</span
            >
          </div>
        </div>
      </section>

      <!-- Chi tiết -->
      <section
        class="grid gap-6 border-t border-line py-8 md:grid-cols-[200px_1fr]"
        :aria-labelledby="`${uid}-details`"
      >
        <div>
          <h2 :id="`${uid}-details`" class="m-0 text-title">Chi tiết</h2>
          <p class="mt-1 text-caption text-ink-muted">
            Tuỳ chọn — điền càng đủ càng dễ được duyệt.
          </p>
        </div>
        <div class="grid max-w-[640px] gap-6">
          <div class="grid gap-5 sm:grid-cols-2">
            <div class="ls-field">
              <label :for="`${uid}-price-min`">Giá thấp nhất (₫ / người)</label>
              <input
                :id="`${uid}-price-min`"
                class="ls-input"
                type="number"
                min="0"
                step="1000"
                inputmode="numeric"
                :value="priceMin.value.value ?? ''"
                :aria-invalid="priceMin.errorMessage.value ? 'true' : undefined"
                @input="priceMin.value.value = parsePrice($event)"
              />
              <span v-if="priceMin.errorMessage.value" class="ls-field__error"
                >✕ {{ priceMin.errorMessage.value }}</span
              >
            </div>
            <div class="ls-field">
              <label :for="`${uid}-price-max`">Giá cao nhất (₫ / người)</label>
              <input
                :id="`${uid}-price-max`"
                class="ls-input"
                type="number"
                min="0"
                step="1000"
                inputmode="numeric"
                :value="priceMax.value.value ?? ''"
                :aria-invalid="priceMax.errorMessage.value ? 'true' : undefined"
                @input="priceMax.value.value = parsePrice($event)"
              />
              <span v-if="priceMax.errorMessage.value" class="ls-field__error"
                >✕ {{ priceMax.errorMessage.value }}</span
              >
            </div>
            <FormInput name="phone" label="Số điện thoại" autocomplete="tel" />
            <FormInput name="website" label="Trang web" placeholder="https://" />
          </div>

          <fieldset class="m-0 border-0 p-0">
            <legend class="mb-3 font-semibold">Giờ mở cửa</legend>
            <div class="flex flex-col gap-2">
              <div
                v-for="(day, i) in week"
                :key="WEEKDAYS[i]!.day"
                class="grid grid-cols-[110px_auto] items-center gap-3 sm:grid-cols-[110px_auto_1fr]"
              >
                <label class="ls-check">
                  <input v-model="day.open" type="checkbox" />
                  {{ WEEKDAYS[i]!.label }}
                </label>
                <span v-if="!day.open" class="text-caption text-ink-muted">Đóng cửa</span>
                <span v-else class="flex items-center gap-2">
                  <label class="sr-only" :for="`${uid}-open-${i}`"
                    >{{ WEEKDAYS[i]!.label }} mở lúc</label
                  >
                  <input
                    :id="`${uid}-open-${i}`"
                    v-model="day.openTime"
                    class="ls-input w-[120px]"
                    type="time"
                  />
                  –
                  <label class="sr-only" :for="`${uid}-close-${i}`"
                    >{{ WEEKDAYS[i]!.label }} đóng lúc</label
                  >
                  <input
                    :id="`${uid}-close-${i}`"
                    v-model="day.closeTime"
                    class="ls-input w-[120px]"
                    type="time"
                  />
                </span>
              </div>
            </div>
            <p class="mt-2 mb-0 text-caption text-ink-muted">
              Giờ đóng sớm hơn giờ mở nghĩa là mở qua đêm (vd. 18:00 – 02:00).
            </p>
          </fieldset>

          <fieldset v-if="amenities.data.value?.length" class="m-0 border-0 p-0">
            <legend class="mb-3 font-semibold">Tiện ích</legend>
            <div class="grid gap-2 sm:grid-cols-2">
              <label v-for="a in amenities.data.value" :key="a.id" class="ls-check">
                <input v-model="amenityIds" type="checkbox" :value="a.id" />
                {{ a.name }}
              </label>
            </div>
          </fieldset>
        </div>
      </section>

      <!-- Ảnh -->
      <section
        class="grid gap-6 border-t border-line py-8 md:grid-cols-[200px_1fr]"
        :aria-labelledby="`${uid}-photos`"
      >
        <h2 :id="`${uid}-photos`" class="m-0 text-title">Ảnh</h2>
        <div class="max-w-[640px]">
          <PhotoPicker v-model="drafts" :disabled="isSubmitting" />
        </div>
      </section>

      <div class="flex flex-col gap-4 border-t border-line pt-6">
        <FormError :message="formError" />
        <div class="flex flex-wrap items-center gap-3">
          <LsButton type="submit" :loading="isSubmitting">Gửi đề xuất</LsButton>
          <LsButton variant="ghost" @click="saveNow">Lưu nháp</LsButton>
        </div>
        <p class="m-0 text-caption text-ink-muted">
          Nháp tự lưu trên trình duyệt này khi bạn nhập (không gồm ảnh).
        </p>
      </div>
    </form>
  </article>
</template>
