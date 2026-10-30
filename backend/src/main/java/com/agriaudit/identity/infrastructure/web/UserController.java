package com.agriaudit.identity.infrastructure.web;

import com.agriaudit.identity.application.UserDataExportService;
import com.agriaudit.identity.application.UserService;
import com.agriaudit.identity.application.AuthDtos.UserDto;
import com.agriaudit.identity.application.UserDtos.TemporaryPasswordResponse;
import com.agriaudit.identity.application.UserDtos.UserCreateRequest;
import com.agriaudit.identity.application.UserDtos.UserUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;
    private final UserDataExportService dataExport;

    public UserController(UserService userService, UserDataExportService dataExport) {
        this.userService = userService;
        this.dataExport = dataExport;
    }

    @GetMapping
    public List<UserDto> list() {
        return userService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    /** Genera una contraseña temporal para otro usuario; solo se devuelve en esta respuesta. */
    @PostMapping("/{id}/reset-password")
    public TemporaryPasswordResponse resetPassword(@PathVariable Long id) {
        return userService.resetPassword(id);
    }

    @org.springframework.web.bind.annotation.PostMapping("/{id}/block")
    public UserDto block(@PathVariable Long id) {
        return userService.block(id);
    }

    /** Datos que la aplicación guarda sobre el usuario, en un ZIP (derecho de acceso, art. 15 RGPD). */
    @GetMapping("/{id}/data-export")
    public ResponseEntity<byte[]> dataExport(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"datos-usuario-" + id + "-" + LocalDate.now() + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(dataExport.export(id));
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(id, request);
    }
}
