<template>
  <el-tag :type="meta.type" effect="plain" size="small">{{ meta.label }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { priorityMap } from '../utils/dict'

const props = defineProps({ priority: { type: String, default: '' } })
const meta = computed(() => priorityMap[props.priority] || { label: props.priority || '-', type: 'info' })
</script>
