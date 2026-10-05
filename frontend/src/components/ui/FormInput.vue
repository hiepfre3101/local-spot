<script setup lang="ts">
import { computed, useId } from 'vue'
import { useField } from 'vee-validate'

/**
 * Ô nhập gắn với form vee-validate (FormField README): nhãn phía trên, gợi ý `ls-field__hint`, lỗi bắt đầu bằng ✕ +
 * viền 2px `danger` + `aria-invalid`. Kiểm khi rời ô và khi gửi form, không báo lỗi trong lúc đang gõ dở.
 * Kiểu quản trị hay công khai do lớp `ls-pub` của khung bao quyết định.
 */
const props = withDefaults(
  defineProps<{
    name: string
    label: string
    type?: 'text' | 'email' | 'password'
    autocomplete?: string
    hint?: string
    placeholder?: string
    disabled?: boolean
  }>(),
  { type: 'text', autocomplete: 'off', hint: undefined, placeholder: undefined },
)

const { value, errorMessage, handleBlur } = useField<string>(() => props.name, undefined, {
  validateOnValueUpdate: false,
  initialValue: '',
})

const id = useId()
const describedBy = computed(() =>
  errorMessage.value ? `${id}-error` : props.hint ? `${id}-hint` : undefined,
)
</script>

<template>
  <div class="ls-field">
    <label :for="id">{{ label }}</label>
    <input
      :id="id"
      v-model="value"
      class="ls-input"
      :type="type"
      :name="name"
      :autocomplete="autocomplete"
      :placeholder="placeholder"
      :disabled="disabled"
      :aria-invalid="errorMessage ? 'true' : undefined"
      :aria-describedby="describedBy"
      @blur="handleBlur($event, true)"
    />
    <span v-if="errorMessage" :id="`${id}-error`" class="ls-field__error"
      >✕ {{ errorMessage }}</span
    >
    <span v-else-if="hint" :id="`${id}-hint`" class="ls-field__hint">{{ hint }}</span>
  </div>
</template>
