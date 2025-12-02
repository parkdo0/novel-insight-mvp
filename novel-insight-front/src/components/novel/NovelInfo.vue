<script setup lang="ts">
import type { Novel } from '../../api/novelApi'

// 부모에게서 novel 정보를 받음
defineProps<{ novel: Novel }>()
</script>

<template>
  <div class="info-box card">
    <h3>줄거리</h3>
    <p>{{ novel.description || '작품 설명이 없습니다.' }}</p>
  </div>
</template>

<style scoped>
.info-box { min-height: 200px; line-height: 1.6; }
</style>