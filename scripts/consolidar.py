#!/usr/bin/env python3
"""Consolida los JSON exportados por CoverScan (varios teléfonos) en una sola base.

- Fusiona los documentos de todos los archivos (por id: el primero gana).
- Avisa de posibles duplicados (mismo título y primer autor, sin tildes).
- Puede escribir un CSV para revisar en Excel y una base SQLite lista para la BD.

Uso:
    python3 scripts/consolidar.py escaneos/*.json --csv datos.csv --sqlite biblioteca.db

Solo usa la biblioteca estándar de Python 3 (no hay que instalar nada).
"""
import argparse
import csv
import json
import sqlite3
import sys
import unicodedata
from pathlib import Path

COLUMNAS_CSV = [
    "id", "categoria", "tipo_trabajo", "titulo", "autores", "tutores", "institucion",
    "facultad_area", "fecha_lugar", "otros", "campos_extra", "notas", "escaneado_en",
]

ESQUEMA_SQL = """
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS documentos (
    id TEXT PRIMARY KEY,
    categoria TEXT,
    tipo_trabajo TEXT,
    titulo TEXT,
    institucion TEXT,
    facultad_area TEXT,
    fecha_lugar TEXT,
    notas TEXT,
    escaneado_en TEXT,
    texto_completo TEXT
);
CREATE TABLE IF NOT EXISTS autores (
    documento_id TEXT NOT NULL REFERENCES documentos(id) ON DELETE CASCADE,
    orden INTEGER NOT NULL,
    nombre TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS tutores (
    documento_id TEXT NOT NULL REFERENCES documentos(id) ON DELETE CASCADE,
    orden INTEGER NOT NULL,
    rol TEXT,
    nombre TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS campos_extra (
    documento_id TEXT NOT NULL REFERENCES documentos(id) ON DELETE CASCADE,
    clave TEXT NOT NULL,
    valor TEXT
);
"""


def normalizar(texto):
    """Minúsculas, sin tildes y con espacios simples, para comparar textos."""
    sin_tildes = "".join(
        c for c in unicodedata.normalize("NFD", texto or "") if not unicodedata.combining(c)
    )
    return " ".join(sin_tildes.lower().split())


def clave_duplicado(doc):
    """Posible mismo documento: (título normalizado, primer autor normalizado)."""
    autores = doc.get("autores") or []
    return (normalizar(doc.get("titulo")), normalizar(autores[0] if autores else ""))


def leer_archivo(ruta):
    datos = json.loads(Path(ruta).read_text(encoding="utf-8"))
    if not isinstance(datos, dict) or "documentos" not in datos:
        raise ValueError("no tiene la clave 'documentos' (¿es un export completo de CoverScan?)")
    docs = datos.get("documentos")
    if not isinstance(docs, list):
        raise ValueError("'documentos' no es una lista")
    return [d for d in docs if isinstance(d, dict)]


def separar_rol(valor):
    """'Asesor: Juan Pérez' → ('Asesor', 'Juan Pérez'); 'Juan Pérez' → ('', 'Juan Pérez')."""
    if ":" in valor:
        rol, nombre = valor.split(":", 1)
        rol, nombre = rol.strip(), nombre.strip()
        if rol and nombre:
            return rol, nombre
    return "", valor.strip()


def escribir_csv(docs, ruta):
    with open(ruta, "w", newline="", encoding="utf-8-sig") as f:
        escritor = csv.DictWriter(f, fieldnames=COLUMNAS_CSV)
        escritor.writeheader()
        for d in docs:
            escritor.writerow({
                "id": d.get("id", ""),
                "categoria": d.get("categoria") or "",
                "tipo_trabajo": d.get("tipo_trabajo") or "",
                "titulo": (d.get("titulo") or "").replace("\n", " "),
                "autores": "; ".join(d.get("autores") or []),
                "tutores": "; ".join(d.get("tutores") or []),
                "institucion": (d.get("institucion") or "").replace("\n", " "),
                "facultad_area": (d.get("facultad_area") or "").replace("\n", " "),
                "fecha_lugar": (d.get("fecha_lugar") or "").replace("\n", " "),
                "otros": " | ".join(d.get("otros") or []).replace("\n", " "),
                "campos_extra": "; ".join(f"{k}={v}" for k, v in (d.get("campos_extra") or {}).items()),
                "notas": (d.get("notas") or "").replace("\n", " "),
                "escaneado_en": d.get("escaneado_en") or "",
            })


