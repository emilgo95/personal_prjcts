package com.agriaudit.trace.infrastructure.persistence;

import com.agriaudit.trace.TraceDtos.TraceFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Acceso JDBC al esquema {@code trace}: solo altas y lecturas (la base de datos impide modificar o borrar). */
@Repository
public class TraceRepository {

    public record Head(long lastSeq, String lastHash) {
    }

    public record SealRow(Long id, long organizationId, Instant sealedAt, long lastSeq, String lastHash,
                          String signature) {
    }

    private static final String COLUMNS = "id, organization_id, seq, occurred_at, created_at, user_id, username, "
            + "user_full_name, user_role, entity_type, entity_id, entity_ref, parent_type, parent_id, audit_id, "
            + "action, summary, changes, prev_hash, hash, signature";

    private static final RowMapper<StoredEntry> ENTRY = (rs, i) -> new StoredEntry(
            rs.getLong("id"), rs.getLong("organization_id"), rs.getLong("seq"),
            rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            (Long) rs.getObject("user_id"), rs.getString("username"), rs.getString("user_full_name"),
            rs.getString("user_role"), rs.getString("entity_type"), (Long) rs.getObject("entity_id"),
            rs.getString("entity_ref"), rs.getString("parent_type"), (Long) rs.getObject("parent_id"),
            (Long) rs.getObject("audit_id"), rs.getString("action"), rs.getString("summary"),
            rs.getString("changes"), rs.getString("prev_hash"), rs.getString("hash"), rs.getString("signature"));

    private static final RowMapper<SealRow> SEAL = (rs, i) -> new SealRow(
            rs.getLong("id"), rs.getLong("organization_id"),
            rs.getObject("sealed_at", OffsetDateTime.class).toInstant(), rs.getLong("last_seq"),
            rs.getString("last_hash"), rs.getString("signature"));

    private final JdbcTemplate jdbc;

    public TraceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Bloquea (y crea si hace falta) la cabeza de la cadena de la organización y la devuelve. */
    public Head lockHead(long organizationId, String genesisHash) {
        jdbc.update("INSERT INTO trace.chain_head (organization_id, last_seq, last_hash, updated_at) "
                + "VALUES (?, 0, ?, now()) ON CONFLICT (organization_id) DO NOTHING", organizationId, genesisHash);
        return jdbc.queryForObject("SELECT last_seq, last_hash FROM trace.chain_head WHERE organization_id = ? "
                        + "FOR UPDATE", (rs, i) -> new Head(rs.getLong(1), rs.getString(2).trim()), organizationId);
    }

    public void insert(StoredEntry e) {
        jdbc.update("INSERT INTO trace.change_log (organization_id, seq, occurred_at, created_at, user_id, username, "
                        + "user_full_name, user_role, entity_type, entity_id, entity_ref, parent_type, parent_id, "
                        + "audit_id, action, summary, changes, prev_hash, hash, signature) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                e.organizationId(), e.seq(), Timestamp.from(e.occurredAt()), Timestamp.from(e.createdAt()),
                e.userId(), e.username(), e.userFullName(), e.userRole(), e.entityType(), e.entityId(),
                e.entityRef(), e.parentType(), e.parentId(), e.auditId(), e.action(), e.summary(), e.changes(),
                e.prevHash(), e.hash(), e.signature());
        jdbc.update("UPDATE trace.chain_head SET last_seq = ?, last_hash = ?, updated_at = now() "
                + "WHERE organization_id = ?", e.seq(), e.hash(), e.organizationId());
    }

    public Optional<Head> head(long organizationId) {
        return jdbc.query("SELECT last_seq, last_hash FROM trace.chain_head WHERE organization_id = ?",
                (rs, i) -> new Head(rs.getLong(1), rs.getString(2).trim()), organizationId).stream().findFirst();
    }

    public List<Long> organizationsWithEntries() {
        return jdbc.queryForList("SELECT organization_id FROM trace.chain_head WHERE last_seq > 0", Long.class);
    }

