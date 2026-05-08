package com.yumu.noveltranslator.adapter.out.translate;

import com.yumu.noveltranslator.enums.TranslationMode;

/**
 * 前端引擎别名注册表
 *
 * @deprecated 请使用 {@link com.yumu.noveltranslator.domain.util.EngineAliasRegistry}
 */
@Deprecated
public class EngineAliasRegistry {

    private EngineAliasRegistry() {
    }

    public static TranslationMode normalizeToMode(String engineName) {
        return com.yumu.noveltranslator.domain.util.EngineAliasRegistry.normalizeToMode(engineName);
    }
}
