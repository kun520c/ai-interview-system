<script setup lang="ts">
import { RouterLink } from 'vue-router'

import EmptyState from '@/components/ui/EmptyState.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
</script>

<template>
  <section>
    <PageHeader title="权限不足" description="当前账号没有管理员权限。你仍然处于登录状态。" />
    <EmptyState title="无法打开管理工具" description="USER 不能访问 /admin。这不是退出登录。">
      <template #action>
        <RouterLink to="/">返回工作台</RouterLink>
      </template>
    </EmptyState>
  </section>
</template>
