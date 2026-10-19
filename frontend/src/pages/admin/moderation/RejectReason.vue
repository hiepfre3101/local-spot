<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

/**
 * Lý do từ chối (AdminForm: "Lý do nếu từ chối"): chọn lý do có sẵn + ghi chú; "Lý do khác" bắt buộc ghi chú. Ghép
 * thành một chuỗi `reason` (API nhận một trường, tối đa 500 ký tự) — người gửi đọc nguyên câu này trong thông báo.
 */
const props = defineProps<{ presets: readonly string[]; error?: string | null; hint: string }>()
const reason = defineModel<string>({ required: true })

const OTHER = 'Lý do khác'
const MAX = 500
const id = useId()
const preset = ref('')
const note = ref('')

const composed = computed(() => {
  const text = note.value.trim()
  if (!preset.value) return ''
  if (preset.value === OTHER) return text
  return text ? `${preset.value} — ${text}` : preset.value
})
watch(composed, (value) => (reason.value = value.slice(0, MAX)))
const options = computed(() => [...props.presets, OTHER])
</script>

<template>
  <section class="ls-section fs">
    <div class="fs__intro">
      <h2>Lý do nếu từ chối</h2>
      <p>{{ hint }}</p>
    </div>
    <div class="fs__grid">
      <div class="ls-field">
        <label :for="`${id}-preset`">Lý do</label>
        <span class="ls-selectwrap">
          <select
            :id="`${id}-preset`"
            v-model="preset"
            class="ls-select"
            :aria-invalid="error ? true : undefined"
            :aria-describedby="error ? `${id}-error` : undefined"
          >
            <option value="">Chọn lý do…</option>
            <option v-for="option in options" :key="option" :value="option">{{ option }}</option>
          </select>
        </span>
        <span v-if="error" :id="`${id}-error`" class="ls-field__error">✕ {{ error }}</span>
      </div>
      <div class="ls-field col-span-full">
        <label :for="`${id}-note`">
          Ghi chú{{ preset === OTHER ? ' *' : ' (không bắt buộc)' }}
        </label>
        <textarea :id="`${id}-note`" v-model="note" class="ls-textarea" :maxlength="MAX"></textarea>
      </div>
    </div>
  </section>
</template>
