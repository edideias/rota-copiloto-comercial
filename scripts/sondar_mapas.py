"""Procura no Sisgran (Planurb) as camadas de mapa e se alguma traz pavimentação."""
import re, urllib.request, urllib.parse, ssl, json
ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
SAIDA = open("dados/sondagem.txt", "w")
def note(m): SAIDA.write(str(m)[:3000] + "\n"); SAIDA.flush()
def get(u, n=8000000):
    try:
        r = urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0"}), timeout=60, context=ctx)
        return r.status, r.geturl(), r.read(n).decode("utf-8", "replace")
    except Exception as e:
        return 0, u, repr(e)
st, fim, html = get("https://sisgran.campogrande.ms.gov.br/")
note("HTML: " + html)
urls = set()
for s in re.findall(r'(?:src|href)=["\']([^"\']+)', html):
    full = urllib.parse.urljoin(fim, s)
    st, f2, js = get(full)
    note(f"ASSET {st} {full} {len(js)}")
    for u in re.findall(r'https?://[A-Za-z0-9._:/%?&=+\-#~]+', js): urls.add(u)
    for u in re.findall(r'["\'](/[A-Za-z0-9_\-/]*(?:api|geoserver|wms|wfs|ows|camada|layer|mapa)[A-Za-z0-9_\-/.?=&]*)["\']', js, re.I): urls.add(urllib.parse.urljoin(fim, u))
    for t in re.findall(r'.{0,150}pavim.{0,150}', js, re.I)[:30]: note("PAVIM: " + t)
for u in sorted(urls):
    if not re.search(r'w3\.org|reactjs|github|npmjs|mozilla|googleapis\.com/css|fonts\.', u): note("URL " + u)
# tenta servidores de mapa achados
for u in sorted(urls):
    m = re.match(r'(https?://[^?#]*?(?:geoserver[^?#]*?/(?:ows|wms|wfs)|arcgis/rest/services[^?#]*|/api/[^?#]*))', u, re.I)
    if not m: continue
    base = m.group(1)
    for q in (["?service=WFS&version=2.0.0&request=GetCapabilities", "?service=WMS&request=GetCapabilities"] if 'geoserver' in base else ["?f=json", ""]):
        st, f2, txt = get(base + q, 3000000)
        nomes = re.findall(r'<(?:Name|wfs:Name)>([^<]+)</', txt)
        note(f"SERVICO {st} {base+q} {len(txt)}b camadas={len(nomes)}")
        note("  " + ", ".join(nomes)[:2500] if nomes else "  " + txt[:800])
