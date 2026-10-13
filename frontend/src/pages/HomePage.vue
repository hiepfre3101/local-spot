<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { catalogApi } from '@/api/catalog'
import HomeReviewCard from '@/components/place/HomeReviewCard.vue'
import SpotCard from '@/components/place/SpotCard.vue'
import FlatPicture from '@/components/ui/FlatPicture.vue'
import LsButton from '@/components/ui/LsButton.vue'
import { catalogKeys } from '@/composables/catalogQueries'
import { useAuthStore } from '@/stores/auth'
import { useFilterStore } from '@/stores/filter'
import { MAX_QUERY_LENGTH } from '@/utils/searchQuery'

/**
 * Trang chủ (UC07, HomePage README, FR-08): hero + tìm kiếm có lọc danh mục (UC08) + "Tìm quanh tôi" (UC09) → tuyên ngôn
 * → hàng thẻ (ReviewCard, SpotCard, ô Bản đồ) → danh mục, nổi bật, mới duyệt → băng `coral` → chức năng thành viên →
 * chủ địa điểm → hỏi đáp → CTA. Dữ liệu một request `GET /home` (backend cache). Liên kết tới module chưa làm chỉ hiện
 * khi đã có route — không có link chết.
 */
const auth = useAuthStore()
const filter = useFilterStore()
const router = useRouter()

const home = useQuery({ queryKey: catalogKeys.home, queryFn: catalogApi.home, staleTime: 60_000 })

const q = ref('')
const category = ref('')

const featured = computed(() => home.data.value?.featured ?? [])
const latest = computed(() => home.data.value?.latest ?? [])
const categories = computed(() => home.data.value?.categories ?? [])
/** Hai thẻ đầu đi cùng ReviewCard ở hàng tuyên ngôn; phần còn lại vào lưới "Đang được yêu thích". */
const rowSpots = computed(() => featured.value.slice(0, 2))
const moreFeatured = computed(() => featured.value.slice(2, 6))

const canPropose = computed(() => router.hasRoute('propose'))
const canClaim = computed(() => router.hasRoute('owner'))

function submitSearch(): void {
  void filter.open({ q: q.value.trim(), category: category.value || null })
}

function nearMe(): void {
  void filter.open({ near: 'me' })
}
</script>

