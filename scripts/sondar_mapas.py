"""Baixa a camada oficial de pavimentação do Sisgran (sisgran:vw_mobilidade_pavimentacao)."""
import re, urllib.request, urllib.parse, ssl, json
ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
SAIDA = open("dados/sondagem.txt", "w")
def note(m): SAIDA.write(str(m)[:4000] + "\n"); SAIDA.flush()
def get(u, n=60000000):
    try:
        r = urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0", "Referer": "https://sisgran.campogrande.ms.gov.br/"}), timeout=120, context=ctx)
        return r.status, r.geturl(), r.read(n).decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, u, e.read(2000).decode("utf-8", "replace")
    except Exception as e:
        return 0, u, repr(e)
st, fim, js = get("https://sisgran.campogrande.ms.gov.br/sisgran/assets/index-B_Eqb3qG.js")
for pat in [r'.{0,200}(?:geoserver|/ows|/wms|wfs).{0,200}', r'.{0,120}mobilidade-pavimentacao.{0,900}']:
    for t in re.findall(pat, js, re.I)[:12]: note("JS: " + t)
cands = set(re.findall(r'["\'`]((?:https?:)?/[^"\'`\s]*(?:geoserver|ows|wms|wfs)[^"\'`\s]*)["\'`]', js, re.I))
note("CANDS " + repr(cands))
bases = {urllib.parse.urljoin("https://sisgran.campogrande.ms.gov.br/", c.split("?")[0]) for c in cands}
bases |= {"https://sisgran.campogrande.ms.gov.br/geoserver/ows", "https://sisgran.campogrande.ms.gov.br/geoserver/sisgran/ows",
          "https://sisgran.campogrande.ms.gov.br/geoserver/wfs", "https://sisgran.campogrande.ms.gov.br/sisgran/geoserver/ows",
          "https://sisgran.campogrande.ms.gov.br/api/geoserver/ows"}
for b in sorted(bases):
    b = re.sub(r'/(wms|wfs)$', '/ows', b) if not b.endswith('ows') and re.search('(wms|wfs)$', b) else b
    q = "?service=WFS&version=2.0.0&request=GetFeature&typeNames=sisgran:vw_mobilidade_pavimentacao&outputFormat=application/json&srsName=EPSG:4326"
    st, f2, txt = get(b + q)
    note(f"TRY {st} {b+q} {len(txt)}b :: {txt[:300]}")
    if st == 200 and txt.lstrip().startswith("{"):
        open("dados/pavimentacao-oficial.geojson", "w").write(txt)
        d = json.loads(txt); fs = d.get("features", [])
        note(f"OK features={len(fs)}")
        from collections import Counter
        note("PROPS " + json.dumps(fs[0]["properties"], ensure_ascii=False) if fs else "vazio")
        note("SITUACAO " + repr(Counter(str(f["properties"].get("situacao")) for f in fs).most_common(20)))
        break
# v2
