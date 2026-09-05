<template>
  <div class="final-answer">
    <div class="final-label"><span class="mark">✓</span> 已完成</div>
    <div class="final-content">{{ answer.content }}</div>
  </div>
</template>

<script setup>
defineProps({ answer: { type: Object, required: true } })
</script>

<style scoped>
.final-answer { padding-top: 8px; }
.final-label { display: flex; align-items: center; gap: 8px; color: var(--text-soft); font-size: 12px; font-weight: 600; }
.mark { width: 19px; height: 19px; display: grid; place-items: center; border-radius: 50%; background: var(--success-soft); color: var(--success); font-size: 10px; }
.final-content { margin-top: 10px; color: var(--text); font-size: 14px; line-height: 1.75; white-space: pre-wrap; }
</style>