    /** Siguiente tanda de entradas en orden de cadena (para verificar o exportar sin cargar todo en memoria). */
    public List<StoredEntry> chainAfter(long organizationId, long afterSeq, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM trace.change_log WHERE organization_id = ? AND seq > ? "
                + "ORDER BY seq LIMIT ?", ENTRY, organizationId, afterSeq, limit);
    }

    public Optional<StoredEntry> bySeq(long organizationId, long seq) {
        return jdbc.query("SELECT " + COLUMNS + " FROM trace.change_log WHERE organization_id = ? AND seq = ?",
                ENTRY, organizationId, seq).stream().findFirst();
    }

    /**
     * Consulta paginada, de la más reciente a la más antigua. {@code visibleAudits} limita a esas auditorías
     * (auditora); {@code null} = toda la organización.
     */
    public List<StoredEntry> search(long organizationId, TraceFilter f, Collection<Long> visibleAudits,
                                    boolean withParentOfFinding, int offset, int limit) {
        List<Object> args = new ArrayList<>();
        String where = where(organizationId, f, visibleAudits, withParentOfFinding, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query("SELECT " + COLUMNS + " FROM trace.change_log WHERE " + where
                + " ORDER BY seq DESC LIMIT ? OFFSET ?", ENTRY, args.toArray());
    }

    public long count(long organizationId, TraceFilter f, Collection<Long> visibleAudits,
                      boolean withParentOfFinding) {
        List<Object> args = new ArrayList<>();
        String where = where(organizationId, f, visibleAudits, withParentOfFinding, args);
        Long n = jdbc.queryForObject("SELECT count(*) FROM trace.change_log WHERE " + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    private static String where(long organizationId, TraceFilter f, Collection<Long> visibleAudits,
                                boolean withParentOfFinding, List<Object> args) {
        StringBuilder sb = new StringBuilder("organization_id = ?");
        args.add(organizationId);
        if (visibleAudits != null) {
            if (visibleAudits.isEmpty()) {
                sb.append(" AND FALSE");
            } else {
                sb.append(" AND audit_id IN (").append(String.join(",", java.util.Collections.nCopies(
                        visibleAudits.size(), "?"))).append(")");
                args.addAll(visibleAudits);
            }
        }
        if (f.auditId() != null) {
            sb.append(" AND audit_id = ?");
            args.add(f.auditId());
        }
        if (f.entityType() != null && f.entityId() != null && withParentOfFinding) {
            // Historial de una NC: sus propias entradas y las de sus acciones correctivas y adjuntos.
            sb.append(" AND ((entity_type = ? AND entity_id = ?) OR (parent_type = ? AND parent_id = ?))");
            args.add(f.entityType());
            args.add(f.entityId());
            args.add(f.entityType());
            args.add(f.entityId());
        } else {
            if (f.entityType() != null) {
                sb.append(" AND entity_type = ?");
                args.add(f.entityType());
            }
            if (f.entityId() != null) {
                sb.append(" AND entity_id = ?");
                args.add(f.entityId());
            }
        }
        if (f.action() != null) {
            sb.append(" AND action = ?");
            args.add(f.action());
        }
        if (f.username() != null && !f.username().isBlank()) {
            sb.append(" AND (lower(username) LIKE ? OR lower(coalesce(user_full_name, '')) LIKE ?)");
            String like = "%" + f.username().trim().toLowerCase() + "%";
            args.add(like);
            args.add(like);
        }
        if (f.from() != null) {
            sb.append(" AND occurred_at >= ?");
            args.add(Timestamp.from(f.from()));
        }
        if (f.to() != null) {
            sb.append(" AND occurred_at < ?");
            args.add(Timestamp.from(f.to()));
        }
        return sb.toString();
    }

    // ---- sellos ----

    public void insertSeal(SealRow s) {
        jdbc.update("INSERT INTO trace.seal (organization_id, sealed_at, last_seq, last_hash, signature) "
                        + "VALUES (?,?,?,?,?) ON CONFLICT (organization_id, last_seq) DO NOTHING",
                s.organizationId(), Timestamp.from(s.sealedAt()), s.lastSeq(), s.lastHash(), s.signature());
    }

    public List<SealRow> seals(long organizationId) {
        return jdbc.query("SELECT id, organization_id, sealed_at, last_seq, last_hash, signature FROM trace.seal "
                + "WHERE organization_id = ? ORDER BY last_seq DESC", SEAL, organizationId);
    }

    public Optional<SealRow> latestSeal(long organizationId) {
        return jdbc.query("SELECT id, organization_id, sealed_at, last_seq, last_hash, signature FROM trace.seal "
                + "WHERE organization_id = ? ORDER BY last_seq DESC LIMIT 1", SEAL, organizationId)
                .stream().findFirst();
    }
}
