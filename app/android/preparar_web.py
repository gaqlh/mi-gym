"""Arma la página que va dentro del APK a partir de app/app-gym.html.

Le agrega el esqueleto HTML (el Artifact lo pone solo al publicar) y cambia las fuentes de
Google Fonts por copias guardadas dentro del APK, para que la app funcione sin internet.
Uso: python preparar_web.py <app-gym.html> <carpeta de salida> <versión>
"""
import base64
import pathlib
import re
import sys
import urllib.request

AQUI = pathlib.Path(__file__).resolve().parent
FUENTES = AQUI / 'fuentes'
CSS_URL = ('https://fonts.googleapis.com/css2?family=Barlow+Condensed:wght@600;700'
           '&family=Barlow:wght@400;500;600;700&family=IBM+Plex+Mono:wght@400;500;600&display=swap')
# Con un navegador moderno Google entrega woff2 separados por alfabeto
UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36'


def fuentes_css():
    """Descarga las fuentes una sola vez (latín y latín extendido) y devuelve su CSS local."""
    css_local = FUENTES / 'fuentes.css'
    if css_local.exists():
        return css_local.read_text(encoding='utf-8')
    FUENTES.mkdir(exist_ok=True)
    pedido = urllib.request.Request(CSS_URL, headers={'User-Agent': UA})
    css = urllib.request.urlopen(pedido, timeout=60).read().decode('utf-8')
    salida = []
    for alfabeto, bloque in re.findall(r'/\*\s*([\w-]+)\s*\*/\s*(@font-face\s*\{[^}]*\})', css):
        if alfabeto not in ('latin', 'latin-ext'):
            continue
        url = re.search(r'url\((https://[^)]+\.woff2)\)', bloque).group(1)
        familia = re.search(r"font-family:\s*'([^']+)'", bloque).group(1)
        peso = re.search(r'font-weight:\s*(\d+)', bloque).group(1)
        nombre = f"{familia.replace(' ', '')}-{peso}-{alfabeto}.woff2"
        destino = FUENTES / nombre
        if not destino.exists():
            destino.write_bytes(urllib.request.urlopen(url, timeout=60).read())
        salida.append(bloque.replace(url, 'fonts/' + nombre))
    if not salida:
        raise SystemExit('No se encontraron fuentes en la respuesta de Google Fonts')
    texto = '\n'.join(salida)
    css_local.write_text(texto, encoding='utf-8')
    return texto


def main(origen, destino, version):
    html = pathlib.Path(origen).read_text(encoding='utf-8')
    # La app compara esta versión con la última publicada en GitHub para avisar de actualizaciones
    if html.count("'__VERSION__'") != 1:
        raise SystemExit('No encontré el marcador de versión en la app')
    html = html.replace("'__VERSION__'", "'" + version + "'")
    destino = pathlib.Path(destino)
    destino.mkdir(parents=True, exist_ok=True)
    # Las fuentes van dentro de la misma página: la app no pide ningún archivo aparte
    css = re.sub(r'url\(fonts/([^)]+\.woff2)\)',
                 lambda m: 'url(data:font/woff2;base64,' + base64.b64encode((FUENTES / m.group(1)).read_bytes()).decode('ascii') + ')',
                 fuentes_css())
    html, quitados = re.subn(r'<link rel="(?:preconnect|stylesheet)" href="https://fonts\.g[^"]*"[^>]*>\n?', '', html)
    if quitados != 3:
        raise SystemExit(f'Esperaba 3 enlaces a Google Fonts y encontré {quitados}')
    cabeza = ('<!doctype html><html lang="es"><head><meta charset="utf-8">'
              '<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,viewport-fit=cover">'
              '<style>:root{color-scheme:light}body{margin:0}img{max-width:100%}[hidden]{display:none!important}</style>'
              '<style>' + css + '</style></head><body>')
    (destino / 'index.html').write_text(cabeza + html + '</body></html>', encoding='utf-8')
    print(f'index.html listo con {len(list(FUENTES.glob("*.woff2")))} fuentes')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3])
