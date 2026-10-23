<template>
  <div class="page-header">
    <div>
      <div class="title">{{ title }}</div>
      <div v-if="subtitle" class="subtitle">{{ subtitle }}</div>
    </div>
    <div class="actions"><slot /></div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' }
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.title { font-size: 18px; font-weight: 600; }
.subtitle { font-size: 12px; color: #6b7280; margin-top: 4px; }
.actions { display: flex; gap: 8px; }
</style>