def escribir_sqlite(docs, ruta):
    con = sqlite3.connect(ruta)
    try:
        con.executescript(ESQUEMA_SQL)
        for d in docs:
            ident = d.get("id")
            con.execute(
                "INSERT OR REPLACE INTO documentos VALUES (?,?,?,?,?,?,?,?,?,?)",
                (ident, d.get("categoria"), d.get("tipo_trabajo"), d.get("titulo"),
                 d.get("institucion"), d.get("facultad_area"), d.get("fecha_lugar"),
                 d.get("notas"), d.get("escaneado_en"), d.get("texto_completo")),
            )
            # Se reemplazan las filas hijas para que reimportar un id no duplique listas
            con.execute("DELETE FROM autores WHERE documento_id = ?", (ident,))
            con.execute("DELETE FROM tutores WHERE documento_id = ?", (ident,))
            con.execute("DELETE FROM campos_extra WHERE documento_id = ?", (ident,))
            for i, nombre in enumerate(d.get("autores") or []):
                con.execute("INSERT INTO autores VALUES (?,?,?)", (ident, i, nombre))
            for i, valor in enumerate(d.get("tutores") or []):
                rol, nombre = separar_rol(str(valor))
                con.execute("INSERT INTO tutores VALUES (?,?,?,?)", (ident, i, rol, nombre))
            for clave, valor in (d.get("campos_extra") or {}).items():
                con.execute("INSERT INTO campos_extra VALUES (?,?,?)", (ident, clave, valor))
        con.commit()
    finally:
        con.close()


def consolidar(rutas, eliminar_duplicados=False):
    por_id = {}
    por_clave = {}
    duplicados = []
    for ruta in rutas:
        try:
            docs = leer_archivo(ruta)
        except (OSError, ValueError, json.JSONDecodeError) as e:
            print(f"[aviso] {ruta}: {e}", file=sys.stderr)
            continue
        nuevos = 0
        for d in docs:
            ident = d.get("id")
            if ident in por_id:
                continue
            por_id[ident] = d
            nuevos += 1
            clave = clave_duplicado(d)
            if clave[0]:
                if clave in por_clave:
                    duplicados.append((d, por_clave[clave]))
                else:
                    por_clave[clave] = d
        print(f"{ruta}: {len(docs)} documentos ({nuevos} nuevos, {len(docs) - nuevos} ya estaban)")
    docs = sorted(por_id.values(), key=lambda d: (d.get("escaneado_en") or ""))
    descartados = 0
    if eliminar_duplicados:
        vistos = set()
        filtrados = []
        for d in docs:
            clave = clave_duplicado(d)
            if clave[0] and clave in vistos:
                descartados += 1
                continue
            if clave[0]:
                vistos.add(clave)
            filtrados.append(d)
        docs = filtrados
    return docs, duplicados, descartados


def main():
    parser = argparse.ArgumentParser(
        description="Consolida los JSON de CoverScan en una sola base (CSV / SQLite).",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("archivos", nargs="+", help="archivos JSON exportados por CoverScan")
    parser.add_argument("--csv", help="escribir además un CSV para revisar en Excel")
    parser.add_argument("--sqlite", help="escribir además una base SQLite")
    parser.add_argument("--eliminar-duplicados", action="store_true",
                        help="descarta copias repetidas (mismo título y primer autor; conserva la más antigua)")
    args = parser.parse_args()

    docs, duplicados, descartados = consolidar(args.archivos, args.eliminar_duplicados)

    print(f"\nTotal consolidado: {len(docs)} documentos")
    if duplicados:
        print(f"Posibles duplicados (mismo título y primer autor): {len(duplicados)}"
              + (f", {descartados} descartados" if args.eliminar_duplicados else " (revisar o usar --eliminar-duplicados)"))
        for d, _ in duplicados[:10]:
            print(f"  - {d.get('titulo')!r}  ({d.get('escaneado_en')})")
        if len(duplicados) > 10:
            print(f"  … y {len(duplicados) - 10} más")
    if args.csv:
        escribir_csv(docs, args.csv)
        print(f"CSV escrito: {args.csv}")
    if args.sqlite:
        escribir_sqlite(docs, args.sqlite)
        print(f"SQLite escrito: {args.sqlite}")


if __name__ == "__main__":
    main()
