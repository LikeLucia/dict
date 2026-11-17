"""Generate PNG fallbacks for APK installers; retain adaptive/monochrome variants."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

P = Path(__file__).resolve().parent
# Keep rendering dependencies local to the workspace.
sys.path.insert(0, str(P.parent / 'render-tools'))
import cairosvg

A = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', A)
ICON = 'ic_launcher_105'
foreground = ET.parse(P / 'res/drawable/cimu_foreground.xml').getroot()
group = foreground.find('group')
assert group is not None
key = lambda name: '{' + A + '}' + name
transform = 'translate({} {}) scale({} {})'.format(
    group.attrib[key('translateX')], group.attrib[key('translateY')],
    group.attrib[key('scaleX')], group.attrib[key('scaleY')])
paths = ''.join('<path fill="{}" d="{}"/>'.format(
    child.attrib.get(key('fillColor'), '#FFFFFFFF')[-6:],
    child.attrib[key('pathData')]) for child in group.findall('path'))
# Android colors are AARRGGBB; this foreground is fully opaque white.
paths = paths.replace('fill="FFFFFF"', 'fill="#FFFFFF"')
# Flatten the center 72dp of the 108dp adaptive canvas for the legacy fallback.
# This gives installer previews the same glyph proportions as an adaptive mask.
inset = 512 / 6
span = 512 * 2 / 3
svg = '''<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
<defs><clipPath id="shape"><rect width="512" height="512" rx="104"/></clipPath></defs>
<g clip-path="url(#shape)"><rect width="512" height="512" fill="#3582FF"/>
<g transform="scale(1.5) translate(-{inset} -{inset})"><g transform="{transform}">{paths}</g></g></g>
</svg>'''.format(inset=inset, transform=transform, paths=paths)
for density, px in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    folder = P / 'res' / ('mipmap-' + density)
    folder.mkdir(exist_ok=True)
    cairosvg.svg2png(bytestring=svg.encode(), write_to=str(folder/(ICON+'.png')),
                    output_width=px, output_height=px)
for api in [26,33]:
    root = ET.Element('adaptive-icon')
    ET.SubElement(root, 'background', {key('drawable'):'@color/cimu_blue'})
    ET.SubElement(root, 'foreground', {key('drawable'):'@drawable/cimu_foreground'})
    if api == 33:
        ET.SubElement(root, 'monochrome', {key('drawable'):'@drawable/cimu_foreground'})
    folder = P / 'res' / ('mipmap-anydpi-v' + str(api))
    folder.mkdir(exist_ok=True)
    (folder/(ICON+'.xml')).write_text(ET.tostring(root, encoding='unicode'))
# Standalone artwork for reviewing the actual flattened installer graphic.
(P / 'build').mkdir(exist_ok=True)
cairosvg.svg2png(bytestring=svg.encode(), write_to=str(P/'build/installer-icon.png'),
                output_width=512, output_height=512)
print('Generated 5 PNG density fallbacks and API26/API33 adaptive icons: '+ICON)
