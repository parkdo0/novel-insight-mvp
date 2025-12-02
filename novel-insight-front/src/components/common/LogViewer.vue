<script setup lang="ts">
defineProps<{
  logs: string[]
}>()
</script>

<template>
  <div class="log-area">
    <h3>📜 통신 로그</h3>
    <div v-for="(msg, index) in logs" :key="index" class="log-item">
      {{ msg }}
    </div>
  </div>
</template>

<style scoped>
.log-area { background: #333; color: #fff; padding: 15px; border-radius: 8px; min-height: 150px; font-family: monospace; }
.log-item { border-bottom: 1px solid #444; padding: 4px 0; }
</style>