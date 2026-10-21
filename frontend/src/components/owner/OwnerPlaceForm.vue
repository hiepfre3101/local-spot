<script setup lang="ts">
import { computed, onBeforeUnmount, ref, useId, watch } from 'vue'

import { ownerApi, type PlaceUpdate } from '@/api/owner'
import { problemMessage, toProblem } from '@/api/problem'
import PhotoPicker from '@/components/review/PhotoPicker.vue'
import { releaseDraft, type DraftPhoto } from '@/components/review/draftPhoto'
import LsButton from '@/components/ui/LsButton.vue'
import { useAmenities } from '@/composables/catalogQueries'
import { useNotify } from '@/plugins/toast'
import type { PlaceDetail } from '@/types/api'
import {
  PHONE,
  WEBSITE,
  WEEKDAYS,
  fromOpeningHours,
  toOpeningHours,
  type DayHours,
} from '@/validation/place'

/**
 * Cập nhật thông tin địa điểm (UC24, FR-32) — áp dụng ngay, không duyệt lại (O5, chốt 2026-10-05). Bố cục section như
 * P05 / AdminForm. Không sửa được tên, vị trí, **địa chỉ** (chốt 2026-10-21), danh mục — backend không nhận; địa chỉ chỉ
 * hiện (tắt) để chủ đối chiếu. Địa điểm do thành viên đề xuất và kiểm duyệt viên duyệt: sai thì báo cáo WRONG_INFO, không
 * phải quán của mình thì đề xuất đúng địa điểm rồi yêu cầu sở hữu. Giá chỉ đặt được, không xoá được (API: `null` = giữ
 * nguyên). Gửi `version`: người khác vừa sửa → 409, báo tải lại.
 *
 * Ảnh: thêm / xoá gọi API ngay (không đợi "Lưu"), xử lý nền như ảnh đề xuất; tối đa 30 ảnh / địa điểm.
 */
const props = defineProps<{ place: PlaceDetail }>()
const emit = defineEmits<{ saved: [place: PlaceDetail] }>()

const PHOTOS_MAX = 30

const uid = useId()
const notify = useNotify()
const amenities = useAmenities()

const description = ref('')
const priceMin = ref<number | null>(null)
const priceMax = ref<number | null>(null)
const phone = ref('')
const website = ref('')
const week = ref<DayHours[]>([])
const amenityIds = ref<number[]>([])
const errors = ref<Record<string, string>>({})
const formError = ref<string | null>(null)
const saving = ref(false)

function load(p: PlaceDetail): void {
  description.value = p.description ?? ''
  priceMin.value = p.priceMin
  priceMax.value = p.priceMax
  phone.value = p.phone ?? ''
  website.value = p.website ?? ''
  week.value = fromOpeningHours(p.openingHours)
  amenityIds.value = p.amenities.map((a) => a.id)
}
watch(() => props.place, load, { immediate: true })

function parsePrice(event: Event): number | null {
  const raw = (event.target as HTMLInputElement).value.trim()
  return raw === '' ? null : Number(raw)
}

function validate(): boolean {
  const e: Record<string, string> = {}
  for (const [key, value] of [
    ['priceMin', priceMin.value],
    ['priceMax', priceMax.value],
  ] as const) {
    if (value !== null && (!Number.isInteger(value) || value < 0))
      e[key] = 'Nhập số nguyên không âm.'
  }
  if (props.place.priceMin !== null && priceMin.value === null)
    e.priceMin = 'Không xoá được giá đã có — hãy nhập giá mới.'
  if (props.place.priceMax !== null && priceMax.value === null)
    e.priceMax = 'Không xoá được giá đã có — hãy nhập giá mới.'
  if (
    !e.priceMax &&
    priceMin.value !== null &&
    priceMax.value !== null &&
    priceMin.value > priceMax.value
  )
    e.priceMax = 'Giá thấp nhất phải nhỏ hơn hoặc bằng giá cao nhất.'
  const ph = phone.value.trim()
  if (ph && !PHONE.test(ph)) e.phone = 'Số điện thoại không hợp lệ.'
  const web = website.value.trim()
  if (web && !WEBSITE.test(web)) e.website = 'Địa chỉ web phải bắt đầu bằng http:// hoặc https://.'
  errors.value = e
  return Object.keys(e).length === 0
}

