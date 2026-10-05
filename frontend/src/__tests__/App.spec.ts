import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import App from '../App.vue'
import HomePage from '../pages/HomePage.vue'

describe('App', () => {
  it('renders the home route through RouterView', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: HomePage }],
    })
    router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [router, createPinia(), [VueQueryPlugin, { queryClient: new QueryClient() }]],
      },
    })
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('LocalSpot')
  })
})
