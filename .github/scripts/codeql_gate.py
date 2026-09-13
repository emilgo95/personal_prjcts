#!/usr/bin/env python3
"""Puerta de CodeQL sin code scanning (RT-50).

El repositorio es privado y personal: GitHub no permite activar code scanning, así que los resultados de
CodeQL no se suben a la pestaña Security. El workflow ejecuta la CLI de CodeQL y este script revisa los
ficheros SARIF que genera:

* anota cada hallazgo en el PR (``::error``, ``::warning`` o ``::notice`` con fichero y línea);
* escribe una tabla en el resumen del job;
* termina con código 1 si hay algún hallazgo bloqueante: severidad de seguridad >= umbral (7.0: alta o
  crítica) o, si la regla no la declara, nivel ``error``.

Los hallazgos registrados en el fichero de excepciones (regla + fichero, con justificación) no bloquean
mientras no haya pasado su fecha ``reviewBy``.

Uso: codeql_gate.py <directorio-con-sarif> [--threshold 7.0] [--exceptions .github/codeql-exceptions.json]
"""
import argparse
import datetime as dt
import json
import os
import pathlib
import sys


def rules_by_id(run):
    rules = {}
    tool = run.get("tool", {})
    components = [tool.get("driver", {})] + tool.get("extensions", [])
    for component in components:
        for rule in component.get("rules", []) or []:
            rules[rule.get("id")] = rule
    return rules


def severity_label(score):
    if score is None:
        return "—"
    if score >= 9.0:
        return "crítica"
    if score >= 7.0:
        return "alta"
    if score >= 4.0:
        return "media"
    return "baja"


def location(result):
    for loc in result.get("locations", []) or []:
        physical = loc.get("physicalLocation", {})
        uri = physical.get("artifactLocation", {}).get("uri")
        line = physical.get("region", {}).get("startLine")
        if uri:
            return uri, line
    return None, None


def escape(text):
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def load_exceptions(path, today):
    if not path or not pathlib.Path(path).exists():
        return []
    data = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
    out = []
    for exc in data.get("exceptions", []):
        for key in ("rule", "path", "reason", "guard", "reviewBy"):
            if not exc.get(key):
                raise SystemExit(f"::error::Excepción de CodeQL incompleta (falta '{key}'): {exc}")
        exc["expired"] = dt.date.fromisoformat(exc["reviewBy"]) < today
        out.append(exc)
    return out


def matching_exception(exceptions, rule_id, uri):
    for exc in exceptions:
        if exc["rule"] == rule_id and uri is not None and uri.replace("\\", "/").endswith(exc["path"]):
            return exc
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("folder")
    parser.add_argument("--threshold", type=float, default=7.0)
    parser.add_argument("--exceptions")
    args = parser.parse_args()

    folder = pathlib.Path(args.folder)
    files = sorted(folder.glob("*.sarif"))
    if not files:
        print(f"::error::No hay ficheros SARIF en {folder}: el análisis de CodeQL no ha generado resultados")
        return 1
    exceptions = load_exceptions(args.exceptions, dt.date.today())

    findings = []
    for sarif in files:
        data = json.loads(sarif.read_text(encoding="utf-8"))
        for run in data.get("runs", []):
            rules = rules_by_id(run)
            for result in run.get("results", []) or []:
                rule_id = result.get("ruleId")
                rule = rules.get(rule_id, {})
                props = rule.get("properties", {}) or {}
                raw = props.get("security-severity")
                score = float(raw) if raw not in (None, "") else None
                level = result.get("level") or rule.get("defaultConfiguration", {}).get("level", "warning")
                severe = score >= args.threshold if score is not None else level == "error"
                uri, line = location(result)
                exc = matching_exception(exceptions, rule_id, uri)
                if severe and exc and not exc["expired"]:
                    status = "aceptado"
                elif severe:
                    status = "bloquea"
                else:
                    status = "aviso"
                message = result.get("message", {}).get("text", "").strip()
                findings.append({"status": status, "score": score, "level": level, "rule": rule_id,
                                 "uri": uri, "line": line, "message": message, "exception": exc})

    order = {"bloquea": 0, "aviso": 1, "aceptado": 2}
    findings.sort(key=lambda f: (order[f["status"]], -(f["score"] or 0)))
    blocking = [f for f in findings if f["status"] == "bloquea"]

    for f in findings:
        kind = {"bloquea": "error", "aviso": "warning", "aceptado": "notice"}[f["status"]]
        where = f" file={f['uri']},line={f['line']}" if f["uri"] else ""
        text = f"CodeQL {f['rule']} (severidad {severity_label(f['score'])}): {f['message']}"
        if f["status"] == "aceptado":
            text += f" — Excepción registrada hasta {f['exception']['reviewBy']}: {f['exception']['reason']}"
        elif f["exception"] and f["exception"]["expired"]:
            text += f" — La excepción caducó el {f['exception']['reviewBy']}: revísala."
        print(f"::{kind}{where}::{escape(text)}")

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as out:
            out.write(f"### CodeQL · {', '.join(p.name for p in files)}\n\n")
            if not findings:
                out.write("Sin hallazgos.\n")
            else:
                accepted = sum(1 for f in findings if f["status"] == "aceptado")
                out.write(f"{len(findings)} hallazgos · {len(blocking)} bloqueantes · {accepted} aceptados "
                          f"(umbral de severidad {args.threshold}).\n\n")
                out.write("| Estado | Severidad | Regla | Ubicación | Mensaje |\n|---|---|---|---|---|\n")
                for f in findings:
                    where = f"{f['uri']}:{f['line']}" if f["uri"] else "—"
                    short = f["message"].replace("|", "\\|").replace("\n", " ")[:160]
                    sev = f["score"] if f["score"] is not None else f["level"]
                    out.write(f"| {f['status']} | {severity_label(f['score'])} ({sev}) | `{f['rule']}` "
                              f"| `{where}` | {short} |\n")

    print(f"CodeQL: {len(findings)} hallazgos, {len(blocking)} bloqueantes (umbral {args.threshold}).")
    return 1 if blocking else 0


if __name__ == "__main__":
    sys.exit(main())