<template>
  <div class="-mt-8">
    <!-- Hero -->
    <section
      class="relative flex min-h-[520px] flex-col justify-between overflow-hidden rounded-lg p-6 md:h-[560px] md:p-10"
      aria-labelledby="home-title"
    >
      <FlatPicture class="absolute inset-0" />
      <div class="relative">
        <h1 id="home-title" class="m-0">
          <span class="sr-only">LocalSpot</span>
          <svg
            class="block h-auto w-full overflow-visible"
            viewBox="0 0 1040 160"
            aria-hidden="true"
          >
            <text
              x="0"
              y="150"
              textLength="1040"
              lengthAdjust="spacingAndGlyphs"
              class="fill-sun"
              font-size="196"
              font-weight="800"
            >
              LOCALSPOT
            </text>
          </svg>
        </h1>
        <p class="hero-lead mt-4 ml-1.5 max-w-[380px] text-on-olive">
          Đánh giá thật từ người đã đến — quán ăn, điểm check-in và góc phố đáng ghé ở khắp Việt
          Nam.
        </p>
      </div>

      <div class="relative flex flex-col gap-3 lg:flex-row lg:items-center lg:gap-4">
        <form
          role="search"
          class="flex w-full flex-col gap-1.5 rounded-lg bg-surface-raised p-1.5 sm:flex-row sm:rounded-pill lg:w-[690px]"
          @submit.prevent="submitSearch"
        >
          <input
            v-model="q"
            class="ls-input hero-control min-w-0 flex-1"
            type="search"
            :maxlength="MAX_QUERY_LENGTH"
            placeholder="Tìm quán, điểm check-in, khu vực, #thẻ…"
            aria-label="Tìm kiếm địa điểm"
          />
          <span class="ls-selectwrap hero-select sm:w-44 sm:flex-none">
            <select
              v-model="category"
              class="ls-select hero-control hero-control--sunk"
              aria-label="Danh mục"
            >
              <option value="">Mọi danh mục</option>
              <optgroup v-for="root in categories" :key="root.id" :label="root.name">
                <option :value="root.slug">Tất cả {{ root.name.toLocaleLowerCase('vi') }}</option>
                <option v-for="child in root.children" :key="child.id" :value="child.slug">
                  {{ child.name }}
                </option>
              </optgroup>
            </select>
          </span>
          <LsButton variant="accent" type="submit" class="h-12!">Tìm kiếm</LsButton>
        </form>
        <LsButton variant="sun" size="sm" class="self-start lg:self-center" @click="nearMe">
          <svg
            class="size-4"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            aria-hidden="true"
          >
            <circle cx="12" cy="12" r="3" />
            <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
          </svg>
          Tìm quanh tôi
        </LsButton>
      </div>
    </section>

    <!-- Tuyên ngôn -->
    <section class="py-16 md:ml-[320px] md:max-w-[760px] md:pt-24 md:pb-12">
      <span class="ls-kick mb-4 block">01 — Về LocalSpot</span>
      <h2 class="disp">MỖI GÓC PHỐ ĐỀU ĐÁNG ĐƯỢC KỂ.<br />CHIA SẺ NGAY!</h2>
    </section>

    <!-- Hàng thẻ: ReviewCard + 2 SpotCard + ô Bản đồ -->
    <section
      aria-label="Nổi bật hôm nay"
      class="grid gap-4 md:grid-cols-2 lg:grid-cols-[1.1fr_1fr_1fr_1fr]"
    >
      <template v-if="home.isPending.value">
        <span v-for="i in 4" :key="i" class="ls-skel h-[320px]" aria-hidden="true"></span>
      </template>
      <template v-else>
        <HomeReviewCard
          v-if="home.data.value?.featuredReview"
          :review="home.data.value.featuredReview"
        />
        <SpotCard v-for="place in rowSpots" :key="place.id" :place="place" featured />
        <div class="ls-feature">
          <h3>Gần bạn<br />lúc này</h3>
          <p>Xem các địa điểm quanh vị trí của bạn trên bản đồ, lọc theo danh mục.</p>
          <LsButton variant="sun" size="sm" class="self-start" @click="nearMe">Mở bản đồ</LsButton>
        </div>
      </template>
    </section>
    <p v-if="home.isError.value" class="mt-6 text-ink-muted" role="alert">
      ✕ Chưa tải được địa điểm nổi bật.
      <button
        class="ls-link cursor-pointer border-0 bg-transparent p-0"
        type="button"
        @click="home.refetch()"
      >
        Thử lại
      </button>
    </p>

    <!-- Danh mục -->
    <section v-if="categories.length" class="pt-16 md:pt-24" aria-labelledby="home-categories">
      <span class="ls-kick mb-4 block">02 — Khám phá theo danh mục</span>
      <h2 id="home-categories" class="disp mb-8">ĐƯỢC RỒI ĐI THÔI!</h2>
      <ul class="m-0 flex list-none flex-wrap gap-2 p-0">
        <li v-for="root in categories" :key="root.id">
          <RouterLink class="ls-fchip" :to="{ name: 'search', query: { category: root.slug } }">
            {{ root.name }}
          </RouterLink>
        </li>
        <template v-for="root in categories" :key="`c-${root.id}`">
          <li v-for="child in root.children" :key="child.id">
            <RouterLink class="ls-fchip" :to="{ name: 'search', query: { category: child.slug } }">
              {{ child.name }}
            </RouterLink>
          </li>
        </template>
      </ul>
    </section>

    <!-- Nổi bật (điểm Bayesian) -->
    <section v-if="moreFeatured.length" class="pt-16 md:pt-24" aria-labelledby="home-featured">
      <div class="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          <span class="ls-kick mb-4 block">03 — Đang được yêu thích</span>
          <h2 id="home-featured" class="disp">NHỮNG ĐIỂM ĐẾN THÚ VỊ</h2>
        </div>
        <RouterLink class="ls-link" :to="{ name: 'search', query: { sort: 'SCORE' } }"
          >Xem tất cả →</RouterLink
        >
      </div>
      <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        <SpotCard v-for="place in moreFeatured" :key="place.id" :place="place" />
      </div>
      <p class="mt-4 text-caption text-ink-muted">
        Xếp theo điểm có trọng số: nơi mới có vài đánh giá không vượt được nơi nhiều người đã chấm.
      </p>
    </section>

    <!-- Mới duyệt -->
    <section v-if="latest.length" class="pt-16 md:pt-24" aria-labelledby="home-latest">
      <div class="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          <span class="ls-kick mb-4 block">04 — Mới trên LocalSpot</span>
          <h2 id="home-latest" class="disp">VỪA ĐƯỢC CỘNG ĐỒNG ĐỀ XUẤT.</h2>
        </div>
        <RouterLink class="ls-link" :to="{ name: 'search', query: { sort: 'NEWEST' } }"
          >Xem tất cả →</RouterLink
        >
      </div>
      <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        <SpotCard v-for="place in latest.slice(0, 8)" :key="place.id" :place="place" />
      </div>
    </section>

    <!-- Băng coral -->
    <section
      class="mt-16 grid min-h-[360px] gap-4 rounded-lg bg-coral p-4 text-on-accent md:mt-24 md:grid-cols-[1.4fr_1fr]"
    >
      <div class="flex flex-col items-start justify-between gap-8 p-2 md:p-6">
        <h2 class="disp band-title">ĐI CÙNG CỘNG ĐỒNG, TÌM ĐÚNG CHỖ,<br />ĐÚNG TRẢI NGHIỆM.</h2>
        <div>
          <template v-if="auth.isAuthenticated">
            <p class="band-lead mb-4 max-w-[420px]">
              Tìm nơi bạn vừa ghé và kể lại trải nghiệm thật — đánh giá có điểm, ảnh và check-in.
            </p>
            <LsButton :to="{ name: 'search' }">Tìm nơi để đánh giá</LsButton>
          </template>
          <template v-else>
            <p class="band-lead mb-4 max-w-[420px]">
              Đăng ký để viết đánh giá, check-in, lưu bộ sưu tập và theo dõi những người viết bạn
              tin.
            </p>
            <LsButton :to="{ name: 'register' }">Đăng ký miễn phí</LsButton>
          </template>
        </div>
      </div>
      <FlatPicture class="min-h-[220px] rounded-md" />
    </section>

    <!-- Chức năng thành viên -->
    <section class="grid gap-12 pt-16 md:grid-cols-2 md:pt-28">
      <div>
        <span class="ls-kick mb-4 block">05 — Thành viên làm được gì</span>
        <h2 class="disp">KHÔNG CHỈ LÀ TRANG ĐÁNH GIÁ — MÀ LÀ MỘT CỘNG ĐỒNG.</h2>
        <FlatPicture class="mt-8 h-[220px] rounded-md" />
      </div>
      <div class="grid content-end gap-3 sm:grid-cols-2">
        <div class="ls-feature">
          <h3>Đánh giá<br />&amp; ảnh</h3>
          <p>Chấm điểm, viết cảm nhận và đính kèm ảnh cho từng nơi bạn đã đến.</p>
        </div>
        <div class="ls-feature">
          <h3>Check-in<br />tại chỗ</h3>
          <p>Đánh dấu bạn đã ở đó — đánh giá có check-in được hiển thị rõ.</p>
        </div>
        <div class="ls-feature">
          <h3>Bộ sưu<br />tập</h3>
          <p>Gom địa điểm theo chuyến đi, theo món, theo nhóm bạn.</p>
        </div>
        <div class="ls-feature">
          <h3>Xếp hạng<br />&amp; huy hiệu</h3>
          <p>Đóng góp đều đặn, nhận huy hiệu và lên bảng xếp hạng cộng đồng.</p>
        </div>
      </div>
    </section>

    <!-- Chủ địa điểm -->
    <section class="grid items-end gap-12 pt-16 md:grid-cols-[1fr_1.2fr] md:pt-28">
      <div>
        <span class="ls-kick mb-4 block">06 — Dành cho chủ địa điểm</span>
        <h2 class="disp owner-title">BẠN LÀ CHỦ QUÁN? NẮM BẮT NGAY THÔNG TIN.</h2>
        <LsButton v-if="canClaim" class="mt-6" :to="{ name: 'owner' }"
          >Yêu cầu xác nhận sở hữu</LsButton
        >
      </div>
      <ol class="steps">
        <li>
          <b>Xác nhận sở hữu</b><span>Gửi giấy tờ, kiểm duyệt viên xác nhận trang là của bạn.</span>
        </li>
        <li>
          <b>Cập nhật thông tin</b><span>Giờ mở cửa, liên hệ, ảnh — luôn đúng với thực tế.</span>
        </li>
        <li>
          <b>Phản hồi &amp; thống kê</b
          ><span>Trả lời đánh giá công khai, xem lượt xem và điểm theo thời gian.</span>
        </li>
      </ol>
    </section>

    <!-- Hỏi đáp -->
    <section class="pt-16 md:pt-28" aria-labelledby="home-faq">
      <span class="ls-kick mb-4 block">07 — Hỏi đáp</span>
      <h2 id="home-faq" class="disp mb-6">CÓ THẮC MẮC?<br />CHÚNG TÔI TRẢ LỜI.</h2>
      <div class="ls-faq">
        <div>
          <details open>
            <summary>Ai được viết đánh giá?</summary>
            <p>
              Thành viên đã đăng ký và xác thực email. Khách vãng lai vẫn xem, tìm kiếm và dùng bản
              đồ bình thường.
            </p>
          </details>
          <details>
            <summary>Điểm của địa điểm được tính thế nào?</summary>
            <p>
              Điểm có trọng số theo số lượt đánh giá: nơi chỉ có vài đánh giá 5 sao không vượt được
              nơi hàng trăm người đã chấm 4,6. Chỉ đánh giá đã được duyệt mới được tính.
            </p>
          </details>
          <details>
            <summary>Đánh giá của tôi có được đăng ngay không?</summary>
            <p>
              Tài khoản đã có uy tín được đăng ngay; tài khoản mới chờ kiểm duyệt viên xem trước —
              trong lúc chờ chỉ bạn thấy bài của mình.
            </p>
          </details>
        </div>
        <div>
          <details>
            <summary>Làm sao thêm địa điểm mới?</summary>
            <p>Gửi đề xuất kèm ảnh; địa điểm hiển thị sau khi kiểm duyệt viên duyệt.</p>
          </details>
          <details>
            <summary>Chủ địa điểm có xoá được đánh giá xấu?</summary>
            <p>
              Không. Sau khi được xác nhận sở hữu, chủ địa điểm chỉ phản hồi công khai và cập nhật
              thông tin.
            </p>
          </details>
          <details>
            <summary>Tìm không dấu có được không?</summary>
            <p>
              Được. Gõ "pho bat dan" hay "cafe" vẫn ra "Phở Bát Đàn", "Cà phê" — gõ sai một chữ cũng
              không sao.
            </p>
          </details>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="flex flex-col items-center gap-5 py-16 text-center md:py-28">
      <h2 class="disp">CÙNG KHÁM PHÁ<br />MÙA DU LỊCH NÀY</h2>
      <p class="m-0 max-w-[440px] text-ink-muted">
        Biết một nơi đáng ghé mà chưa có trên LocalSpot? Đề xuất để cộng đồng cùng biết.
      </p>
      <div class="flex flex-wrap justify-center gap-2">
        <LsButton variant="accent" :to="{ name: 'search' }">Bắt đầu khám phá</LsButton>
        <LsButton v-if="canPropose" variant="ghost" :to="{ name: 'propose' }"
          >Đề xuất địa điểm</LsButton
        >
      </div>
    </section>
  </div>
