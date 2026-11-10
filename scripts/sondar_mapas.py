"""Procura nos mapas oficiais da Prefeitura de Campo Grande uma camada de pavimentação que possa ser baixada."""
import re, urllib.request, ssl, json
ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
def get(u, n=400000):
    try:
        r = urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0"}), timeout=40, context=ctx)
        return r.status, r.geturl(), r.read(n).decode("utf-8", "replace")
    except Exception as e:
        return 0, u, repr(e)
def note(m): print("::notice::" + m.replace("\n", " ")[:900])
INICIO = ["https://sisgran.campogrande.ms.gov.br/", "https://www.campogrande.ms.gov.br/agetran/mapas-interativos/",
          "https://www.campogrande.ms.gov.br/agetran/", "https://www.campogrande.ms.gov.br/semadur/simgeo/",
          "https://simgeo.campogrande.ms.gov.br/", "https://geo.campogrande.ms.gov.br/", "https://mapas.campogrande.ms.gov.br/",
          "https://geoserver.campogrande.ms.gov.br/geoserver/web/", "https://sisgran.campogrande.ms.gov.br/geoserver/ows?service=WMS&request=GetCapabilities"]
achados = set()
fila = list(INICIO); vistos = set()
while fila and len(vistos) < 40:
    u = fila.pop(0)
    if u in vistos: continue
    vistos.add(u)
    st, fim, txt = get(u)
    note(f"{st} {u} -> {fim} ({len(txt)} bytes) titulo={re.search(r'<title>(.*?)</title>', txt, re.S).group(1).strip()[:80] if '<title>' in txt else '-'}")
    for m in re.findall(r'https?://[^\s"\'<>)]+', txt):
        if re.search(r'geoserver|arcgis|/wms|/wfs|MapServer|FeatureServer|\.kml|\.kmz|\.shp|\.geojson|\.zip|mapas?-interativ|simgeo|qgis|lizmap|i3geo|umap|mymaps|google\.com/maps/d', m, re.I):
            achados.add(m.rstrip('.,;'))
        if ('campogrande.ms.gov.br' in m) and re.search(r'mapa|geo|simgeo|sisgran|agetran', m, re.I) and len(fila) < 60:
            fila.append(m.rstrip('.,;'))
    for m in re.findall(r'(?:href|src)=["\']([^"\']+)["\']', txt):
        if re.search(r'geoserver|arcgis|wms|wfs|kml|kmz|shp|geojson|mapa|simgeo|i3geo|lizmap', m, re.I):
            achados.add(urllib.parse.urljoin(fim, m))
    if re.search(r'pavimenta', txt, re.I):
        for t in re.findall(r'.{0,120}pavimenta.{0,120}', txt, re.I)[:5]: note("PAVIMENTA em " + u + ": " + t)
import urllib.parse
for a in sorted(achados)[:80]: note("LINK " + a)
# tenta GetCapabilities em qualquer servidor de mapas achado
for a in sorted(achados):
    m = re.match(r'(https?://[^?#]*?(geoserver|arcgis/rest/services|cgi-bin/mapserv|i3geo)[^?#]*)', a, re.I)
    if not m: continue
    base = m.group(1)
    for q in ["?service=WFS&request=GetCapabilities", "?service=WMS&request=GetCapabilities", "?f=json"]:
        st, fim, txt = get(base + q, 2000000)
        if st == 200:
            nomes = re.findall(r'<(?:Name|wfs:Name)>([^<]+)</', txt) or re.findall(r'"name"\s*:\s*"([^"]+)"', txt)
            note(f"SERVICO {base+q}: {len(nomes)} camadas; com pavimento: {[n for n in nomes if re.search('pav|asfal|via|logradouro|eixo', n, re.I)][:40]}")
