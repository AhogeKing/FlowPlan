package com.lxy.flowplan.exception;

import com.lxy.flowplan.pojo.Result;
import com.lxy.flowplan.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final OperationLogService operationLogService;

    public GlobalExceptionHandler(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @ExceptionHandler(JwtAuthenticationException.class)
    public ResponseEntity<Result> handleJwtAuthenticationException(JwtAuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(e.getMessage()));
    }

    @ExceptionHandler(AdminAccessDeniedException.class)
    public ResponseEntity<Result> handleAdminAccessDeniedException(AdminAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.error(e.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result> handleMissingRequestHeaderException(MissingRequestHeaderException e,
                                                                      HttpServletRequest request) {
        operationLogService.logFailureForCurrentRequest(e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error("缺少请求头：" + e.getHeaderName()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result> handleMissingServletRequestParameterException(MissingServletRequestParameterException e,
                                                                               HttpServletRequest request) {
        operationLogService.logFailureForCurrentRequest(e.getMessage());
        return ResponseEntity.badRequest().body(Result.error("缺少请求参数：" + e.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e,
                                                                           HttpServletRequest request) {
        operationLogService.logFailureForCurrentRequest(e.getMessage());
        return ResponseEntity.badRequest().body(Result.error("请求参数格式错误：" + e.getName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result> handleHttpMessageNotReadableException(HttpMessageNotReadableException e,
                                                                       HttpServletRequest request) {
        operationLogService.logFailureForCurrentRequest(e.getMessage());
        return ResponseEntity.badRequest().body(Result.error("请求体不能为空或格式不正确"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result> handleValidationException(MethodArgumentNotValidException e,
                                                           HttpServletRequest request) {
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("请求参数错误");

        operationLogService.logFailureForCurrentRequest(message);
        return ResponseEntity.badRequest().body(Result.error(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result> handleIllegalArgumentException(IllegalArgumentException e,
                                                                HttpServletRequest request) {
        operationLogService.logFailureForCurrentRequest(e.getMessage());
        return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result> handleDataIntegrityViolationException(DataIntegrityViolationException e,
                                                                       HttpServletRequest request) {
        operationLogService.logFailureForCurrentRequest(e.getMessage());
        return ResponseEntity.badRequest().body(Result.error("请求数据违反唯一性或非空约束"));
    }
}
