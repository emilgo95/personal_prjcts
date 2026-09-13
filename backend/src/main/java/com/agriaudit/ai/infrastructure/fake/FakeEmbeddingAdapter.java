package com.agriaudit.ai.infrastructure.fake;

import com.agriaudit.ai.port.EmbeddingPort;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Embeddings deterministas para tests y CI ({@code agriaudit.ai.embeddings.provider=fake}): bolsa de palabras
 * con hashing en 1024 dimensiones, normalizada. Textos que comparten palabras quedan cerca, lo suficiente para
 * probar indexado, orden y aislamiento sin llamar a ningún servicio.
 */
public class FakeEmbeddingAdapter implements EmbeddingPort {

    public static final String MODEL = "fake-hash-1024";
    private static final int DIMENSIONS = 1024;

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String model() {
        return MODEL;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.stream().map(FakeEmbeddingAdapter::embed).toList();
    }

    private static float[] embed(String text) {
        float[] v = new float[DIMENSIONS];
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        for (String token : normalized.split("[^a-z0-9]+")) {
            if (token.length() > 2) {
                v[Math.floorMod(token.hashCode(), DIMENSIONS)] += 1f;
            }
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm == 0) {
            v[0] = 1f;
            return v;
        }
        float inv = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < v.length; i++) {
            v[i] *= inv;
        }
        return v;
    }
}
