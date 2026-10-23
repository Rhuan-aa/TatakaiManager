#!/usr/bin/env python3
"""Contas e dados de teste (PLANO_FICHA_DIGITAL.md §13.4).

Uso: python3 scripts/dev_fixtures.py [--api http://localhost:8080]
Só stdlib; reexecutável (faz login se a conta existe e pula o que já existe).
Requer o backend no ar com o catálogo global semeado (catalog/traits.json).
"""
import argparse
import json
import sys
import urllib.error
import urllib.request

PASSWORD = "tatakai123"
USERS = [("mestre", "Mestre Kenji"), ("jogador1", "Akira"), ("jogador2", "Bianca"), ("jogador3", "Caio")]
ATTRS = ["STR", "DEX", "CON", "INT", "CHA", "MEN", "TAL"]
API = "http://localhost:8080"


class ApiError(Exception):
    def __init__(self, status, msg):
        super().__init__(f"HTTP {status}: {msg}")
        self.status = status


def call(method, path, body=None, token=None):
    """-> JSON (ou None em 204). Levanta ApiError em status >= 400."""
    req = urllib.request.Request(API + path, method=method,
                                 data=None if body is None else json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"})
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
    except urllib.error.HTTPError as e:
        raw = e.read().decode(errors="replace")
        try:
            raw = json.loads(raw).get("message", raw)
        except ValueError:
            pass
        raise ApiError(e.code, f"{method} {path} -> {raw}") from None
    return json.loads(raw) if raw else None


def soft(status_ok_on, *a, **kw):
    """call() que tolera os status dados (ex.: 409 'já existe'); devolve None nesse caso."""
    try:
        return call(*a, **kw)
    except ApiError as e:
        if e.status in status_ok_on:
            return None
        raise


def login(key, name):
    email = f"{key}@tatakai.dev"
    try:
        r = call("POST", "/auth/register", {"name": name, "email": email, "password": PASSWORD})
    except ApiError as e:
        if e.status != 409:
            raise
        r = call("POST", "/auth/login", {"email": email, "password": PASSWORD})
    return {"id": r["userId"], "token": r["token"], "email": email, "name": name}


def find(items, pred):
    return next((x for x in items if pred(x)), None)


# ---------------------------------------------------------------- Construção Inicial
def drive_build(sid, owner, master, cid, stop_at_attributes=False):
    """Conduz o wizard a partir do passo atual. Para em ATTRIBUTES (dados rolados) ou envia + aprova."""
    base = f"/character-sheets/{sid}/build"
    tok = owner["token"]
    for _ in range(40):
        b = call("GET", base, token=tok)
        if b["buildStatus"] != "IN_BUILD":
            break
        step = b["step"]
        if step == "IDENTITY":
            call("PUT", base + "/identity", {"bodyType": "MESOMORPH", "dominance": "RIGHT", "heightM": 1.75}, tok)
            call("POST", base + "/steps/IDENTITY/confirm", token=tok)
        elif step == "TRAITS":
            if not b["traitSlots"]:
                b = call("POST", base + "/traits/roll", token=tok)
            traits = call("GET", f"/catalog/traits?campaignId={cid}", token=master["token"])
            for slot in b["traitSlots"]:
                if slot.get("traitId"):
                    continue
                used = {s.get("traitId") for s in b["traitSlots"]}
                pick = find(traits, lambda t: t["rarity"] == slot["rarity"] and t["id"] not in used)
                if not pick:
                    sys.exit(f"Catálogo sem traço {slot['rarity']} livre para o slot {slot['index']} "
                             "(o seed global foi carregado?)")
                b = call("POST", f"{base}/traits/slots/{slot['index']}/resolve",
                         {"traitId": pick["id"], "reason": "Traço de reposição (fixture de teste)"},
                         master["token"])
            call("POST", base + "/steps/TRAITS/confirm", token=tok)
        elif step == "BLOOD_TEST":
            if b["bloodTestRoll"] is None:
                b = call("POST", base + "/blood-test/roll", token=tok)
            if len(b["clans"]) != b["clanSlots"]:
                clans = call("GET", f"/catalog/clans?campaignId={cid}", token=tok)
                clans.sort(key=lambda c: (c["campaignId"] is None, c["name"]))  # clãs da campanha primeiro
                ids = [c["id"] for c in clans[:b["clanSlots"]]]
                if len(ids) < b["clanSlots"]:
                    sys.exit("Clãs insuficientes no catálogo para o Teste de Sangue")
                call("PUT", base + "/clans", {"clanIds": ids}, tok)
            call("POST", base + "/steps/BLOOD_TEST/confirm", token=tok)
        elif step == "ATTRIBUTES":
            if not b["attributeDice"]:
                call("POST", base + "/attributes/roll", token=tok)
            if stop_at_attributes:
                return call("GET", base, token=tok)
            call("PUT", base + "/attributes/assignment", {"assignment": {a: i for i, a in enumerate(ATTRS)}}, tok)
            call("POST", base + "/steps/ATTRIBUTES/confirm", token=tok)
        elif step == "FREE_POINTS":
            call("PUT", base + "/free-points", {"freePoints": {}}, tok)  # pontos livres ficam sem gastar
            call("POST", base + "/steps/FREE_POINTS/confirm", token=tok)
        elif step == "REVIEW":
            call("POST", base + "/submit", token=tok)
    sheet = call("GET", f"/character-sheets/{sid}", token=master["token"])
    if sheet["buildStatus"] == "PENDING_APPROVAL":
        sheet = call("POST", f"/character-sheets/{sid}/approve", {"approved": True}, master["token"])
    return sheet


def ensure_build(cid, player, master, stop_at_attributes):
    sheets = call("GET", f"/campaigns/{cid}/character-sheets", token=master["token"])
    s = find(sheets, lambda x: x["ownerUserId"] == player["id"])
    if s is None:
        sid = call("POST", f"/campaigns/{cid}/character-sheets/build", {"name": player["name"]}, player["token"])["sheetId"]
    else:
        sid = s["id"]
    return sid, drive_build(sid, player, master, cid, stop_at_attributes)


# ---------------------------------------------------------------- main
def main():
    global API
    ap = argparse.ArgumentParser()
    ap.add_argument("--api", default=API)
    API = ap.parse_args().api.rstrip("/")

    u = {k: login(k, n) for k, n in USERS}
    m, p1, p2, p3 = u["mestre"], u["jogador1"], u["jogador2"], u["jogador3"]

    # campanha + membros
    camps = call("GET", "/campaigns", token=m["token"])
    camp = find(camps, lambda c: c["name"] == "Mesa de Testes") or call(
        "POST", "/campaigns", {"name": "Mesa de Testes", "description": "Campanha de testes (dev_fixtures.py)"}, m["token"])
    cid = camp["id"]
    for p in (p1, p2, p3):
        soft((409,), "POST", f"/campaigns/{cid}/members", {"email": p["email"]}, m["token"])

    # clãs da campanha (Shiroi existe para o Teste de Sangue com 2 clãs)
    clans = call("GET", f"/catalog/clans?campaignId={cid}", token=m["token"])
    for name, roll, attr in (("Clã Kuroi", 1, "CON"), ("Clã Shiroi", 2, "DEX")):
        if not find(clans, lambda c: c["name"] == name and c["campaignId"] == cid):
            call("POST", f"/campaigns/{cid}/catalog/clans",
                 {"name": name, "rollNumber": roll, "description": f"Clã de teste ({name}).",
                  "buildGains": {"attributeFlat": {attr: 3}}, "heritage": []}, m["token"])

    # NPC com ficha
    npcs = call("GET", "/npcs", token=m["token"])
    npc = find(npcs, lambda n: n["name"] == "Sensei Kuro")
    if not npc:
        npc = call("POST", "/npcs", {
            "name": "Sensei Kuro", "description": "Mestre de artes marciais veterano.",
            "interactions": [
                {"type": "Treino", "name": "Treino", "description": "Treino básico.", "idlePointCost": 1},
                {"type": "Sparring", "name": "Sparring", "description": "Luta de treino.", "idlePointCost": 2}]},
            m["token"])
    nid = npc["id"]
    soft((409,), "POST", f"/campaigns/{cid}/npcs/{nid}", token=m["token"])
    if soft((404,), "GET", f"/npcs/{nid}/sheet", token=m["token"]) is None:
        call("POST", f"/npcs/{nid}/sheet", {
            "name": "Sensei Kuro", "bodyType": "MESOMORPH", "dominance": "RIGHT", "heightM": 1.80,
            "attributes": {"str": 14, "dex": 12, "con": 13, "intl": 10, "cha": 8, "men": 12, "tal": 9}}, m["token"])
    call("PATCH", f"/campaigns/{cid}/npcs/{nid}/sheet/visibility", {"section": "ATTRIBUTES", "visible": True}, m["token"])

    # TimeSkip + agendamento + log do Akira
    tss = call("GET", f"/campaigns/{cid}/timeskips", token=m["token"])
    ts = find(tss, lambda t: t["status"] == "ACTIVE") or call(
        "POST", f"/campaigns/{cid}/timeskips", {"name": "Semana de Treino", "totalDays": 7}, m["token"])
    tid = ts["id"]
    bks = call("GET", f"/campaigns/{cid}/timeskips/{tid}/bookings", token=p1["token"])
    bk = find(bks, lambda b: b["userId"] == p1["id"] and b["npcId"] == nid)
    if not bk:
        day = ts["currentDay"]
        for slot in range(1, 5):  # primeiro slot livre
            bk = soft((409,), "POST", f"/campaigns/{cid}/timeskips/{tid}/bookings",
                      {"npcId": nid, "dayNumber": day, "slotNumber": slot, "interactionName": "Treino"}, p1["token"])
            if bk:
                break
    logs = call("GET", f"/campaigns/{cid}/logs", token=p1["token"])
    if bk and not find(logs, lambda l: l["authorId"] == p1["id"]):
        call("POST", f"/campaigns/{cid}/logs",
             {"bookingId": bk["id"], "narrative": "Akira treinou com o Sensei Kuro até o pôr do sol."}, p1["token"])

    # fichas via Construção Inicial
    sid1, sheet1 = ensure_build(cid, p1, m, False)
    sid2, build2 = ensure_build(cid, p2, m, True)

    print("\nConta                      Senha        Papel / estado")
    for k, n in USERS:
        role = {"mestre": "Mestre da Mesa de Testes",
                "jogador1": f"ficha {sheet1['buildStatus']}",
                "jogador2": f"ficha IN_BUILD (passo {build2['step']})",
                "jogador3": "sem ficha"}[k]
        print(f"{k}@tatakai.dev".ljust(27) + PASSWORD.ljust(13) + f"{n} - {role}")
    print(f"\ncampanha {cid}\nnpc      {nid}\ntimeskip {tid}\nficha Akira  {sid1}\nficha Bianca {sid2}")
    print("Obs.: para editar o catálogo GLOBAL, ponha mestre@tatakai.dev em CATALOG_EDITORS.")


if __name__ == "__main__":
    main()
