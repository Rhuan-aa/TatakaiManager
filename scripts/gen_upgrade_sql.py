#!/usr/bin/env python3
"""Generates an idempotent upgrade script (scripts/prod_upgrade.sql) from a schema-only pg_dump.

Production runs with ddl-auto=validate, so it never creates tables or columns on its own.
To get the dump of the current entities:
  1. create an empty database (e.g. `schemagen`) on a dev Postgres;
  2. boot the backend jar against it with DDL_AUTO=create (and a throwaway MONGO_URI db);
  3. pg_dump -U postgres --schema-only --no-owner --no-privileges schemagen > gen.sql
Then: python3 scripts/gen_upgrade_sql.py gen.sql > scripts/prod_upgrade.sql

The output can run on any older database (or twice): it creates missing tables, adds missing
columns, drops NOT NULL where the entity allows null, drops every CHECK (Hibernate's enum CHECKs
never update when an enum gains a value, see AGENTS.md §11.11) and adds missing keys/indexes.
"""
import re
import sys

dump = open(sys.argv[1], encoding="utf-8").read()
out = ["-- Gerado por scripts/gen_upgrade_sql.py. Idempotente: pode rodar mais de uma vez.",
       "BEGIN;", "SET client_min_messages = warning;", "",
       "-- CHECKs antigos (enum de Hibernate, faixa de slot): o app valida os valores.",
       """DO $$ DECLARE r record; BEGIN
  FOR r IN SELECT conrelid::regclass AS t, conname FROM pg_constraint
           WHERE contype = 'c' AND connamespace = 'public'::regnamespace LOOP
    EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.t, r.conname);
  END LOOP;
END $$;""", "",
       "-- Várias fichas por jogador (§52): sai o unique de character_sheets.campaign_member_id.",
       """DO $$ DECLARE r record; BEGIN
  FOR r IN SELECT c.conname FROM pg_constraint c
           JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
           WHERE c.contype = 'u' AND c.conrelid = to_regclass('public.character_sheets')
             AND array_length(c.conkey, 1) = 1 AND a.attname = 'campaign_member_id' LOOP
    EXECUTE format('ALTER TABLE public.character_sheets DROP CONSTRAINT %I', r.conname);
  END LOOP;
END $$;""", ""]

for name, body in re.findall(r"CREATE TABLE (public\.\w+) \((.*?)\n\);", dump, re.S):
    cols = [l.strip().rstrip(",") for l in body.split("\n")
            if l.strip() and not l.strip().startswith("CONSTRAINT")]
    out.append(f"CREATE TABLE IF NOT EXISTS {name} (\n    " + ",\n    ".join(cols) + "\n);")
    for col in cols:
        cname, rest = col.split(" ", 1)
        not_null = rest.endswith(" NOT NULL")
        typ = rest[: -len(" NOT NULL")] if not_null else rest
        # ADD COLUMN NOT NULL without a default fails on a table that already has rows.
        keep = " NOT NULL" if not_null and " DEFAULT " in typ else ""
        out.append(f"ALTER TABLE {name} ADD COLUMN IF NOT EXISTS {cname} {typ}{keep};")
        if not not_null:
            out.append(f"ALTER TABLE {name} ALTER COLUMN {cname} DROP NOT NULL;")
    out.append("")

out.append("-- Chaves e índices que faltarem (falha de dados só avisa, não aborta).")
for table, cname, kind, rest in re.findall(
        r"ALTER TABLE ONLY (public\.\w+)\n    ADD CONSTRAINT (\w+) (PRIMARY KEY|UNIQUE|FOREIGN KEY) (.*?);", dump):
    guard = ("SELECT 1 FROM pg_constraint WHERE contype = 'p' AND conrelid = '%s'::regclass" % table
             if kind == "PRIMARY KEY" else
             "SELECT 1 FROM pg_constraint WHERE conname = '%s' AND conrelid = '%s'::regclass" % (cname, table))
    out.append(f"""DO $$ BEGIN
  IF NOT EXISTS ({guard}) THEN
    ALTER TABLE {table} ADD CONSTRAINT {cname} {kind} {rest};
  END IF;
EXCEPTION WHEN others THEN RAISE WARNING '{table} {cname}: %', SQLERRM;
END $$;""")
for stmt in re.findall(r"CREATE (?:UNIQUE )?INDEX .*?;", dump):
    out.append(stmt.replace("INDEX ", "INDEX IF NOT EXISTS ", 1))

out += ["", "COMMIT;"]
print("\n".join(out))
