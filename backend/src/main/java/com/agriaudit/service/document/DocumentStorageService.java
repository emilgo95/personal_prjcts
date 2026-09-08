package com.agriaudit.service.document;

import com.agriaudit.common.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class DocumentStorageService {

    private final Path root;

    public DocumentStorageService(@Value("${agriaudit.storage.dir:./data/storage}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root.resolve("documents"));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el directorio de almacenamiento " + this.root, e);
        }
    }

    /** Guarda los bytes en la categoría "documents" y devuelve la clave relativa. */
    public String store(byte[] data, String fileName) {
        return store(data, fileName, "documents");
    }

    /** Guarda los bytes bajo la categoría indicada y devuelve la clave relativa de almacenamiento. */
    public String store(byte[] data, String fileName, String category) {
        String safeCategory = category.replaceAll("[^A-Za-z0-9_-]", "_");
        String safeName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        String key = safeCategory + "/" + LocalDate.now().getYear() + "/" + UUID.randomUUID() + "-" + safeName;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Ruta de almacenamiento no válida");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el documento", e);
        }
        return key;
    }

    public byte[] load(String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw new NotFoundException("Fichero no disponible en almacenamiento: " + storageKey);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el fichero", e);
        }
    }

    /** Borra el fichero si existe. No falla si ya no está. */
    public void delete(String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar el fichero", e);
        }
    }
}
