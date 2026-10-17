package edu.njust.narrativestudio.exception;

import org.springframework.http.HttpStatus;

// 【业务异常】携带错误码、提示和 HTTP 状态；notFound/forbidden/conflict 分别对应 404/403/409，由全局处理器转响应。
// 它继承 RuntimeException，事务中抛出时通常触发回滚；修改提示可在调用点完成，不要为一个功能改全局状态码。
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }

    public static BusinessException notFound(String message) {
        return new BusinessException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException("CONFLICT", message, HttpStatus.CONFLICT);
    }
}
