<script setup lang="ts">
import { computed } from 'vue'

import { initials } from '@/utils/format'

/** Ảnh đại diện; chưa có ảnh (module ảnh làm sau) thì hiện chữ cái đầu trên nền olive. */
const props = withDefaults(
  defineProps<{ name: string; src?: string | null; size?: 'sm' | 'md' }>(),
  { src: null, size: 'md' },
)

const letters = computed(() => initials(props.name))
</script>

<template>
  <img
    v-if="src"
    :src="src"
    alt=""
    :class="['ls-avatar object-cover', size === 'sm' && 'ls-avatar--sm']"
  />
  <span v-else :class="['ls-avatar', size === 'sm' && 'ls-avatar--sm']" aria-hidden="true">
    {{ letters }}
  </span>
</template>
