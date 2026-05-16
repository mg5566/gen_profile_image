# Gen Profile Image

프로필 사진을 업로드하면 Spring Boot 백엔드가 이미지를 임시 파일로 저장하고, ComfyUI 클라이언트 계층을 통해 정장 프로필 사진을 생성하는 예제 프로젝트입니다. 현재 구현은 실제 ComfyUI 호출 대신 mock PNG 이미지를 반환하며, 이후 workflow JSON과 `RealComfyUiClient` 구현만 교체해 실제 ComfyUI 서버(`http://localhost:8188`)로 확장할 수 있게 구성되어 있습니다.

## 기술 스펙

| 영역 | 기술 | 버전/설정 |
| --- | --- | --- |
| Frontend | Vue | 3.5.22 |
| Frontend | TypeScript | 5.9.3 |
| Frontend | Vite | 6.3.5 |
| Backend | Spring Boot | 3.5.7 |
| Backend | Java target | 21 |
| Backend build | Maven | 3.x |
| Image server | ComfyUI | `http://localhost:8188` 기준, 현재는 mock 모드 |
| API | Multipart upload | `POST /api/profile-photo/suit` |

## 프로젝트 구조

```text
backend/   Spring Boot API 서버
frontend/  Vue 3 + TypeScript 화면
```

## 백엔드 실행 방법

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
    mock: true
    workflow-path: classpath:comfyui/workflows/suit-profile.example.json
    polling-timeout-seconds: 120
    polling-interval-millis: 1000
```

현재는 `mock: true`이므로 실제 ComfyUI 서버를 호출하지 않습니다. 실제 연동 시에는 `mock: false`로 바꾸고 `workflow-path`가 가리키는 JSON을 ComfyUI에서 export한 API workflow로 교체하면 됩니다. 업로드 파일명이 들어갈 Load Image 노드의 `image` 값에는 `${input_image}` placeholder를 유지해주세요.

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
6. Before / After 영역에서 원본과 mock 변환 결과 확인

## API

### `POST /api/profile-photo/suit`

이미지 파일을 multipart form-data로 업로드하면 mock 정장 프로필 PNG를 반환합니다.

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

## ComfyUI 실제 연동 확장 지점

초기 구현에서는 `ComfyUiClient` 인터페이스 뒤에 mock 구현을 연결했습니다.

- `MockComfyUiClient`: 현재 사용 중인 mock PNG 생성기
- `RealComfyUiClient`: `/upload/image`, `/prompt`, `/history/{prompt_id}`, `/view`를 호출하는 실제 연동 클라이언트
- `comfyui/workflows/suit-profile.example.json`: 교체 가능한 workflow JSON template 위치. 업로드 이미지 파일명 주입 지점은 `${input_image}` placeholder로 표시합니다. 포함된 예시는 구조 설명용 최소 template이므로, 실제 변환에는 ComfyUI에서 export한 완성 workflow로 교체해야 합니다.

실제 클라이언트는 다음 흐름을 이미 코드로 분리해두었습니다.

1. 업로드된 임시 파일을 ComfyUI `/upload/image`로 전송
2. workflow JSON의 `${input_image}` placeholder를 업로드 파일명으로 치환
3. `/prompt`로 workflow queue 등록
4. 반환된 `prompt_id`로 `/history/{prompt_id}` polling
5. 결과 이미지 파일명을 추출
6. `/view`로 결과 이미지를 다운로드
7. Spring Boot API 응답으로 프론트엔드에 PNG/JPEG 반환

실제 workflow로 전환하려면 ComfyUI에서 API format workflow를 export하고, Load Image 노드의 이미지 파일명 자리에 `${input_image}`를 넣은 뒤 `backend/src/main/resources/comfyui/workflows/suit-profile.example.json`를 교체하세요.

## 검증 명령

```bash
cd backend && mvn test
cd frontend && npm install
cd frontend && npm run build
```
