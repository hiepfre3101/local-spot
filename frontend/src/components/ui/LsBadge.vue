<script setup lang="ts">
/**
 * Nhãn trạng thái quản trị (Badge README). Nội dung luôn gồm ký hiệu + chữ (● Chờ duyệt, ✓ Hoạt động, ✕ Từ chối) —
 * không bao giờ chỉ dựa vào màu.
 */
defineProps<{ tone: 'success' | 'warning' | 'danger' | 'info' | 'neutral' }>()
</script>

<template>
  <span :class="['ls-badge', `ls-badge--${tone}`]"><slot /></span>
</template>
