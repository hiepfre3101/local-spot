<script setup lang="ts">
import { computed, useId } from 'vue'
import { useField } from 'vee-validate'

import { RATING_LABELS } from '@/validation/review'

/**
 * Nhập điểm (Rating README — UC12): 5 radio hình tròn, chỉ điểm nguyên 1–5, kèm chữ mô tả mức đã chọn. Radio thật nên
 * dùng được phím mũi tên và trình đọc màn hình; mỗi hình tròn có nhãn ẩn "4 điểm — Rất tốt". Thứ tự DOM 5 → 1 (CSS
 * `row-reverse` hiển thị lại 1 → 5) để tô các mức thấp hơn bằng CSS thuần.
 */
const props = defineProps<{ name: string; label: string }>()

const { value, errorMessage } = useField<number | undefined>(() => props.name)
const id = useId()
const levels = [5, 4, 3, 2, 1]
const caption = computed(() =>
  value.value ? `${value.value} · ${RATING_LABELS[value.value]}` : 'Chưa chọn',
)
</script>

<template>
  <div class="ls-field">
    <span :id="`${id}-label`" class="ls-field__label">{{ label }}</span>
    <div class="flex flex-wrap items-center gap-4">
      <fieldset
        class="ls-rate"
        :aria-labelledby="`${id}-label`"
        :aria-invalid="errorMessage ? 'true' : undefined"
        :aria-describedby="errorMessage ? `${id}-error` : undefined"
      >
        <template v-for="level in levels" :key="level">
          <input
            :id="`${id}-${level}`"
            v-model="value"
            type="radio"
            :name="`${id}-rating`"
            :value="level"
          />
          <label :for="`${id}-${level}`"
            ><span class="sr-only">{{ level }} điểm — {{ RATING_LABELS[level] }}</span></label
          >
        </template>
      </fieldset>
      <span class="font-semibold" aria-live="polite">{{ caption }}</span>
    </div>
    <span v-if="errorMessage" :id="`${id}-error`" class="ls-field__error"
      >✕ {{ errorMessage }}</span
    >
  </div>
</template>
