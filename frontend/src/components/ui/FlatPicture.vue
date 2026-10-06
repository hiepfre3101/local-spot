<template>
  <!-- Ảnh giữ chỗ dạng minh hoạ phẳng (README design system: chưa có ảnh thật, không phủ gradient) -->
  <div class="ls-pic">
    <svg viewBox="0 0 400 300" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
      <rect class="sky" width="400" height="300" />
      <circle class="sun" cx="320" cy="80" r="34" />
      <path class="ink" d="M0 190 L70 120 L130 170 L190 110 L260 190Z" />
      <rect class="sea2" y="190" width="400" height="50" />
      <rect class="sand" y="238" width="400" height="62" />
    </svg>
    <slot />
  </div>
</template>
