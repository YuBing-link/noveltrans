package com.yumu.noveltranslator.port.out;

import java.util.List;

public interface EmbeddingPort {
    List<Double> embed(String text);
    List<List<Double>> embedBatch(List<String> texts);
}
