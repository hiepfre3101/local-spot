<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, onBeforeUnmount, ref, useId, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { ownerApi } from '@/api/owner'
import { placesApi } from '@/api/places'
import { problemMessage, toProblem } from '@/api/problem'
import PhotoPicker from '@/components/review/PhotoPicker.vue'
import { releaseDraft, type DraftPhoto } from '@/components/review/draftPhoto'
import LsButton from '@/components/ui/LsButton.vue'
import type { PlaceDetail, PlaceSummary } from '@/types/api'

/**
 * Yêu cầu xác nhận sở hữu (UC23, FR-31). Chọn địa điểm: có sẵn từ nút trên trang địa điểm (`initialSlug`), hoặc tìm theo
 * tên. Địa điểm đã có chủ / chưa duyệt không gửi được (`claimable` của chi tiết). Minh chứng 1–5 ảnh JPEG / PNG (giấy
 * phép kinh doanh, hoá đơn…) — chỉ kiểm duyệt viên xem; điện thoại để gọi xác minh. Gửi xong phát `submitted`.
 *
 * Bản mẫu có "Vai trò của bạn" và nhận PDF ≤ 10 MB — API không có trường vai trò và chỉ nhận ảnh ≤ 5 MB (chốt
 * 2026-10-16), nên form theo API.
 */
const props = defineProps<{ initialSlug?: string | null }>()
const emit = defineEmits<{ submitted: [place: PlaceDetail] }>()

const EVIDENCE_MAX = 5
const PHONE = /^[0-9+()\s.-]{6,20}$/
const NOTE_MAX = 1000
const SEARCH_DEBOUNCE_MS = 400

const uid = useId()

// ─── Chọn địa điểm ──────────────────────────────────────────────────────────
const selectedSlug = ref<string | null>(props.initialSlug ?? null)
watch(
  () => props.initialSlug,
  (slug) => {
    if (slug) selectedSlug.value = slug
  },
)
const selected = useQuery({
  queryKey: computed(() => ['place', selectedSlug.value]),
  queryFn: () => placesApi.detail(selectedSlug.value!),
  enabled: computed(() => !!selectedSlug.value),
})
const place = computed(() => (selectedSlug.value ? (selected.data.value ?? null) : null))

const term = ref('')
const query = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(term, (value) => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => (query.value = value.trim()), SEARCH_DEBOUNCE_MS)
})
const results = useQuery({
  queryKey: computed(() => ['claim-search', query.value]),
  queryFn: () => placesApi.search(query.value, {}, 'RELEVANCE', null, 8),
  enabled: computed(() => query.value.length >= 2 && !selectedSlug.value),
  staleTime: 60_000,
})

function choose(p: PlaceSummary): void {
  selectedSlug.value = p.slug
  term.value = ''
  query.value = ''
}

function changePlace(): void {
  selectedSlug.value = null
}

// ─── Thông tin + minh chứng ─────────────────────────────────────────────────
const phone = ref('')
const note = ref('')
const evidence = ref<DraftPhoto[]>([])
const errors = ref<{ phone?: string; evidence?: string; form?: string }>({})
const sending = ref(false)

onBeforeUnmount(() => {
  clearTimeout(searchTimer)
  evidence.value.forEach(releaseDraft)
})

