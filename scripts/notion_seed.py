#!/usr/bin/env python3
"""Converte o export do Notion (contexto/) em catalog/traits.json e catalog/arts.json.

Uso (da raiz do repo): python3 scripts/notion_seed.py
Só stdlib; determinístico e idempotente. Ver PLANO_FICHA_DIGITAL.md §12.4.
"""
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TRACOS = ROOT / "contexto/traço/TRAÇOS"
ARTES = ROOT / "contexto/artes marciais/ARTES MARCIAIS/Clássicas"
OUT = ROOT / "backend/src/main/resources/catalog"

RARITIES = [("Comum", "COMMON", 20), ("Raro", "RARE", 20), ("Épico", "EPIC", 20),
            ("Lendário", "LEGENDARY", 20), ("Divino", "DIVINE", 10)]
CATS = {"FISICO": "PHYSICAL", "MENTAL": "MENTAL", "HIBRIDO": "HYBRID", "ESPIRITUAL": "SPIRITUAL"}
ATTRS = {"forca": "STR", "destreza": "DEX", "constituicao": "CON", "inteligencia": "INT",
         "carisma": "CHA", "mental": "MEN", "talento": "TAL"}
ALL_ATTRS = ["STR", "DEX", "CON", "INT", "CHA", "MEN", "TAL"]
PHYS = ["STR", "DEX", "CON"]
GRADS = {"iniciante": "BEGINNER", "intermediario": "INTERMEDIATE", "mestre": "MASTER"}

unclassified = []  # reservado: blocos que não viraram base/técnica/passiva


# ---------- limpeza de texto ----------
def noacc(s):
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn").lower()


def light(line):
    """Remove só filler e <u>; mantém ** para detectar regras."""
    return re.sub(r"</?u>", "", line.replace("ㅤ", "")).strip()


def keep(line):
    """Como light(), mas preserva a indentação (aninhamento de bullets)."""
    return re.sub(r"</?u>", "", line.replace("ㅤ", "")).rstrip()


def clean(s):
    s = re.sub(r"<img[^>]*>", "", s)
    s = re.sub(r"</?(aside|u)>", "", s)
    s = re.sub(r"!?\[([^\]]*)\]\((?:[^()]|\([^()]*\))*\)", r"\1", s)
    s = s.replace("ㅤ", "").replace(" ", " ")
    s = re.sub(r"[*`]", "", s)
    return re.sub(r"[ \t]+", " ", s).strip()


SEP_CELL = re.compile(r"^:?-{3,}:?$")


