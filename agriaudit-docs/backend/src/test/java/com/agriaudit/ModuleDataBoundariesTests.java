package com.agriaudit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Higiene de datos entre módulos (RT-02): cada módulo es dueño de sus tablas y nadie navega por las
 * entidades de otro. Complementa a {@link ModularityTests}, que vigila las dependencias de código.
 */
class ModuleDataBoundariesTests {

    static final String ROOT = "com.agriaudit.";

    static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.agriaudit");

    @Test
    void entitiesLiveInTheSchemaOfTheirModule() {
        classes().that().areAnnotatedWith(Entity.class)
                .should(liveInTheSchemaOfTheirModule())
                .because("las tablas se agrupan por módulo en un esquema con su nombre (RT-02, RT-30)")
                .check(classes);
    }

    @Test
    void entitiesDoNotReferenceEntitiesOfOtherModules() {
        classes().that().areAnnotatedWith(Entity.class)
                .should(notReferenceEntitiesOfOtherModules())
                .because("entre módulos se guarda el identificador y se consulta la API del módulo dueño (RT-02)")
                .check(classes);
    }

    static String moduleOf(JavaClass type) {
        String name = type.getPackageName();
        if (!name.startsWith(ROOT)) {
            return null;
        }
        String rest = name.substring(ROOT.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    static ArchCondition<JavaClass> liveInTheSchemaOfTheirModule() {
        return new ArchCondition<>("declare @Table(schema = <módulo>)") {
            @Override
            public void check(JavaClass entity, ConditionEvents events) {
                String module = moduleOf(entity);
                String schema = entity.tryGetAnnotationOfType(Table.class).map(Table::schema).orElse("");
                boolean ok = schema.equals(module);
                events.add(new SimpleConditionEvent(entity, ok, String.format(
                        "%s está en el esquema '%s' y su módulo es '%s'", entity.getName(), schema, module)));
                for (JavaField field : entity.getFields()) {
                    field.tryGetAnnotationOfType(JoinTable.class).ifPresent(join -> {
                        boolean joinOk = join.schema().equals(module);
                        events.add(new SimpleConditionEvent(field, joinOk, String.format(
                                "La tabla de unión %s de %s está en el esquema '%s' y su módulo es '%s'",
                                join.name(), field.getFullName(), join.schema(), module)));
                    });
                }
            }
        };
    }

    static ArchCondition<JavaClass> notReferenceEntitiesOfOtherModules() {
        return new ArchCondition<>("no referenciar entidades de otros módulos") {
            @Override
            public void check(JavaClass entity, ConditionEvents events) {
                String module = moduleOf(entity);
                for (JavaField field : entity.getFields()) {
                    for (JavaClass target : referencedTypes(field.getType())) {
                        if (target.isAnnotatedWith(Entity.class) && !module.equals(moduleOf(target))) {
                            events.add(SimpleConditionEvent.violated(field, String.format(
                                    "%s referencia la entidad %s del módulo '%s'",
                                    field.getFullName(), target.getName(), moduleOf(target))));
                        }
                    }
                }
            }
        };
    }

    private static List<JavaClass> referencedTypes(JavaType type) {
        List<JavaClass> out = new ArrayList<>();
        out.add(type.toErasure());
        if (type instanceof JavaParameterizedType parameterized) {
            for (JavaType argument : parameterized.getActualTypeArguments()) {
                out.addAll(referencedTypes(argument));
            }
        }
        return out;
    }
}
