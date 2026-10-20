import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import App from '../App.vue'
import HomePage from '../pages/HomePage.vue'

vi.mock('@/api/catalog', () => ({
  catalogApi: {
    home: () => Promise.resolve({ featured: [], latest: [], categories: [], featuredReview: null }),
  },
}))

describe('App', () => {
  it('renders the home route through RouterView', async () => {
    const Empty = { render: () => null }
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'home', component: HomePage },
        { path: '/search', name: 'search', component: Empty },
        { path: '/register', name: 'register', component: Empty },
      ],
    })
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [router, createPinia(), [VueQueryPlugin, { queryClient: new QueryClient() }]],
      },
    })
    await flushPromises()

    expect(wrapper.get('h1 .sr-only').text()).toBe('LocalSpot')
  })
})
