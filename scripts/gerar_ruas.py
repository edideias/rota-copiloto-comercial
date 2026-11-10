"""Gera o banco de ruas de Campo Grande (MS).

Pavimentação: camada oficial "Pavimentação viária" da Agetran, publicada no Sisgran (Prefeitura).
Nomes, bairros e ruas que faltarem: OpenStreetMap.

Saída: dados/campo-grande-ruas.json
  - ruas: nome, bairros por onde passa, pavimentação (pavimentada / terra / misto / sem informação) e km
  - bairros: nomes oficiais encontrados no mapa
Roda pelo GitHub Actions todo mês (ou na mão), então o banco se atualiza sozinho.
Fonte: © colaboradores do OpenStreetMap, licença ODbL.
"""
import json, sys, time, unicodedata, re, urllib.request, urllib.parse, math, datetime

SERVIDORES = ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter",
              "https://maps.mail.ru/osm/tools/overpass/api/interpreter"]
BB = "(-20.66,-54.82,-20.32,-54.43)"  # área urbana de Campo Grande (sul, oeste, norte, leste)
RUAS = 'way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|track|road)(_link)?$"]["name"]' + BB + ';out tags geom;'
BAIRROS = '(rel["boundary"="administrative"]["admin_level"~"^(9|10)$"]' + BB + ';rel["place"~"^(suburb|neighbourhood|quarter)$"]' + BB + ';way["place"~"^(suburb|neighbourhood|quarter)$"]' + BB + ';node["place"~"^(suburb|neighbourhood|quarter)$"]' + BB + ';);out tags geom;'

# Condomínios fechados / loteamentos com nome (áreas residenciais)
CONDOS = ('(way["landuse"="residential"]["name"]' + BB + ';rel["landuse"="residential"]["name"]' + BB +
          ';way["residential"="gated"]' + BB + ';way["place"~"^(neighbourhood|quarter)$"]["name"]' + BB +
          ';way["landuse"="residential"]["access"="private"]' + BB + ';);out tags geom;')
NOME_CONDO = re.compile(r"(?i)condom|resid|damha|dama|alphavil|village|vilage|ville|reserva|quinta|portal|terras|eco ?park|park|bosque|gardens?|green|lagoa|chac")

PAVIM = {"asphalt", "paved", "concrete", "concrete:plates", "concrete:lanes", "paving_stones", "sett", "cobblestone",
         "unhewn_cobblestone", "metal", "wood", "chipseal"}
TERRA = {"unpaved", "dirt", "earth", "ground", "gravel", "fine_gravel", "compacted", "sand", "grass", "mud", "pebblestone", "rock"}

def overpass(q):
    erro = None
    for url in SERVIDORES:
        for tent in range(3):
            try:
                req = urllib.request.Request(url, data=urllib.parse.urlencode({"data": "[out:json][timeout:300];" + q}).encode(),
                                             headers={"User-Agent": "RotaCalculadora-dados/1.0"})
                with urllib.request.urlopen(req, timeout=360) as r:
                    return json.load(r)["elements"]
            except Exception as e:
                erro = e; print(f"::warning::Overpass {url}: {e}"); time.sleep(20)
    print(f"::error::Overpass indisponível: {erro}"); raise SystemExit(1)

def norm(s):
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = re.sub(r"[^a-z0-9 ]", " ", s)
    s = re.sub(r"\b(r|rua)\b", "rua", s); s = re.sub(r"\b(av|avenida)\b", "avenida", s)
    s = re.sub(r"\b(tv|trav|travessa)\b", "travessa", s); s = re.sub(r"\b(al|alameda)\b", "alameda", s)
    return re.sub(r"\s+", " ", s).strip()

def km(geom):
    t = 0.0
    for a, b in zip(geom, geom[1:]):
        dl = math.radians(b["lat"] - a["lat"]); dn = math.radians(b["lon"] - a["lon"])
        h = math.sin(dl/2)**2 + math.cos(math.radians(a["lat"]))*math.cos(math.radians(b["lat"]))*math.sin(dn/2)**2
        t += 12742 * math.asin(math.sqrt(h))
    return t

def aneis(el):
    if el["type"] == "way":
        return [[(p["lon"], p["lat"]) for p in el.get("geometry", [])]]
    out = []
    for m in el.get("members", []):
        if m.get("role") in ("outer", "") and m.get("geometry"):
            out.append([(p["lon"], p["lat"]) for p in m["geometry"]])
    return out

