export async function convertToSuitProfile(image: File): Promise<Blob> {
  const formData = new FormData()
  formData.append('image', image)

  const response = await fetch('/api/profile-photo/suit', {
    method: 'POST',
    body: formData,
  })

  if (!response.ok) {
    throw new Error(await resolveErrorMessage(response))
  }

  return response.blob()
}

async function resolveErrorMessage(response: Response): Promise<string> {
  const contentType = response.headers.get('content-type') ?? ''

  if (contentType.includes('application/json')) {
    const payload = (await response.json()) as { message?: string }
    return payload.message ?? '이미지 변환 중 오류가 발생했습니다.'
  }

  const text = await response.text()
  return text || '이미지 변환 중 오류가 발생했습니다.'
}
