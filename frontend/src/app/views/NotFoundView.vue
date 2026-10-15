<script setup lang="ts">
import { RouterLink } from 'vue-router'

import EmptyState from '@/components/ui/EmptyState.vue'
import ProductMark from '@/components/ui/ProductMark.vue'
</script>

<template>
  <div class="public-shell">
    <a class="skip-link" href="#public-main">跳到正文</a>
    <div id="public-main" class="public-shell-inner">
      <ProductMark :size="24" />
      <EmptyState heading-tag="h1" title="页面不存在" description="没有匹配的产品路由。">
        <template #action>
          <RouterLink to="/">返回工作台</RouterLink>
        </template>
      </EmptyState>
    </div>
  </div>
</template>