async function save(): Promise<void> {
  formError.value = null
  if (!validate()) return
  const update: PlaceUpdate = {
    description: description.value.trim(),
    priceMin: priceMin.value ?? undefined,
    priceMax: priceMax.value ?? undefined,
    phone: phone.value.trim(),
    website: website.value.trim(),
    openingHours: toOpeningHours(week.value),
    amenityIds: amenityIds.value,
    version: props.place.version,
  }
  saving.value = true
  try {
    const saved = await ownerApi.update(props.place.id, update)
    notify.success('Đã cập nhật thông tin — hiển thị ngay trên trang địa điểm.')
    emit('saved', saved)
  } catch (cause) {
    const problem = toProblem(cause)
    if (problem.status === 409) {
      formError.value =
        'Thông tin vừa được cập nhật ở nơi khác. Tải lại trang để xem bản mới rồi sửa tiếp.'
      return
    }
    const fields: Record<string, string> = {}
    for (const [field, message] of Object.entries(problem.fieldErrors)) {
      if (field.startsWith('openingHours')) formError.value = `Giờ mở cửa: ${message}`
      else if (field.startsWith('amenityIds')) formError.value = `Tiện ích: ${message}`
      else fields[field] = message
    }
    errors.value = fields
    if (!formError.value && !Object.keys(fields).length) formError.value = problemMessage(problem)
  } finally {
    saving.value = false
  }
}

// ─── Ảnh: gọi API ngay ───────────────────────────────────────────────────────
const photos = computed(() => props.place.photos)
const newPhotos = ref<DraftPhoto[]>([])
const uploading = ref(false)
const photoError = ref<string | null>(null)

async function upload(): Promise<void> {
  if (!newPhotos.value.length) return
  uploading.value = true
  photoError.value = null
  try {
    await ownerApi.addPhotos(
      props.place.id,
      newPhotos.value.map((d) => ({ blob: d.file, fileName: d.file.name })),
    )
    newPhotos.value.forEach(releaseDraft)
    newPhotos.value = []
    notify.success('Đã tải ảnh lên — ảnh hiển thị sau khi xử lý xong.')
    emit('saved', props.place)
  } catch (cause) {
    const problem = toProblem(cause)
    const photo = Object.entries(problem.fieldErrors).find(([f]) => f.startsWith('photos'))
    photoError.value = photo
      ? `Ảnh thứ ${Number(/\d+/.exec(photo[0])?.[0] ?? 0) + 1}: ${photo[1]}`
      : problemMessage(problem)
  } finally {
    uploading.value = false
  }
}

/** Ảnh đã đăng bị xoá hẳn khỏi kho — xác nhận trước (như xoá bình luận). */
const confirmingPhoto = ref<number | null>(null)

async function removePhoto(): Promise<void> {
  const photoId = confirmingPhoto.value
  if (photoId === null) return
  photoError.value = null
  try {
    await ownerApi.deletePhoto(props.place.id, photoId)
    confirmingPhoto.value = null
    emit('saved', props.place)
  } catch (cause) {
    photoError.value = problemMessage(toProblem(cause))
  }
}

onBeforeUnmount(() => newPhotos.value.forEach(releaseDraft))
</script>

