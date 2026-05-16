package com.example.genprofileimage.profile;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ProfilePhotoExceptionHandler {

    @ExceptionHandler(InvalidImageUploadException.class)
    public ResponseEntity<ApiError> handleInvalidImageUpload(InvalidImageUploadException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("image 필드로 업로드할 파일을 전달해주세요."));
    }

    @ExceptionHandler(ComfyUiException.class)
    public ResponseEntity<ApiError> handleComfyUiException(ComfyUiException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiError.of(exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of("업로드 가능한 파일 크기를 초과했습니다."));
    }
}
