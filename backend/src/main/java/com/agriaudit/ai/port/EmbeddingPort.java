package com.agriaudit.ai.port;

import java.util.List;

/**
 * Puerto de embeddings (RT-A01, RT-A06). La elección del modelo (Voyage {@code voyage-3} gestionado o
 * {@code bge-m3} autoalojado) se decide en WS-5; hoy solo existen los adaptadores {@code none} y {@code fake}.
 */
public interface EmbeddingPort {

    boolean isAvailable();

    /** Identificador del modelo; se guarda con cada vector para no mezclar espacios vectoriales. */
    String model();

    int dimensions();

    /** Un vector por texto, en el mismo orden. */
    List<float[]> embed(List<String> texts);
}
