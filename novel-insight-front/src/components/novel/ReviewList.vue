<script setup lang="ts">
import type { Novel } from '../../api/novelApi'
defineProps<{ novel: Novel }>()
</script>
<template>
  <div class="card">
    <ul v-if="novel.reviews.length > 0">
      <li v-for="review in novel.reviews" :key="review.id" style="padding:10px; border-bottom:1px solid #eee;">
        {{ review.content }}
      </li>
    </ul>
    <p v-else class="text-center" style="color:#999">등록된 리뷰가 없습니다.</p>
  </div>
</template>