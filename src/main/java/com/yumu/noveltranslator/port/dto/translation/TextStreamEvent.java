package com.yumu.noveltranslator.port.dto.translation;

/**
 * 文本流式翻译事件 — port 层 DTO，不依赖 Spring 类型
 */
public class TextStreamEvent {

    private String text;
    private boolean done;
    private boolean error;

    public static TextStreamEvent chunk(String text) {
        TextStreamEvent event = new TextStreamEvent();
        event.text = text;
        return event;
    }

    public static TextStreamEvent done() {
        TextStreamEvent event = new TextStreamEvent();
        event.done = true;
        return event;
    }

    public static TextStreamEvent error(String message) {
        TextStreamEvent event = new TextStreamEvent();
        event.text = "ERROR: " + message;
        event.error = true;
        return event;
    }

    public String getText() { return text; }
    public boolean isDone() { return done; }
    public boolean isError() { return error; }
}
