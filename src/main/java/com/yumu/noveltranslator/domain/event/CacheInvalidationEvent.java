package com.yumu.noveltranslator.domain.event;

/**
 * Redis pub/sub 缓存失效事件
 * 轻量 JSON 序列化，避免引入额外依赖
 */
public class CacheInvalidationEvent {

    private final String sourceLang;
    private final String targetLang;
    private final String version;
    private final long timestamp;

    public CacheInvalidationEvent(String sourceLang, String targetLang, String version) {
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.version = version;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 序列化为 JSON: {"sourceLang":"en","targetLang":"zh","version":"2","timestamp":1714838400000}
     */
    public String serialize() {
        return "{\"sourceLang\":\"" + escape(sourceLang) + "\","
             + "\"targetLang\":\"" + escape(targetLang) + "\","
             + "\"version\":\"" + escape(version) + "\","
             + "\"timestamp\":" + timestamp + "}";
    }

    /**
     * 从 JSON 反序列化为事件对象
     */
    public static CacheInvalidationEvent deserialize(String json) {
        String sourceLang = extractField(json, "sourceLang");
        String targetLang = extractField(json, "targetLang");
        String version = extractField(json, "version");
        return new CacheInvalidationEvent(sourceLang, targetLang, version);
    }

    public String getSourceLang() { return sourceLang; }
    public String getTargetLang() { return targetLang; }
    public String getVersion() { return version; }
    public long getTimestamp() { return timestamp; }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractField(String json, String fieldName) {
        String key = "\"" + fieldName + "\":\"";
        int start = json.indexOf(key);
        if (start == -1) return "";
        start += key.length();
        int end = json.indexOf('"', start);
        if (end == -1) return "";
        return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
