package com.agriaudit.storage.application;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.storage.AttachmentStore.NewAttachment;
import com.agriaudit.storage.BlobStore;
import com.agriaudit.storage.infrastructure.persistence.Attachment;
import com.agriaudit.storage.infrastructure.persistence.AttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AttachmentStoreServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private AttachmentRepository repository;
    private BlobStore blobs;
    private AttachmentStoreService service;

    @BeforeEach
    void setUp() {
        repository = mock(AttachmentRepository.class);
        blobs = mock(BlobStore.class);
        service = new AttachmentStoreService(repository, blobs, new org.springframework.beans.factory.support.DefaultListableBeanFactory().getBeanProvider(com.agriaudit.storage.AttachmentDeletionGuard.class));
        when(blobs.store(any(), anyString(), eq("attachments"))).thenReturn("attachments/2026/k");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void guardaUnaImagenDetectadaPorMagicBytesAunqueElClienteNoDeclareTipo() {
        var dto = service.store(upload("sin-extension", null, PNG));

        assertThat(dto.contentType()).isEqualTo("image/png");
        assertThat(dto.image()).isTrue();
        assertThat(dto.uploadedBy()).isEqualTo("ana@demo.test");
        verify(blobs).store(PNG, "sin-extension", "attachments");
    }

    @Test
    void rechazaFicherosVaciosYTiposNoPermitidos() {
        assertThatThrownBy(() -> service.store(upload("vacio.png", "image/png", new byte[0])))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.store(upload("script.sh", "text/x-shellscript", "echo".getBytes())))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(blobs);
    }

    @Test
    void noSirveContenidoDeOtraOrganizacion() {
        when(repository.findByIdAndOrganizationId(5L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.content(2L, 5L)).isInstanceOf(NotFoundException.class);
        verify(blobs, never()).load(any());
    }

    @Test
    void borraMetadatosYContenido() {
        Attachment a = new Attachment();
        a.setStorageKey("attachments/2026/k");
        a.setEntityType("AUDIT");
        a.setEntityId(7L);
        when(repository.findByIdAndOrganizationId(5L, 1L)).thenReturn(Optional.of(a));

        service.delete(1L, 5L);

        verify(blobs).delete("attachments/2026/k");
        verify(repository).delete(a);
    }

    private static NewAttachment upload(String name, String type, byte[] content) {
        return new NewAttachment(1L, "ana@demo.test", AttachmentEntityType.AUDIT, 7L, name, type, content);
    }
}
