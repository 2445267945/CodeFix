<template>
  <div class="review-block" :class="`is-${level}`">
    <div class="review-icon">{{ icon }}</div>
    <div class="review-copy">
      <div class="review-title">{{ block.title || defaultTitle }}</div>
      <div v-if="block.content" class="review-content">{{ block.content }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ block: { type: Object, required: true } })

const level = computed(() => props.block.level || (props.block.status === 'failed' ? 'error' : 'info'))
const icon = computed(() => ({ success: '✓', warning: '!', error: '×', info: 'i' }[level.value] || 'i'))
const defaultTitle = computed(() => ({ success: '验证通过', warning: '需要注意', error: '执行失败', info: '执行信息' }[level.value] || '执行信息'))
</script>

<style scoped>
.review-block { display: flex; gap: 10px; margin: 6px 0; padding: 10px 12px; border-radius: 7px; border: 1px solid var(--border-subtle); background: var(--surface-soft); }
.review-icon { width: 20px; height: 20px; flex: 0 0 20px; display: grid; place-items: center; border-radius: 50%; background: var(--surface); color: var(--muted-strong); font-size: 11px; font-weight: 700; }
.is-success .review-icon { color: var(--success); }
.is-warning .review-icon { color: var(--warning); }
.is-error .review-icon { color: var(--danger); }
.review-title { font-size: 12px; font-weight: 600; }
.review-content { margin-top: 4px; color: var(--muted-strong); font-size: 12px; line-height: 1.6; white-space: pre-wrap; }
</style>
