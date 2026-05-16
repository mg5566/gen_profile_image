<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { convertToSuitProfile } from '../api/profilePhotoApi'

const MAX_UPLOAD_SIZE_BYTES = 10 * 1024 * 1024

const selectedFile = ref<File | null>(null)
const beforeUrl = ref<string | null>(null)
const afterUrl = ref<string | null>(null)
const isLoading = ref(false)
const errorMessage = ref<string | null>(null)

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  setSelectedFile(file)
}

function handleDrop(event: DragEvent) {
  const file = event.dataTransfer?.files?.[0] ?? null
  setSelectedFile(file)
}

function setSelectedFile(file: File | null) {
  errorMessage.value = null
  selectedFile.value = null
  revokeUrl(beforeUrl.value)
  revokeUrl(afterUrl.value)
  beforeUrl.value = null
  afterUrl.value = null

  if (!file) {
    return
  }

  if (!file.type.startsWith('image/')) {
    errorMessage.value = '이미지 파일만 업로드할 수 있습니다.'
    return
  }

  if (file.size > MAX_UPLOAD_SIZE_BYTES) {
    errorMessage.value = '최대 10MB까지 업로드할 수 있습니다.'
    return
  }

  selectedFile.value = file
  beforeUrl.value = URL.createObjectURL(file)
}

async function submit() {
  if (!selectedFile.value || isLoading.value) {
    return
  }

  isLoading.value = true
  errorMessage.value = null
  revokeUrl(afterUrl.value)
  afterUrl.value = null

  try {
    const convertedImage = await convertToSuitProfile(selectedFile.value)
    afterUrl.value = URL.createObjectURL(convertedImage)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '이미지 변환 중 오류가 발생했습니다.'
  } finally {
    isLoading.value = false
  }
}

function revokeUrl(url: string | null) {
  if (url) {
    URL.revokeObjectURL(url)
  }
}

onBeforeUnmount(() => {
  revokeUrl(beforeUrl.value)
  revokeUrl(afterUrl.value)
})
</script>

<template>
  <section class="uploader-card" aria-labelledby="upload-title">
    <div class="card-header">
      <div>
        <p class="eyebrow">Upload</p>
        <h2 id="upload-title">이미지 업로드</h2>
      </div>
      <span class="status-pill">Mock mode</span>
    </div>

    <label
      class="drop-zone"
      :class="{ 'has-file': selectedFile }"
      @dragover.prevent
      @drop.prevent="handleDrop"
    >
      <input type="file" accept="image/*" @change="handleFileChange" />
      <span class="upload-icon">📸</span>
      <strong>{{ selectedFile ? selectedFile.name : '클릭하거나 이미지를 끌어다 놓으세요' }}</strong>
      <small>JPG, PNG, WEBP 등 이미지 파일을 지원합니다. 최대 10MB까지 업로드할 수 있습니다.</small>
    </label>

    <div class="actions">
      <button type="button" :disabled="!selectedFile || isLoading" @click="submit">
        <span v-if="isLoading" class="spinner" aria-hidden="true"></span>
        {{ isLoading ? '정장 프로필 생성 중...' : '정장 프로필로 변환하기' }}
      </button>
      <p v-if="errorMessage" class="error-message" role="alert">{{ errorMessage }}</p>
    </div>

    <div class="comparison-grid" aria-live="polite">
      <article class="preview-panel">
        <div class="preview-header">
          <h3>Before</h3>
          <span>원본</span>
        </div>
        <div class="image-frame">
          <img v-if="beforeUrl" :src="beforeUrl" alt="업로드한 원본 프로필 사진" />
          <p v-else>변환할 이미지를 선택해주세요.</p>
        </div>
      </article>

      <article class="preview-panel after-panel">
        <div class="preview-header">
          <h3>After</h3>
          <span>정장 프로필</span>
        </div>
        <div class="image-frame">
          <img v-if="afterUrl" :src="afterUrl" alt="mock으로 생성된 정장 프로필 사진" />
          <div v-else-if="isLoading" class="loading-box">
            <span class="spinner large" aria-hidden="true"></span>
            <p>ComfyUI mock 클라이언트가 이미지를 준비하고 있습니다.</p>
          </div>
          <p v-else>변환 결과가 여기에 표시됩니다.</p>
        </div>
      </article>
    </div>
  </section>
</template>
