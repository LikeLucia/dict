"""Rebuild the previously inspected 1.0.3 icon (archive is no longer present)."""
from pathlib import Path
import struct
import xml.etree.ElementTree as E
P = Path(__file__).resolve().parent / 'res'
A = 'http://schemas.android.com/apk/res/android'
E.register_namespace('android', A)
def at(k): return '{'+A+'}'+k
def fl(h): return str(struct.unpack('!f',bytes.fromhex(h))[0])
DATA = "m249.47,314.05h-29.06v-16.64h12.67v-80.77h-58.11v-15.74h74.5zM132.48,235.33h30.59v53.89l12.03,-4.1v19.07l-29.18,9.86v-61.7h-13.44zM158.72,198.85 L167.55,226.62h-17.41l-8.83,-27.78zM176.38,225.86h50.18v14.59h-50.18zM177.79,249.79h42.88v44.54h-42.88zM205.57,279.49v-14.85h-12.67v14.85zM309,198.5 L326,198.5 L333,214 L316,214 Z M271,215 H378 V230 H287 V262 Q287,295 273,314 L260,304 Q271,286 271,260 Z M298,240 H373 V254 H298 Z M316,231 H332 L311,267 H374 V281 H291 V268 Z M334,255 H350 V315 H334 Z M289,290 H379 V305 H289 Z"
def vector(bg):
    r=E.Element('vector',{at('width'):'108dp',at('height'):'108dp',at('viewportWidth'):'512',at('viewportHeight'):'512'})
    if bg: E.SubElement(r,'path',{at('fillColor'):'#FF3582FF',at('pathData'):'M0,0H512V512H0Z'})
    g=E.SubElement(r,'group',{at('scaleX'):fl('3f81f820'),at('scaleY'):fl('3f81f820'),at('translateX'):fl('c07c0fc1'),at('translateY'):fl('c07c0fc1')})
    E.SubElement(g,'path',{at('fillColor'):'#FFFFFFFF',at('pathData'):DATA})
    return E.tostring(r,encoding='unicode')
for n,bg in [('cimu_foreground.xml',False),('cimu_icon.xml',True)]:
    (P/'drawable'/n).write_text(vector(bg))
for version in [26,33]:
    r=E.Element('adaptive-icon')
    E.SubElement(r,'background',{at('drawable'):'@color/cimu_blue'})
    E.SubElement(r,'foreground',{at('drawable'):'@drawable/cimu_foreground'})
    if version==33:E.SubElement(r,'monochrome',{at('drawable'):'@drawable/cimu_foreground'})
    (P/('drawable-v'+str(version))/'cimu_icon.xml').write_text(E.tostring(r,encoding='unicode'))
