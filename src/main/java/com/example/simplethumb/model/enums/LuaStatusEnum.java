package com.example.simplethumb.model.enums;

import lombok.Getter;

/**
 * Lua脚本执行结果枚举
 */
@Getter
public enum LuaStatusEnum {
    // 成功
    SUCCESS(1L),
    // 失败
    FAIL(-1L),
    ;
    private final Long value;

    LuaStatusEnum(Long value) {
        this.value = value;
    }

}
