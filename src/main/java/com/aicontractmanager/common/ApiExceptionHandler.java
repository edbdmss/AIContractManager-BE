package com.aicontractmanager.common;

import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    public record FieldError(String field, String message) {}
    public record Error(String code, String message, List<FieldError> fieldErrors) {}
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage())).toList();
        var jsonHeaders = new HttpHeaders();
        jsonHeaders.putAll(headers);
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(new Error("VALIDATION_ERROR", "요청값을 확인해 주세요.", fields), jsonHeaders, status);
    }
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var jsonHeaders = new HttpHeaders();
        jsonHeaders.putAll(headers);
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(new Error("HTTP_" + status.value(), "요청을 처리할 수 없습니다.", List.of()), jsonHeaders, status);
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Error> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(new Error("HTTP_" + ex.getStatusCode().value(),
                ex.getReason() == null ? "요청을 처리할 수 없습니다." : ex.getReason(), List.of()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Error> handleUnexpected(Exception ex) {
        logger.error("Unhandled API error", ex);
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON).body(new Error("INTERNAL_ERROR", "서버 오류가 발생했습니다.", List.of()));
    }
}
