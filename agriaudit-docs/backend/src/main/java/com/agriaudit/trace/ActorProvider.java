package com.agriaudit.trace;

/** Puerto: el usuario autenticado. Lo implementa {@code identity} (la dependencia va hacia {@code trace}). */
public interface ActorProvider {

    /** Usuario autenticado; lanza una excepción si no lo hay. */
    TraceActor current();
}
