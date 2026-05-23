# Gen Profile Image

프로필 사진을 업로드하면 Spring Boot 백엔드가 이미지를 임시 파일로 저장하고, ComfyUI 클라이언트 계층을 통해 정장 프로필 사진을 생성하는 예제 프로젝트입니다. 기본값은 mock PNG를 반환하지만, `COMFYUI_MOCK=false`로 실행하면 실제 ComfyUI 서버(`http://localhost:8188`)의 모델 workflow를 호출합니다.

## 기술 스펙

| 영역 | 기술 | 버전/설정 |
| --- | --- | --- |
| Frontend | Vue | 3.5.22 |
| Frontend | TypeScript | 5.9.3 |
| Frontend | Vite | 6.4.2 |
| Backend | Spring Boot | 3.5.7 |
| Backend | Java target | 17 |
| Backend build | Maven | 3.9.15 |
| Image server | ComfyUI | `http://localhost:8188` 기준, mock/real 전환 가능 |
| API | Multipart upload | `POST /api/profile-photo/suit` |

## 프로젝트 구조

```text
backend/   Spring Boot API 서버
frontend/  Vue 3 + TypeScript 화면
```

## 백엔드 실행 방법

asdf를 사용한다면 프로젝트 루트에서 설정된 런타임을 먼저 확인합니다.

```bash
asdf current
```

`mvn` 실행 시 Java Runtime을 찾지 못한다면 asdf Java hook을 shell 설정에 추가합니다.

```bash
. ~/.asdf/plugins/java/set-java-home.zsh
```

```bash
cd backend
mvn spring-boot:run
```

기본 포트는 Spring Boot 기본값인 `8080`입니다.

### 백엔드 설정

`backend/src/main/resources/application.yml`에서 ComfyUI 설정을 관리합니다.

```yaml
app:
  comfyui:
    base-url: http://localhost:8188
    mock: ${COMFYUI_MOCK:true}
    fallback-to-mock: ${COMFYUI_FALLBACK_TO_MOCK:true}
    workflow-path: classpath:comfyui/workflows/suit-profile.example.json
    checkpoint-name: ${COMFYUI_CHECKPOINT_NAME:Realistic_Vision_V6.0_NV_B1_fp16.safetensors}
    positive-prompt: ${COMFYUI_POSITIVE_PROMPT:professional corporate headshot profile photo, wearing a tailored navy suit, white shirt, studio lighting, clean neutral background, realistic, high detail}
    negative-prompt: ${COMFYUI_NEGATIVE_PROMPT:low quality, blurry, distorted face, extra fingers, bad anatomy, cartoon, anime, watermark, text, logo}
    seed: ${COMFYUI_SEED:-1}
    steps: ${COMFYUI_STEPS:24}
    cfg: ${COMFYUI_CFG:7.0}
    denoise: ${COMFYUI_DENOISE:0.65}
    sampler-name: ${COMFYUI_SAMPLER_NAME:euler}
    scheduler: ${COMFYUI_SCHEDULER:normal}
    output-prefix: ${COMFYUI_OUTPUT_PREFIX:suit-profile}
    polling-timeout-seconds: 120
    polling-interval-millis: 1000
```

기본값은 `COMFYUI_MOCK=true`이므로 실제 ComfyUI 서버를 호출하지 않습니다. 실제 연동 시에는 ComfyUI를 `http://localhost:8188`에서 실행한 뒤 백엔드를 다음처럼 실행합니다.

```bash
cd /Users/hack3rs/goinfre/ComfyUI
.venv/bin/python main.py --listen 127.0.0.1 --port 8188 --fp32-vae --force-upcast-attention
```

```bash
cd backend
COMFYUI_MOCK=false \
COMFYUI_CHECKPOINT_NAME=Realistic_Vision_V6.0_NV_B1_fp16.safetensors \
mvn spring-boot:run
```

`COMFYUI_CHECKPOINT_NAME`은 ComfyUI의 `models/checkpoints`에 들어 있는 실제 파일명과 정확히 일치해야 합니다. Apple Silicon/MPS에서는 VAE가 bf16으로 실행될 때 검은 이미지가 나올 수 있어 ComfyUI를 `--fp32-vae --force-upcast-attention` 옵션으로 실행합니다. 현재 workflow는 표준 ComfyUI API 노드인 `CheckpointLoaderSimple -> CLIPTextEncode -> LoadImage -> VAEEncode -> KSampler -> VAEDecode -> SaveImage`로 구성되어 있습니다.

`COMFYUI_FALLBACK_TO_MOCK=true`이면 real 모드에서도 ComfyUI 서버 연결에 실패할 때 mock 이미지로 응답합니다. 실제 서버 오류를 바로 확인하고 싶으면 `COMFYUI_FALLBACK_TO_MOCK=false`로 실행하세요.

## 프론트엔드 실행 방법

```bash
cd frontend
npm install
npm run dev
```

Vite 개발 서버는 기본적으로 `http://localhost:5173`에서 실행됩니다. `/api` 요청은 `vite.config.ts`의 proxy 설정을 통해 `http://localhost:8080`으로 전달됩니다.

## 사용 방법

1. 백엔드 실행: `cd backend && mvn spring-boot:run`
2. 프론트엔드 실행: `cd frontend && npm run dev`
3. 브라우저에서 `http://localhost:5173` 접속
4. 이미지 업로드
5. `정장 프로필로 변환하기` 버튼 클릭
6. Before / After 영역에서 원본과 변환 결과 확인

## API

### `POST /api/profile-photo/suit`

이미지 파일을 multipart form-data로 업로드하면 정장 프로필 PNG를 반환합니다. `COMFYUI_MOCK=false`일 때는 실제 ComfyUI 생성 결과를 반환합니다.

Request:

```text
Content-Type: multipart/form-data
image: 업로드할 이미지 파일
```

Response:

```text
200 OK
Content-Type: image/png
```

## ComfyUI 실제 연동

`ComfyUiClient` 인터페이스 뒤에 mock/real 구현을 분리했습니다.

- `MockComfyUiClient`: 로컬 개발용 mock PNG 생성기
- `RealComfyUiClient`: `/upload/image`, `/prompt`, `/history/{prompt_id}`, `/view`를 호출하는 실제 연동 클라이언트
- `comfyui/workflows/suit-profile.example.json`: 교체 가능한 ComfyUI API workflow JSON template 위치

실제 클라이언트는 다음 흐름으로 동작합니다.

1. 업로드된 임시 파일을 ComfyUI `/upload/image`로 전송
2. workflow JSON의 placeholder를 백엔드 설정값으로 치환
3. `/prompt`로 workflow queue 등록
4. 반환된 `prompt_id`로 `/history/{prompt_id}` polling
5. 결과 이미지 파일명을 추출
6. `/view`로 결과 이미지를 다운로드
7. Spring Boot API 응답으로 프론트엔드에 PNG/JPEG 반환

다른 workflow로 교체할 때는 필요한 위치에 다음 placeholder를 유지하면 백엔드 설정값이 자동 주입됩니다.

- `${input_image}`
- `${checkpoint_name}`
- `${positive_prompt}`
- `${negative_prompt}`
- `${seed}`
- `${steps}`
- `${cfg}`
- `${denoise}`
- `${sampler_name}`
- `${scheduler}`
- `${output_prefix}`

## 검증 명령

```bash
cd backend && mvn test
cd frontend && npm install
cd frontend && npm run build
```
