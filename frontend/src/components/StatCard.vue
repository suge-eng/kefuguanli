<template>
  <div class="stat-card">
    <div class="stat-label">{{ label }}</div>
    <div class="stat-value" :style="{ color }">
      {{ value }}<span v-if="suffix" class="stat-suffix">{{ suffix }}</span>
    </div>
    <div v-if="hint" class="stat-hint">{{ hint }}</div>
  </div>
</template>

<script setup>
defineProps({
  label: { type: String, default: '' },
  value: { type: [String, Number], default: '-' },
  suffix: { type: String, default: '' },
  hint: { type: String, default: '' },
  color: { type: String, default: '#1f2937' }
})
</script>

<style scoped>
.stat-card {
  background: #fff;
  border: 1px solid var(--app-card-border);
  border-radius: 8px;
  padding: 16px 18px;
  height: 100%;
}
.stat-label { font-size: 13px; color: #6b7280; }
.stat-value { font-size: 28px; font-weight: 700; margin-top: 8px; line-height: 1.2; }
.stat-suffix { font-size: 14px; font-weight: 500; margin-left: 2px; }
.stat-hint { font-size: 12px; color: #9ca3af; margin-top: 6px; }
</style>
