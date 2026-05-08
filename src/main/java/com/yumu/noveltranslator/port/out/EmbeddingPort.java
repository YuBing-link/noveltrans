package com.yumu.noveltranslator.port.out;

public interface EmbeddingPort {
    float[] embed(String text);
    int getDimension();
}
