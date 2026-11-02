package com.agriaudit.privacy.infrastructure.web;

import com.agriaudit.privacy.PersonalDataInventory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Dónde guarda la aplicación datos de personas (solo administración). */
@RestController
@RequestMapping("/api/organization/privacy/inventory")
@PreAuthorize("hasRole('ADMIN')")
public class PersonalDataInventoryController {

    @GetMapping
    public List<PersonalDataInventory.Item> inventory() {
        return PersonalDataInventory.items();
    }
}