<template>
  <div>
    <form novalidate @submit.prevent="save">
      <p class="m-0 mb-2 max-w-[640px] text-caption text-ink-muted">
        Tên, vị trí, địa chỉ và danh mục không sửa ở đây — nếu sai, hãy dùng “Báo cáo → Thông tin
        sai” trên trang địa điểm để kiểm duyệt viên xử lý.
      </p>

      <!-- Giới thiệu + địa chỉ -->
      <section
        class="grid gap-6 border-t border-line py-8 md:grid-cols-[200px_1fr]"
        :aria-labelledby="`${uid}-about`"
      >
        <h2 :id="`${uid}-about`" class="m-0 text-title">Giới thiệu</h2>
        <div class="grid max-w-[640px] gap-5">
          <div class="ls-field">
            <label :for="`${uid}-description`">Mô tả</label>
            <textarea
              :id="`${uid}-description`"
              v-model="description"
              class="ls-textarea"
              rows="5"
              maxlength="5000"
            ></textarea>
          </div>
          <div class="ls-field">
            <label :for="`${uid}-address`">Địa chỉ</label>
            <input
              :id="`${uid}-address`"
              :value="place.address"
              class="ls-input"
              disabled
              :aria-describedby="`${uid}-address-hint`"
            />
            <span :id="`${uid}-address-hint`" class="ls-field__hint">
              Địa chỉ gắn với vị trí đã được duyệt nên không sửa được. Nếu đây không phải địa chỉ
              quán của bạn, hãy đề xuất đúng địa điểm rồi gửi yêu cầu xác nhận sở hữu.
            </span>
          </div>
        </div>
      </section>

      <!-- Chi tiết -->
      <section
        class="grid gap-6 border-t border-line py-8 md:grid-cols-[200px_1fr]"
        :aria-labelledby="`${uid}-details`"
      >
        <h2 :id="`${uid}-details`" class="m-0 text-title">Chi tiết</h2>
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
                :value="priceMin ?? ''"
                :aria-invalid="errors.priceMin ? 'true' : undefined"
                @input="priceMin = parsePrice($event)"
              />
              <span v-if="errors.priceMin" class="ls-field__error">✕ {{ errors.priceMin }}</span>
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
                :value="priceMax ?? ''"
                :aria-invalid="errors.priceMax ? 'true' : undefined"
                @input="priceMax = parsePrice($event)"
              />
              <span v-if="errors.priceMax" class="ls-field__error">✕ {{ errors.priceMax }}</span>
            </div>
            <div class="ls-field">
              <label :for="`${uid}-phone`">Số điện thoại</label>
              <input
                :id="`${uid}-phone`"
                v-model="phone"
                class="ls-input"
                type="tel"
                maxlength="20"
                autocomplete="tel"
                :aria-invalid="errors.phone ? 'true' : undefined"
              />
              <span v-if="errors.phone" class="ls-field__error">✕ {{ errors.phone }}</span>
            </div>
            <div class="ls-field">
              <label :for="`${uid}-website`">Trang web</label>
              <input
                :id="`${uid}-website`"
                v-model="website"
                class="ls-input"
                maxlength="300"
                placeholder="https://"
                :aria-invalid="errors.website ? 'true' : undefined"
              />
              <span v-if="errors.website" class="ls-field__error">✕ {{ errors.website }}</span>
            </div>
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

      <div class="flex flex-col gap-3 border-t border-line pt-6">
        <p v-if="formError" role="alert" class="ls-field__error m-0">✕ {{ formError }}</p>
        <div>
          <LsButton type="submit" :loading="saving">Lưu thông tin</LsButton>
        </div>
      </div>
    </form>

    <!-- Ảnh (gọi API ngay) -->
    <section
      class="mt-10 grid gap-6 border-t border-line py-8 md:grid-cols-[200px_1fr]"
      :aria-labelledby="`${uid}-photos`"
    >
      <div>
        <h2 :id="`${uid}-photos`" class="m-0 text-title">Ảnh</h2>
        <p class="mt-1 text-caption text-ink-muted">Thêm / bỏ ảnh có hiệu lực ngay.</p>
      </div>
      <div class="flex max-w-[640px] flex-col gap-3">
        <PhotoPicker
          v-model="newPhotos"
          :existing="photos"
          :max="PHOTOS_MAX"
          :label="`Ảnh địa điểm (tối đa ${PHOTOS_MAX})`"
          :disabled="uploading"
          @remove-existing="confirmingPhoto = $event"
        />
        <p
          v-if="confirmingPhoto !== null"
          role="alert"
          class="m-0 flex flex-wrap items-center gap-3"
        >
          Xoá ảnh này khỏi địa điểm? Không hoàn tác được.
          <LsButton size="sm" @click="removePhoto">Xoá ảnh</LsButton>
          <LsButton size="sm" variant="ghost" @click="confirmingPhoto = null">Giữ lại</LsButton>
        </p>
        <p v-if="photoError" role="alert" class="ls-field__error m-0">✕ {{ photoError }}</p>
        <div v-if="newPhotos.length">
          <LsButton :loading="uploading" @click="upload"
            >Tải lên {{ newPhotos.length }} ảnh</LsButton
          >
        </div>
      </div>
    </section>
  </div>
</template>
