<template>
  <el-tag :type="meta.type" effect="light" size="small">{{ meta.label }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { statusMap } from '../utils/dict'

const props = defineProps({ status: { type: String, default: '' } })
const meta = computed(() => statusMap[props.status] || { label: props.status || '-', type: 'info' })
</script>
