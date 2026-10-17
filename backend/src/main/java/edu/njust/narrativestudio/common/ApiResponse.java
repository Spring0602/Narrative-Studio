package edu.njust.narrativestudio.common;

import java.time.Instant;

// 【公共响应】ok(data) 生成成功包，fail(code,message) 生成错误包；T 是业务数据类型，timestamp 是响应时间。
// 【修改提示】增加公共字段会影响全站前端和测试；HTTP 状态由 Controller/异常处理器设置，不是把 success 改 true 就修好了异常。
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message), Instant.now());
    }

    public record ApiError(String code, String message) {}
}