def dentro(x, y, anel):
    d = False; n = len(anel)
    for i in range(n):
        x1, y1 = anel[i]; x2, y2 = anel[(i+1) % n]
        if (y1 > y) != (y2 > y) and x < (x2-x1)*(y-y1)/(y2-y1+1e-15)+x1: d = not d
    return d

SISGRAN = "https://sisgran.campogrande.ms.gov.br/sisgran/api/v1/maps/features/mobilidade-pavimentacao?limit=50000"

def oficial():
    import ssl
    ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
    for tent in range(3):
        try:
            req = urllib.request.Request(SISGRAN, headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json",
                                                           "Referer": "https://sisgran.campogrande.ms.gov.br/mapas"})
            with urllib.request.urlopen(req, timeout=300, context=ctx) as r:
                return json.load(r)["features"]
        except Exception as e:
            print(f"::warning::Sisgran: {e}"); time.sleep(20)
    print("::warning::Sisgran indisponível: pavimentação só do OpenStreetMap"); return []

def main():
    bairros = []; pontos = []
    for el in overpass(BAIRROS):
        nome = el.get("tags", {}).get("name")
        if not nome: continue
        if el["type"] == "node":
            pontos.append((nome, el["lon"], el["lat"])); continue
        # junta segmentos soltos num só polígono (aproximação suficiente para teste de ponto)
        pts = [p for a in aneis(el) for p in a]
        if len(pts) < 3: continue
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
        bairros.append({"nome": nome, "aneis": aneis(el), "bbox": (min(xs), min(ys), max(xs), max(ys)),
                        "nivel": el["tags"].get("admin_level", "")})
    # bairros (nível 10) antes de regiões maiores (9)
    bairros.sort(key=lambda b: (b["nivel"] != "10", (b["bbox"][2]-b["bbox"][0])*(b["bbox"][3]-b["bbox"][1])))

    def bairro_de(lon, lat):
        for b in bairros:
            x0, y0, x1, y1 = b["bbox"]
            if x0 <= lon <= x1 and y0 <= lat <= y1 and any(dentro(lon, lat, a) for a in b["aneis"] if len(a) > 2):
                return b["nome"]
        # sem polígono: bairro marcado como ponto mais próximo (até 1,8 km)
        melhor, dmin = None, 1.8
        for nome, x, y in pontos:
            d = km([{"lat": lat, "lon": lon}, {"lat": y, "lon": x}])
            if d < dmin: melhor, dmin = nome, d
        return melhor

    condos = []
    for el in overpass(CONDOS):
        t = el.get("tags", {}); nome = t.get("name")
        fechado = t.get("residential") == "gated" or t.get("access") == "private"
        if not nome or not (fechado or NOME_CONDO.search(nome)): continue
        an = [a for a in aneis(el) if len(a) > 2]
        if not an: continue
        xs = [p[0] for a in an for p in a]; ys = [p[1] for a in an for p in a]
        area = (max(xs)-min(xs))*(max(ys)-min(ys))
        if area > 0.0016: continue   # maior que ~4x4 km: é bairro, não condomínio
        condos.append({"nome": nome, "aneis": an, "bbox": (min(xs), min(ys), max(xs), max(ys))})

    def condo_de(lon, lat):
        for c in condos:
            x0, y0, x1, y1 = c["bbox"]
            if x0 <= lon <= x1 and y0 <= lat <= y1 and any(dentro(lon, lat, a) for a in c["aneis"]):
                return c["nome"]
        return None

    ruas = {}
    for el in overpass(RUAS):
        t = el.get("tags", {}); g = el.get("geometry") or []
        if len(g) < 2: continue
        nome = t["name"]; chave = norm(nome)
        r = ruas.setdefault(chave, {"nome": nome, "bairros": {}, "km_pav": 0.0, "km_terra": 0.0, "km_sem": 0.0, "tipos": set(), "condo": {}, "km_osm": 0.0})
        comp = km(g); mid = g[len(g)//2]
        r["km_osm"] += comp
        cd = condo_de(mid["lon"], mid["lat"])
        if cd: r["condo"][cd] = r["condo"].get(cd, 0) + comp
        b = bairro_de(mid["lon"], mid["lat"])
        if b: r["bairros"][b] = r["bairros"].get(b, 0) + comp
        sup = (t.get("surface") or "").split(";")[0]
        if sup in PAVIM: r["km_pav"] += comp
        elif sup in TERRA or t.get("highway") == "track": r["km_terra"] += comp
        else: r["km_sem"] += comp
        r["tipos"].add(t.get("highway"))

    # pavimentação oficial (Agetran/Sisgran) por cima da do OpenStreetMap
    ofi = {}
    for f in oficial():
        pr = f.get("properties", {}); nome = (pr.get("n_logra") or "").strip()
        if not nome: continue
        chave = norm(nome); g = f.get("geometry") or {}
        o = ofi.setdefault(chave, {"nome": nome, "pav": 0.0, "terra": 0.0, "meio": None})
        ext = (pr.get("extensao") or 0) / 1000.0
        if pr.get("situacao") == "PAVIMENTADA": o["pav"] += ext
        else: o["terra"] += ext
        cs = g.get("coordinates") or []
        if g.get("type") == "MultiLineString" and cs: cs = cs[0]
        if cs and not o["meio"]: o["meio"] = cs[len(cs)//2]
    for chave, o in ofi.items():
        r = ruas.get(chave)
        if r is None:
            r = ruas[chave] = {"nome": o["nome"].title(), "bairros": {}, "km_pav": 0.0, "km_terra": 0.0, "km_sem": 0.0, "tipos": set(), "condo": {}, "km_osm": 0.0}
            if o["meio"]:
                b = bairro_de(o["meio"][0], o["meio"][1])
                if b: r["bairros"][b] = 1
                cd = condo_de(o["meio"][0], o["meio"][1])
                if cd: r["condo"][cd] = 1.0; r["km_osm"] = 1.0
        r["km_pav"], r["km_terra"], r["km_sem"] = o["pav"], o["terra"], 0.0
        r["oficial"] = True

    saida = []
    for chave, r in sorted(ruas.items()):
        conhecido = r["km_pav"] + r["km_terra"]
        if conhecido == 0: pav = "sem_info"
        elif r["km_terra"] / conhecido < 0.1: pav = "pavimentada"
        elif r["km_pav"] / conhecido < 0.1: pav = "terra"
        else: pav = "misto"
        saida.append({"nome": r["nome"], "chave": chave, "pavimento": pav,
                      "km_pavimentado": round(r["km_pav"], 2), "km_terra": round(r["km_terra"], 2), "km_sem_info": round(r["km_sem"], 2),
                      "bairros": [b for b, _ in sorted(r["bairros"].items(), key=lambda x: -x[1])],
                      "fonte": "agetran" if r.get("oficial") else "osm",
                      # rua que fica (quase toda) dentro de um condomínio
                      "condominio": next((c for c, k in sorted(r["condo"].items(), key=lambda x: -x[1]) if r["km_osm"] and k / r["km_osm"] >= 0.6), None)})
    resumo = {p: sum(1 for s in saida if s["pavimento"] == p) for p in ("pavimentada", "terra", "misto", "sem_info")}
    import os; os.makedirs("dados", exist_ok=True)
    json.dump({"versao": 3, "cidade": "Campo Grande - MS",
               "gerado_em": datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
               "fonte": "Pavimentação: Agetran/Sisgran - Prefeitura de Campo Grande. Ruas e bairros: © colaboradores do OpenStreetMap (ODbL)", "resumo": {"ruas": len(saida), **resumo},
               "bairros": sorted({b["nome"] for b in bairros if b["nivel"] != "9"} | {p[0] for p in pontos}),
               "condominios": sorted({c["nome"] for c in condos}),
               "ruas": saida}, open("dados/campo-grande-ruas.json", "w"), ensure_ascii=False, indent=0)
    print("::notice::" + json.dumps({"ruas": len(saida), **resumo, "bairros_poligono": len(bairros), "bairros_ponto": len(pontos), "ruas_com_bairro": sum(1 for x in saida if x["bairros"]), "ruas_oficiais": len(ofi), "condominios": len(condos), "ruas_em_condominio": sum(1 for x in saida if x["condominio"])}))

if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as e:
        import traceback
        print("::error::" + repr(e) + " | " + traceback.format_exc().replace("\n", " / ")[-600:])
        raise
