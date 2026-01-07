package com.admin.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 用户管理错误码
 * Validates: Requirements 2.6, 2.7, 3.5, 4.5, 5.4
 */
@Getter
public enum UserErrorCode {
    
    USER_001("USER_001", "用户名已存在", HttpStatus.CONFLICT),
    USER_002("USER_002", "邮箱已被使用", HttpStatus.CONFLICT),
    USER_003("USER_003", "用户不存在", HttpStatus.NOT_FOUND),
    USER_004("USER_004", "不能禁用最后一个管理员", HttpStatus.BAD_REQUEST),
    USER_005("USER_005", "不能删除最后一个管理员", HttpStatus.BAD_REQUEST),
    USER_006("USER_006", "导入文件格式错误", HttpStatus.BAD_REQUEST),
    USER_007("USER_007", "导入数据验证失败", HttpStatus.BAD_REQUEST),
    USER_008("USER_008", "无效的状态转换", HttpStatus.BAD_REQUEST),
    USER_009("USER_009", "邮箱格式不正确", HttpStatus.BAD_REQUEST);
    
    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
    
    UserErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