def clean_line(ln):
    """Uma linha crua -> texto limpo preservando estrutura: bullets viram '- ' (2 espaços por nível,
    o Notion exporta 4) e linhas de tabela viram '| a | b |' (separador '|---|---|')."""
    ln = re.sub(r"^\s*>\s?", "", ln.replace("ㅤ", "").replace("\t", "    "))
    s = ln.strip()
    if s.startswith("|") and s.endswith("|") and len(s) > 1:
        cells = [c.strip() for c in s[1:-1].split("|")]
        if all(SEP_CELL.match(c) for c in cells):
            return "|" + "|".join("---" for _ in cells) + "|"
        return "| " + " | ".join(clean(c) for c in cells) + " |"
    m = re.match(r"^( *)[-*+]\s+(\S.*)$", ln)
    if m:
        return "  " * (len(m.group(1)) // 4) + "- " + clean(m.group(2))
    return clean(ln)


def clean_block(lines):
    """Linhas cruas -> texto limpo, sem '>' de citação, '---', e sem brancos repetidos."""
    out = []
    for ln in lines:
        if re.sub(r"^\s*>\s?", "", ln).strip() == "---":
            continue
        c = clean_line(ln)
        if not c and (not out or not out[-1]):
            continue
        out.append(c)
    while out and not out[-1]:
        out.pop()
    return "\n".join(out)


def nul(s):
    if s is None:
        return None
    s = s.strip()
    return None if re.fullmatch(r"[—\-–?\s]*", s) else s


def first_int(s):
    m = re.search(r"\d+", s or "")
    return int(m.group()) if m else None


def strip_hash(name):
    return re.sub(r"\s+[0-9a-f]{32}$", "", name)


def read(p):
    return p.read_text(encoding="utf-8")


def headings(text):
    """Divide em [(titulo_cru|None, [linhas])] por linhas que começam com #."""
    secs, cur = [], (None, [])
    for ln in text.splitlines():
        if re.match(r"^#{1,6}\s", ln):
            secs.append(cur)
            cur = (ln, [])
        else:
            cur[1].append(ln)
    secs.append(cur)
    return secs


def htitle(raw):
    return noacc(clean(re.sub(r"^#+\s*", "", raw or "")))


# ---------- traços ----------
def parse_index(rarity_dir_name):
    """-> {nome_norm: [ordem, categoria|None]} na ordem do índice."""
    f = next(TRACOS.glob(f"{rarity_dir_name} *.md"))
    order, cat, seen = 0, None, {}
    for ln in read(f).splitlines():
        if "<aside" in ln:
            cat = None
        m = re.match(r"^#+\s*\**➥\s*(\w+)", ln)
        if m:
            cat = CATS.get(noacc(m.group(1)).upper())
        for t in re.findall(r"\[([^\]]*)\]\(", ln):
            n = noacc(clean(t))
            if not n or "(old)" in n:
                continue
            if n not in seen:
                order += 1
                seen[n] = [order, cat]
            elif seen[n][1] is None and cat:
                seen[n][1] = cat  # categoria = primeira seção rotulada
    return seen


GAIN_RE = re.compile(r"^\+\s*(\d+)\s*(%?)\s*(.*)$")


def apply_gain(line, g):
    """Aplica uma linha '+N ...' em buildGains. True se 100% consumida."""
    m = GAIN_RE.match(line.strip())
    if not m or re.match(r"^,\d", m.group(3)):
        return False
    n, pct, rest = int(m.group(1)), bool(m.group(2)), noacc(m.group(3)).strip(" .;")
    if "limitador" in rest:
        if "fisic" in rest:
            for a in PHYS:
                g["limiters"][a] = g["limiters"].get(a, 0) + n
            return True
        return False
    rest = re.sub(r"^(pontos?\s+)?", "", rest)
    rest = re.sub(r"^(de valor de|do valor de|no valor de|em valor de|para o valor de|no valor|de|em)\s+", "", rest)
    if "(" in rest:
        return False
    ok = True
    for tok in re.split(r"\s*,\s*|\s+e\s+", rest):
        tok = re.sub(r"^(valor de|pontos? de|de|o)\s+", "", tok.strip())
        if tok in ATTRS:
            tgt = [ATTRS[tok]]
        elif tok in ("todos atributos", "todos os atributos"):
            tgt = ALL_ATTRS
        elif tok in ("todos atributos fisicos", "todos os atributos fisicos", "atributos fisicos"):
            tgt = PHYS
        elif not pct and tok in ("hp", "hpmax", "vida"):
            g["maxHp"] += n
            continue
        elif not pct and tok in ("sp", "spmax", "estamina", "stamina points"):
            g["maxSp"] += n
            continue
        elif not pct and tok == "pq":
            g["maxKp"] += n
            continue
        else:
            ok = False
            continue
        key = "attributePercent" if pct else "attributeFlat"
        for a in tgt:
            g[key][a] = g[key].get(a, 0) + n
    return ok


TITLE_RE = re.compile(r"^([^:\n.\-|\s][^:\n.]{1,49}):\s+(\S.*)$")


def split_unnamed(effects):
    """Efeitos sem nome (formato antigo): linhas 'Título: texto' viram efeitos nomeados."""
    out = []
    for n, t in effects:
        if n:
            out.append({"name": n, "text": t})
            continue
        cur = None
        for ln in t.split("\n"):
            m = TITLE_RE.match(ln)
            if m:
                cur = {"name": m.group(1).replace("**", "").strip(), "text": m.group(2)}
                out.append(cur)
            elif cur is not None:
                cur["text"] += "\n" + ln
            else:
                cur = {"name": None, "text": ln}
                out.append(cur)
    return out


def parse_trait(path, enum, variations, gains=True):
    name = strip_hash(path.stem)
    desc_l, gain_l, stat_l, pre = [], [], [], []
    mode = "pre"
    for raw, lines in headings(read(path)):
        t = htitle(raw)
        if raw and raw.startswith("# ") and t not in ("descricao", "ganhos", "estatisticas"):
            mode = "pre"  # título do traço
            continue
        if t == "descricao":
            mode = "d"
        elif t == "ganhos":
            mode = "g"
        elif t == "estatisticas":
            mode = "s"
        {"pre": pre, "d": desc_l, "g": gain_l, "s": stat_l}[mode].extend(lines)
    description = nul(clean_block(desc_l if desc_l else pre))

    g = {"attributeFlat": {}, "attributePercent": {}, "maxHp": 0, "maxSp": 0, "maxKp": 0,
         "limiters": {}, "freePoints": 0}
    effects = []
    # blocos "➥ Título"; linhas soltas (formato antigo) ficam em título None
    blocks, cur = [], (None, [])
    for ln in gain_l:
        lt = light(ln)
        if "➥" in lt[:6]:
            blocks.append(cur)
            cur = (clean(lt.split("➥", 1)[1]), [])
        else:
            cur[1].append(ln)
    blocks.append(cur)
    var_norm = {noacc(v) for v in variations}
    ag = apply_gain if gains else (lambda b, g: False)
    for title, lines in blocks:
        if title is None:
            for ln in lines:
                b = clean_line(ln)
                if not b or b == "---" or noacc(b) in var_norm or ag(b, g):
                    continue
                if b.lstrip().startswith("-") and effects:
                    effects[-1][1] += "\n" + b
                else:
                    effects.append([None, b])
            continue
        body = clean_block(lines)
        name_e = title.rstrip(": ").strip()
        if noacc(name_e) == "pontos":
            left = [b for b in body.splitlines() if b.strip() and not ag(b.strip(), g)]
            if left:
                effects.append(["Pontos", "\n".join(left)])
        else:
            effects.append([name_e, body])

    st = {}
    for ln in stat_l:
        m = re.match(r"^([^:]+):\s*(.*)$", clean(ln))
        if m:
            st[noacc(m.group(1))] = m.group(2).strip()
    yn = lambda v: bool(v) and noacc(v).startswith("sim")
    tipo = noacc(st.get("tipo", ""))
    act = "PASSIVE" if tipo.startswith("passiv") else "ACTIVE" if tipo.startswith("ativ") else None
    dev_req = next((nul(v) for k, v in st.items() if k.startswith("requisitos para")), None)
    return {
        "name": name, "rarity": enum, "category": None, "rollNumber": None,
        "description": description,
        "effects": split_unnamed(effects),
        "buildGains": g,
        "developable": yn(st.get("desenvolvivel")),
        "upgradable": yn(st.get("aprimoravel")),
        "activation": act,
        "requirements": nul(st.get("requisitos")),
        "developmentRequirements": dev_req,
    }


def build_traits():
    traits, stats = [], {}
    for rname, enum, maxroll in RARITIES:
        idx = parse_index(rname)
        d = TRACOS / rname
        # variações = .md diretos de subpastas, agrupados pelo nome da pasta; a pasta sem sufixo " 12ee-xxxx"
        # (versão nova do Notion) vence a pasta com sufixo (antiga)
        var = {}
        for sub in sorted(p for p in d.iterdir() if p.is_dir()):  # sem sufixo ordena antes do com sufixo
            key = noacc(re.sub(r"\s+[0-9a-f]{4}-[0-9a-f]{4}$", "", sub.name))
            var.setdefault(key, sorted(sub.glob("*.md")))
        # dedupe por nome (sem acento/caixa): vence o id Notion maior (mais novo); ignora "(Old)"
        best = {}
        for f in sorted(d.glob("*.md")):
            n = strip_hash(f.stem)
            if "(old)" in n.lower():
                continue
            k = noacc(n)
            h = f.stem[-32:]
            if k not in best or h > best[k][0]:
                best[k] = (h, f)
        rows = []
        s = stats[rname] = {"n": 0, "noroll": [], "noidx": []}
        for k, (_, f) in sorted(best.items()):
            vfiles = var.get(k, [])
            vnames = [strip_hash(v.stem) for v in vfiles]
            t = parse_trait(f, enum, vnames)
            t["variations"] = sorted(
                ({"name": strip_hash(v.stem), "description": vt["description"], "effects": vt["effects"],
                  "requirements": vt["requirements"]}
                 for v in vfiles for vt in [parse_trait(v, enum, [], gains=False)]),
                key=lambda x: noacc(x["name"]))
            order, cat = idx.get(k, (None, None))
            t["category"] = cat or "HYBRID"
            if k not in idx:
                s["noidx"].append(t["name"])
            rows.append(t)
        rows.sort(key=lambda t: noacc(t["name"]))  # numeração alfabética (§13.3)
        for n, t in enumerate(rows, 1):
            t["rollNumber"] = n if n <= maxroll else None
            if t["rollNumber"] is None:
                s["noroll"].append(t["name"])
        s["n"] = len(rows)
        traits.extend(rows)
    return traits, stats


# ---------- artes ----------
def split_list(s):
    s = nul(clean(s or ""))
    if not s:
        return []
    return [x.strip().rstrip(".") for x in re.split(r"\s*,\s*(?:e\s+)?|\s+e\s+", s) if x.strip()]


def titlecase(s):
    small = {"de", "da", "do", "das", "dos", "e"}
    return " ".join(x if (i and x in small) else x.capitalize() for i, x in enumerate(s.lower().split()))


STAT_RE = re.compile(r"^(?:-\s*)?(função|montagem|complexidade|pontos de treino|gasto de uso|alcance):\s*(.*)$", re.I)
RULE_RE = re.compile(r"^\*\*([^*]{1,80}?):\*\*\s*(.*)$")


def parse_block(title, lines, grad, group):
    """-> ('base'|'tech'|'passive', dict)"""
    desc, rules, stats = [], [], {}
    for ln in lines:
        lt = light(ln)
        if not lt:
            (rules[-1][1] if rules else desc).append("")
            continue
        sm = STAT_RE.match(clean(lt))
        if sm:
            k, v = sm.group(1).lower(), sm.group(2).strip()
            # ponytail: variantes aninhadas (várias linhas "Gasto de Uso") são concatenadas com " / "
            stats[k] = f"{stats[k]} / {v}" if k == "gasto de uso" and k in stats else v
            continue
        rm = RULE_RE.match(lt)
        if rm:
            rules.append((clean(rm.group(1)), [rm.group(2)]))
        elif rules:
            rules[-1][1].append(keep(ln))
        else:
            desc.append(keep(ln))
    description = nul(clean_block(desc))
    rl = [(n, clean_block(t)) for n, t in rules]
    plain, effect, restr, side = [], None, None, None
    for n, t in rl:
        k = noacc(n)
        if k == "efeito":
            effect = nul(t)
        elif k.startswith("restric"):
            restr = nul(t)
        elif k == "lado":
            side = nul(t)
        else:
            plain.append({"name": n, "text": t})
    title = re.sub(r"^Kihon\s*-\s*", "", title)
    grad = grad or "GENERIC"
    common = lambda: {"assembly": nul(clean(stats.get("montagem", ""))),
                      "complexity": first_int(stats.get("complexidade")),
                      "trainingPoints": first_int(stats.get("pontos de treino"))}
    if "função" in stats or (group == "Bases" and "pontos de treino" in stats):
        if side:  # "Lado" só é campo próprio em técnicas
            plain.append({"name": "Lado", "text": side})
        d = {"name": title, "graduation": grad, "description": description, "rules": plain,
             "effect": effect, "restrictions": restr, "function": nul(clean(stats.get("função", "")))}
        d.update(common())
        return "base", d
    if "gasto de uso" in stats or "alcance" in stats:
        sp = nul(re.sub(r"\s*pontos?\s+de\s+SP", "", stats.get("gasto de uso", ""), flags=re.I))
        sp = re.sub(r"\s+", " ", sp) if sp else None
        d = {"name": title, "graduation": grad, "group": group, "description": description, "rules": plain,
             "effect": effect, "restrictions": restr, "range": nul(clean(stats.get("alcance", ""))), "side": side}
        d.update(common())
        d.update({"spCost": sp, "spCostBase": first_int(sp)})
        return "tech", d
    if side:
        plain.append({"name": "Lado", "text": side})
    text = "\n".join(x for x in [description, effect and f"Efeito: {effect}", restr and f"Restrições: {restr}"]
                     + [f"{r['name']}: {r['text']}" for r in plain] if x)
    return "passive", {"name": title, "group": group, "description": nul(text)}


def parse_subpage(path, art):
    fgrad = next((v for k, v in GRADS.items() if k in noacc(path.stem)), None)
    group, grad, block, in_aside, expect_title = None, fgrad, None, False, False

    def flush():
        nonlocal block
        if block:
            kind, d = parse_block(*block)
            art[{"base": "bases", "tech": "techniques", "passive": "passives"}[kind]].append(d)
        block = None

    intro, outside = [], []

    def flush_intro():  # texto solto antes do 1º bloco do aside -> seção da arte
        text = clean_block(intro)
        if text:
            label = strip_hash(path.stem) + (f" ({group})" if group else "")
            art["sections"].append({"name": label, "text": text})
        intro.clear()

    for ln in read(path).splitlines():
        lt = light(ln)
        if "<aside>" in ln:
            flush_intro()
            flush()
            in_aside, group, grad, expect_title = True, None, fgrad, True
            continue
        if "</aside>" in ln:
            flush_intro()
            flush()
            in_aside = False
            continue
        if not in_aside:
            outside.append(ln)
            continue
        if expect_title:
            if not lt:
                continue
            expect_title = False
            if "<img" in ln:
                group = nul(clean(ln))
                continue
        if lt == "---":
            continue
        if re.match(r"^\**\s*➥", lt):
            flush_intro()
            flush()
            block = [clean(lt.split("➥", 1)[1]), [], grad, group]
            continue
        gm = re.fullmatch(r"\*+([^*]+)\*+", lt)
        if "<u>" in ln and gm and noacc(gm.group(1).strip()) in GRADS:
            flush_intro()
            flush()
            grad = GRADS[noacc(gm.group(1).strip())]
            continue
        if block:
            block[1].append(ln)
        else:
            intro.append(ln)
    flush_intro()
    # texto fora dos <aside> (ex.: Fundamentos/Floreios da Capoeira) -> seções, uma por "###"
    for raw, lines in headings("\n".join(outside)):
        text = clean_block(lines)
        if raw and not raw.startswith("# ") and text:
            art["sections"].append({"name": titlecase(clean(re.sub(r"^#+\s*", "", raw))), "text": text})


def build_art(index_path):
    name = strip_hash(index_path.stem)
    art = {"name": name, "category": "CLASSIC", "rank": None, "martialType": None,
           "attackSpecializations": [], "reactionSpecializations": [], "requirement": None,
           "description": None, "sections": [], "passives": [], "bases": [], "techniques": []}
    for raw, lines in headings(read(index_path)):
        if raw is None:
            continue
        t = htitle(raw)
        if t == "descricao":
            art["description"] = nul(clean_block(lines))
        elif t == noacc(name) or t.startswith("bases e tecnicas"):
            continue
        elif t == "estatisticas":
            for ln in lines:
                m = re.match(r"^([^:]+):\s*(.*)$", clean(ln))
                if not m:
                    continue
                k, v = noacc(m.group(1)), m.group(2)
                if k == "rank":
                    art["rank"] = nul(v)
                elif k in ("tipo", "tipo marcial"):
                    art["martialType"] = nul(v)
                elif k.startswith("especializacao de ataque"):
                    art["attackSpecializations"] = split_list(v)
                elif k.startswith("especializacao de reacao"):
                    art["reactionSpecializations"] = split_list(v)
                elif k.startswith("requisito"):
                    art["requirement"] = nul(v)
        else:
            art["sections"].append({"name": titlecase(clean(re.sub(r"^#+\s*", "", raw))),
                                    "text": clean_block(lines)})
    for sub in sorted((index_path.parent / name).glob("*.md"), key=lambda p: noacc(p.name)):
        parse_subpage(sub, art)
    return art


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    traits, tstats = build_traits()
    arts = [build_art(f) for f in sorted(ARTES.glob("*.md"), key=lambda p: noacc(p.name))]
    dump(OUT / "traits.json", traits)
    dump(OUT / "arts.json", arts)
    print(f"traits.json: {len(traits)} traços")
    for r, s in tstats.items():
        print(f"  {r}: {s['n']}"
              + (f" | sem rollNumber (excedem a faixa): {', '.join(s['noroll'])}" if s["noroll"] else "")
              + (f" | fora do índice (HYBRID, sem roll): {', '.join(s['noidx'])}" if s["noidx"] else ""))
    print(f"arts.json: {len(arts)} artes")
    for a in arts:
        print(f"  {a['name']}: bases={len(a['bases'])} técnicas={len(a['techniques'])} "
              f"passivas={len(a['passives'])} seções={len(a['sections'])}")
    print(f"textos soltos não classificados: {len(unclassified)}")
    for u in unclassified:
        print("  -", u)


if __name__ == "__main__":
    main()
