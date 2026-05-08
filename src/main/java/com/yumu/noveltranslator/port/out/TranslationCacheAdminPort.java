package com.yumu.noveltranslator.port.out;

public interface TranslationCacheAdminPort {
    void invalidateKeysForTerm(String term);
    void clearAllCache();
}
