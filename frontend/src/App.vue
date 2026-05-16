<script setup lang="ts">
import ProfilePhotoUploader from './components/ProfilePhotoUploader.vue'
</script>

<template>
  <main class="app-shell">
    <section class="hero">
      <p class="eyebrow">ComfyUI-ready mock workflow</p>
      <h1>프로필 사진을 정장 프로필로 변환</h1>
      <p class="hero-description">
        이미지를 업로드하면 Spring Boot API가 파일을 임시 저장한 뒤 ComfyUI 클라이언트 계층을 호출합니다.
        현재는 mock 이미지로 결과를 보여주며, 이후 workflow JSON만 교체해 실제 ComfyUI 호출로 확장할 수 있습니다.
      </p>
    </section>

    <ProfilePhotoUploader />
  </main>
</template>
