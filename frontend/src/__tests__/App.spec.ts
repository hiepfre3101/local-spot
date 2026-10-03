import { describe, it, expect } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'

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

    const wrapper = mount(App, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('LocalSpot')
  })
})
