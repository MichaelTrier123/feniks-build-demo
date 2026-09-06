package com.example.feniksdemo;

import java.net.URI;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Provide valid JSON with only the allowed fields and values.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().hasFieldErrors("status")
                ? "status must be OPEN, IN_PROGRESS, CLOSED or CANCELLED."
                : "title must contain 1–200 characters after trimming.";
        return problem(HttpStatus.BAD_REQUEST, detail, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return invalidId(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Object> invalidId(IllegalArgumentException ex, WebRequest request) {
        return invalidId(request);
    }

    private ResponseEntity<Object> invalidId(WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "id must be a positive 64-bit integer.", request);
    }

    @ExceptionHandler(CaseNotFoundException.class)
    ResponseEntity<Object> notFound(CaseNotFoundException ex, WebRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Case not found.", request);
    }

    private ResponseEntity<Object> problem(HttpStatus status, String detail, WebRequest request) {
        var body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setInstance(URI.create(((ServletWebRequest) request).getRequest().getRequestURI()));
        return ResponseEntity.status(status).body(body);
    }
}