</template>

<style scoped>
/* Cỡ chữ theo mẫu HomePage (preview.html) — giá trị thiết kế, không phải cỡ tuỳ ý */
.disp {
  margin: 0;
  font: 800 32px/0.98 var(--font-sans);
  letter-spacing: -0.01em;
  line-height: 4rem;
}
@media (min-width: 768px) {
  .disp {
    font-size: 48px;
  }
  .band-title {
    font-size: 44px;
  }
  .owner-title {
    font-size: 40px;
  }
}
.hero-lead {
  font: 500 16px/24px var(--font-sans);
}
.band-lead {
  margin-top: 0;
  font: 500 15px/22px var(--font-sans);
}
.hero-control {
  height: 48px;
  border-color: transparent;
  border-radius: var(--radius-pill);
  padding: 0 20px;
}
.hero-control--sunk {
  background: var(--surface-sunk);
  padding-left: 18px;
}
.hero-select::after {
  top: 19px;
  right: 18px;
}
.steps {
  margin: 0;
  padding: 0;
  list-style: none;
  counter-reset: s;
  border-top: 1px solid var(--line);
}
.steps li {
  counter-increment: s;
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 4px 16px;
  align-items: baseline;
  padding: 20px 0;
  border-bottom: 1px solid var(--line);
}
@media (min-width: 768px) {
  .steps li {
    grid-template-columns: 48px 200px 1fr;
  }
}
.steps li::before {
  content: '0' counter(s);
  font: 800 20px/1 var(--font-sans);
  color: var(--ink-muted);
}
.steps b {
  font: 600 16px/22px var(--font-sans);
}
.steps span {
  grid-column: 2 / -1;
  font: 400 14px/20px var(--font-sans);
  color: var(--ink-muted);
}
@media (min-width: 768px) {
  .steps span {
    grid-column: auto;
  }
}
</style>
