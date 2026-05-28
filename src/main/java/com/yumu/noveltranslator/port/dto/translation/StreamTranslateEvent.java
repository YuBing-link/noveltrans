package com.yumu.noveltranslator.port.dto.translation;

/**
 * 文档流式翻译事件 — port 层 DTO，不依赖 Spring 类型
 */
public class StreamTranslateEvent {

    private String textId;
    private String original;
    private String translation;
    private int progress;
    private boolean error;
    private boolean done;

    public static StreamTranslateEvent chunk(String textId, String original, String translation, int progress) {
        StreamTranslateEvent event = new StreamTranslateEvent();
        event.textId = textId;
        event.original = original;
        event.translation = translation;
        event.progress = progress;
        return event;
    }

    public static StreamTranslateEvent error(String message) {
        StreamTranslateEvent event = new StreamTranslateEvent();
        event.translation = "ERROR: " + message;
        event.error = true;
        return event;
    }

    public static StreamTranslateEvent done() {
        StreamTranslateEvent event = new StreamTranslateEvent();
        event.done = true;
        return event;
    }

    public String getTextId() { return textId; }
    public String getOriginal() { return original; }
    public String getTranslation() { return translation; }
    public int getProgress() { return progress; }
    public boolean isError() { return error; }
    public boolean isDone() { return done; }
}
