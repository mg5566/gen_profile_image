package com.example.genprofileimage.profile;

import com.example.genprofileimage.config.ComfyUiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(prefix = "app.comfyui", name = "mock", havingValue = "false")
public class RealComfyUiClient implements ComfyUiClient {

    private static final String INPUT_IMAGE_PLACEHOLDER = "${input_image}";
    private static final String CHECKPOINT_PLACEHOLDER = "${checkpoint_name}";
    private static final String POSITIVE_PROMPT_PLACEHOLDER = "${positive_prompt}";
    private static final String NEGATIVE_PROMPT_PLACEHOLDER = "${negative_prompt}";
    private static final String SEED_PLACEHOLDER = "${seed}";
    private static final String STEPS_PLACEHOLDER = "${steps}";
    private static final String CFG_PLACEHOLDER = "${cfg}";
    private static final String DENOISE_PLACEHOLDER = "${denoise}";
    private static final String SAMPLER_NAME_PLACEHOLDER = "${sampler_name}";
    private static final String SCHEDULER_PLACEHOLDER = "${scheduler}";
    private static final String OUTPUT_PREFIX_PLACEHOLDER = "${output_prefix}";

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
        } catch (RestClientResponseException exception) {
            throw new ComfyUiException("ComfyUI 서버가 오류를 반환했습니다. " + responseSummary(exception), exception);
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
        parts.add("type", "input");
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
        JsonNode workflow = objectMapper.readTree(new String(workflowBytes, StandardCharsets.UTF_8));
        applyWorkflowParameters(workflow, uploadedImageName);
        return workflow;
    }

    private void applyWorkflowParameters(JsonNode workflow, String uploadedImageName) {
        replaceTextPlaceholders(workflow, Map.ofEntries(
                Map.entry(INPUT_IMAGE_PLACEHOLDER, uploadedImageName),
                Map.entry(CHECKPOINT_PLACEHOLDER, properties.checkpointName()),
                Map.entry(POSITIVE_PROMPT_PLACEHOLDER, properties.positivePrompt()),
                Map.entry(NEGATIVE_PROMPT_PLACEHOLDER, properties.negativePrompt()),
                Map.entry(SEED_PLACEHOLDER, Long.toString(resolveSeed())),
                Map.entry(STEPS_PLACEHOLDER, Integer.toString(properties.steps())),
                Map.entry(CFG_PLACEHOLDER, Double.toString(properties.cfg())),
                Map.entry(DENOISE_PLACEHOLDER, Double.toString(properties.denoise())),
                Map.entry(SAMPLER_NAME_PLACEHOLDER, properties.samplerName()),
                Map.entry(SCHEDULER_PLACEHOLDER, properties.scheduler()),
                Map.entry(OUTPUT_PREFIX_PLACEHOLDER, properties.outputPrefix())
        ));
    }

    private void replaceTextPlaceholders(JsonNode node, Map<String, String> values) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.properties().forEach(entry -> {
                JsonNode child = entry.getValue();
                if (child instanceof TextNode textNode) {
                    objectNode.set(entry.getKey(), replacementNode(textNode.asText(), values));
                    return;
                }
                replaceTextPlaceholders(child, values);
            });
            return;
        }

        if (node instanceof ArrayNode arrayNode) {
            for (int index = 0; index < arrayNode.size(); index++) {
                JsonNode child = arrayNode.get(index);
                if (child instanceof TextNode textNode) {
                    arrayNode.set(index, replacementNode(textNode.asText(), values));
                    continue;
                }
                replaceTextPlaceholders(child, values);
            }
        }
    }

    private JsonNode replacementNode(String text, Map<String, String> values) {
        return switch (text) {
            case SEED_PLACEHOLDER -> new LongNode(Long.parseLong(values.get(SEED_PLACEHOLDER)));
            case STEPS_PLACEHOLDER -> new IntNode(properties.steps());
            case CFG_PLACEHOLDER -> new DoubleNode(properties.cfg());
            case DENOISE_PLACEHOLDER -> new DoubleNode(properties.denoise());
            default -> new TextNode(replacePlaceholders(text, values));
        };
    }

    private long resolveSeed() {
        if (properties.seed() >= 0) {
            return properties.seed();
        }
        return ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
    }

    private String replacePlaceholders(String text, Map<String, String> values) {
        String replaced = text;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            replaced = replaced.replace(entry.getKey(), entry.getValue());
        }
        return replaced;
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

    private String responseSummary(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "status=" + exception.getStatusCode();
        }

        String normalized = body.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 500) {
            normalized = normalized.substring(0, 500) + "...";
        }
        return "status=" + exception.getStatusCode() + ", body=" + normalized;
    }

    private record UploadedImage(String name) {
    }

    private record OutputImage(String filename, String subfolder, String type) {
    }
}
