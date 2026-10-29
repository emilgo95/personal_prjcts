package com.agriaudit.trace;

import java.util.Collection;
import java.util.Map;

/**
 * Puerto: nombres de los usuarios que aparecen en la traza. Lo implementa {@code identity}. La traza solo guarda el
 * código de usuario: el nombre se busca aquí al mostrarla, y un usuario bloqueado aparece como «Usuario bloqueado».
 */
public interface ActorDirectory {

    record ActorName(String username, String fullName) {
    }

    Map<Long, ActorName> names(Collection<Long> userIds);

    /** Códigos de los usuarios de la organización cuyo correo o nombre actual contiene el texto. */
    java.util.Set<Long> userIdsMatching(Long organizationId, String text);
}
