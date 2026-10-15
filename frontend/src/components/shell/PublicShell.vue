<script setup lang="ts">
import { RouterView } from 'vue-router'

import ProductMark from '@/components/ui/ProductMark.vue'
import {
  PUBLIC_PRODUCT_HEADLINE,
  PUBLIC_PRODUCT_NAME,
  PUBLIC_PRODUCT_POINTS,
  PUBLIC_PRODUCT_TAGLINE,
} from '@/features/auth/copy/publicProductCopy'
</script>

<template>
  <div class="public-shell">
    <a class="skip-link" href="#public-main">跳到正文</a>
    <div class="public-layout">
      <main id="public-main" class="public-main">
        <div class="public-brand">
          <ProductMark :size="24" />
          <div class="product-brand-copy">
            <p class="product-brand-name">{{ PUBLIC_PRODUCT_NAME }}</p>
            <p class="product-brand-sub">{{ PUBLIC_PRODUCT_TAGLINE }}</p>
          </div>
        </div>
        <RouterView />
      </main>
      <aside class="public-aside" aria-label="产品说明">
        <p class="public-aside-kicker">{{ PUBLIC_PRODUCT_TAGLINE }}</p>
        <h2>{{ PUBLIC_PRODUCT_HEADLINE }}</h2>
        <ul>
          <li v-for="point in PUBLIC_PRODUCT_POINTS" :key="point">{{ point }}</li>
        </ul>
      </aside>
    </div>
  </div>
</template>
