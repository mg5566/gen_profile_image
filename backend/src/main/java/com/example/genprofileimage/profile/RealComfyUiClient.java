package com.example.genprofileimage.profile;

import com.example.genprofileimage.config.ComfyUiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.comfyui", name = "mock", havingValue = "false")
public class RealComfyUiClient implements ComfyUiClient {

    private static final String INPUT_IMAGE_PLACEHOLDER = "${input_image}";

    private final ComfyUiProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RealComfyUiClient(ComfyUiProperties properties, ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Override
    public byte[] generateSuitProfile(Path inputImagePath) {
        try {
            UploadedImage uploadedImage = uploadImage(inputImagePath);
            JsonNode workflow = loadWorkflow(uploadedImage.name());
            String promptId = queuePrompt(workflow);
            OutputImage outputImage = waitForOutputImage(promptId);
            return downloadImage(outputImage);
        } catch (IOException exception) {
            throw new ComfyUiException("ComfyUI workflow 파일을 읽거나 파싱하지 못했습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ComfyUiException("ComfyUI 결과 대기 중 작업이 중단되었습니다.", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof ComfyUiException comfyUiException) {
                throw comfyUiException;
            }
            throw new ComfyUiException("ComfyUI 서버 호출 중 오류가 발생했습니다.", exception);
        }
    }

    private UploadedImage uploadImage(Path inputImagePath) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("image", new FileSystemResource(inputImagePath));
        parts.add("overwrite", "true");

        JsonNode response = restClient.post()
                .uri("/upload/image")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("name").isMissingNode()) {
            throw new ComfyUiException("ComfyUI 이미지 업로드 응답에서 파일명을 찾지 못했습니다.");
        }

        return new UploadedImage(response.path("name").asText());
    }

    private JsonNode loadWorkflow(String uploadedImageName) throws IOException {
        byte[] workflowBytes = resourceLoader.getResource(properties.workflowPath()).getInputStream().readAllBytes();
        String workflowJson = new String(workflowBytes, StandardCharsets.UTF_8)
                .replace(INPUT_IMAGE_PLACEHOLDER, uploadedImageName);
        return objectMapper.readTree(workflowJson);
    }

    private String queuePrompt(JsonNode workflow) {
        String clientId = UUID.randomUUID().toString();
        JsonNode response = restClient.post()
                .uri("/prompt")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("client_id", clientId, "prompt", workflow))
                .retrieve()
                .body(JsonNode.class);

        String promptId = response == null ? null : response.path("prompt_id").asText(null);
        if (promptId == null || promptId.isBlank()) {
            throw new ComfyUiException("ComfyUI prompt 등록 응답에서 prompt_id를 찾지 못했습니다.");
        }
        return promptId;
    }

    private OutputImage waitForOutputImage(String promptId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(properties.pollingTimeoutSeconds()));

        while (Instant.now().isBefore(deadline)) {
            JsonNode history = restClient.get()
                    .uri("/history/{promptId}", promptId)
                    .retrieve()
                    .body(JsonNode.class);

            OutputImage outputImage = findFirstOutputImage(history, promptId);
            if (outputImage != null) {
                return outputImage;
            }

            Thread.sleep(properties.pollingIntervalMillis());
        }

        throw new ComfyUiException("ComfyUI 결과 이미지 생성 대기 시간이 초과되었습니다. promptId=" + promptId);
    }

    private OutputImage findFirstOutputImage(JsonNode history, String promptId) {
        if (history == null || !history.has(promptId)) {
            return null;
        }

        JsonNode outputs = history.path(promptId).path("outputs");
        if (!outputs.isObject()) {
            return null;
        }

        for (JsonNode output : outputs) {
            JsonNode images = output.path("images");
            if (!images.isArray() || images.isEmpty()) {
                continue;
            }

            JsonNode image = images.get(0);
            String filename = image.path("filename").asText(null);
            if (filename == null || filename.isBlank()) {
                continue;
            }

            return new OutputImage(
                    filename,
                    image.path("subfolder").asText(""),
                    image.path("type").asText("output")
            );
        }

        return null;
    }

    private byte[] downloadImage(OutputImage outputImage) {
        URI uri = UriComponentsBuilder.fromPath("/view")
                .queryParam("filename", outputImage.filename())
                .queryParam("subfolder", outputImage.subfolder())
                .queryParam("type", outputImage.type())
                .build()
                .encode()
                .toUri();

        byte[] image = restClient.get()
                .uri(uri)
                .retrieve()
                .body(byte[].class);

        if (image == null || image.length == 0) {
            throw new ComfyUiException("ComfyUI 결과 이미지를 다운로드하지 못했습니다.");
        }
        return image;
    }

    private record UploadedImage(String name) {
    }

    private record OutputImage(String filename, String subfolder, String type) {
    }
}