async function submit(): Promise<void> {
  const p = place.value
  if (!p || !p.claimable) return
  const contactPhone = phone.value.trim()
  errors.value = {
    phone: PHONE.test(contactPhone) ? undefined : 'Số điện thoại không hợp lệ (6–20 ký tự).',
    evidence: evidence.value.length ? undefined : 'Cần ít nhất một ảnh minh chứng.',
  }
  if (errors.value.phone || errors.value.evidence) return
  sending.value = true
  try {
    await ownerApi.claim(
      p.id,
      { contactPhone, note: note.value.trim() || undefined },
      evidence.value.map((d) => ({ blob: d.file, fileName: d.file.name })),
    )
    evidence.value.forEach(releaseDraft)
    evidence.value = []
    phone.value = ''
    note.value = ''
    emit('submitted', p)
  } catch (cause) {
    const problem = toProblem(cause)
    const fieldMessage = Object.entries(problem.fieldErrors)[0]
    errors.value = {
      phone: problem.fieldErrors['claim.contactPhone'] ?? problem.fieldErrors.contactPhone,
      form:
        problem.code === 'CLAIM_ALREADY_PENDING'
          ? 'Bạn đã có một yêu cầu đang chờ duyệt cho địa điểm này.'
          : problem.code === 'PLACE_ALREADY_OWNED'
            ? 'Địa điểm này đã có chủ được xác nhận.'
            : problem.code === 'EMAIL_NOT_VERIFIED'
              ? 'Cần xác thực email trước khi gửi yêu cầu.'
              : fieldMessage?.[0].startsWith('evidence')
                ? `Ảnh minh chứng: ${fieldMessage[1]}`
                : problemMessage(problem),
    }
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <form class="grid max-w-[640px] gap-5" novalidate @submit.prevent="submit">
    <!-- Địa điểm -->
    <div v-if="place" class="ls-field">
      <span class="ls-field__label">Địa điểm</span>
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line pb-3">
        <div class="min-w-0">
          <RouterLink
            class="font-semibold text-ink"
            :to="{ name: 'place', params: { slug: place.slug } }"
            >{{ place.name }}</RouterLink
          >
          <p class="m-0 text-caption text-ink-muted">{{ place.address }}, {{ place.city }}</p>
        </div>
        <button type="button" class="ls-act" @click="changePlace">Chọn địa điểm khác</button>
      </div>
      <p v-if="!place.claimable" role="alert" class="ls-field__error m-0">
        ✕
        {{
          place.owner
            ? 'Địa điểm này đã có chủ được xác nhận.'
            : place.status !== 'APPROVED'
              ? 'Địa điểm chưa được duyệt — chưa nhận yêu cầu sở hữu.'
              : 'Bạn đã có một yêu cầu đang chờ duyệt cho địa điểm này.'
        }}
      </p>
    </div>
    <p v-else-if="selectedSlug && selected.isError.value" role="alert" class="m-0">
      ✕ Không tìm thấy địa điểm.
      <button type="button" class="ls-act" @click="changePlace">Tìm địa điểm khác</button>
    </p>
    <div v-else-if="!selectedSlug" class="ls-field">
      <label :for="`${uid}-search`">Địa điểm của bạn *</label>
      <input
        :id="`${uid}-search`"
        v-model="term"
        class="ls-input"
        type="search"
        placeholder="Gõ tên quán…"
        autocomplete="off"
      />
      <span class="ls-field__hint"
        >Chưa có trên LocalSpot?
        <RouterLink class="ls-link" :to="{ name: 'propose' }">Đề xuất địa điểm</RouterLink> trước,
        được duyệt rồi mới gửi yêu cầu sở hữu.</span
      >
      <ul v-if="results.data.value?.items.length" class="m-0 list-none p-0" aria-label="Kết quả">
        <li v-for="p in results.data.value.items" :key="p.id" class="border-b border-line">
          <button
            type="button"
            class="w-full cursor-pointer border-0 bg-transparent px-0 py-3 text-left text-ink"
            @click="choose(p)"
          >
            <b>{{ p.name }}</b>
            <span class="block text-caption text-ink-muted">{{ p.address }}</span>
          </button>
        </li>
      </ul>
      <p
        v-else-if="query.length >= 2 && !results.isFetching.value"
        class="m-0 text-caption text-ink-muted"
      >
        Không tìm thấy địa điểm nào khớp.
      </p>
    </div>

    <template v-if="place?.claimable">
      <div class="ls-field">
        <label :for="`${uid}-phone`">Số điện thoại liên hệ *</label>
        <input
          :id="`${uid}-phone`"
          v-model="phone"
          class="ls-input"
          type="tel"
          autocomplete="tel"
          maxlength="20"
          :aria-invalid="errors.phone ? 'true' : undefined"
          :aria-describedby="`${uid}-phone-hint`"
        />
        <span :id="`${uid}-phone-hint`" class="ls-field__hint"
          >Kiểm duyệt viên có thể gọi để xác minh. Không hiển thị công khai.</span
        >
        <span v-if="errors.phone" class="ls-field__error">✕ {{ errors.phone }}</span>
      </div>
      <div>
        <PhotoPicker
          v-model="evidence"
          :max="EVIDENCE_MAX"
          :label="`Ảnh minh chứng * (1–${EVIDENCE_MAX} ảnh)`"
          :disabled="sending"
        />
        <p class="m-0 mt-1 text-caption text-ink-muted">
          Giấy phép kinh doanh, hoá đơn điện nước đứng tên quán… Chỉ kiểm duyệt viên xem được.
        </p>
        <p v-if="errors.evidence" class="ls-field__error m-0">✕ {{ errors.evidence }}</p>
      </div>
      <div class="ls-field">
        <label :for="`${uid}-note`">Ghi chú (tuỳ chọn)</label>
        <textarea
          :id="`${uid}-note`"
          v-model="note"
          class="ls-textarea"
          rows="3"
          :maxlength="NOTE_MAX"
        ></textarea>
      </div>
    </template>

    <p v-if="errors.form" role="alert" class="ls-field__error m-0">✕ {{ errors.form }}</p>
    <div v-if="place?.claimable">
      <LsButton type="submit" :loading="sending">Gửi yêu cầu</LsButton>
    </div>
  </form>
</template>
