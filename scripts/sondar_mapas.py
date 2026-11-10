"""Baixa a camada oficial de pavimentação viária (Agetran) pelo Sisgran."""
import urllib.request, urllib.parse, ssl, json
from collections import Counter
ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
SAIDA = open("dados/sondagem.txt", "w")
def note(m): SAIDA.write(str(m)[:4000] + "\n"); SAIDA.flush()
def get(u):
    try:
        r = urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json", "Referer": "https://sisgran.campogrande.ms.gov.br/mapas"}), timeout=300, context=ctx)
        return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read(3000).decode("utf-8", "replace")
    except Exception as e:
        return 0, repr(e)
B = "https://sisgran.campogrande.ms.gov.br/sisgran/api/v1/maps/"
st, cfg = get(B + "config"); note(f"CONFIG {st}: {cfg[:1500]}")
for nome in ["sisgran:vw_mobilidade_pavimentacao", "mobilidade-pavimentacao", "vw_mobilidade_pavimentacao"]:
    st, txt = get(B + "features/" + urllib.parse.quote(nome) + "?limit=20000")
    note(f"FEAT {st} {nome} {len(txt)}b :: {txt[:500]}")
    if st == 200 and txt.lstrip().startswith("{"):
        d = json.loads(txt); fs = d.get("features", [])
        note(f"OK features={len(fs)} chaves={list(d.keys())} numberMatched={d.get('numberMatched')}")
        if fs:
            note("EXEMPLO " + json.dumps(fs[0], ensure_ascii=False)[:1200])
            note("SITUACAO " + repr(Counter(str(f['properties'].get('situacao')) for f in fs).most_common(20)))
            json.dump(d, open("dados/pavimentacao-oficial.geojson", "w"), ensure_ascii=False)
        break
try:
    import re
    cj = json.loads(cfg); w = cj.get("wmsUrl", "")
    if w:
        base = urllib.parse.urljoin("https://sisgran.campogrande.ms.gov.br/", w)
        q = "service=WFS&version=2.0.0&request=GetFeature&typeNames=sisgran:vw_mobilidade_pavimentacao&outputFormat=application/json&srsName=EPSG:4326&count=5"
        st, txt = get(base + ("&" if "?" in base else "?") + q); note(f"WFS {st} {base} :: {txt[:800]}")
except Exception as e:
    note("cfg err " + repr(e))
