"""Build dict with Kotlin, Android command-line tools and R8 (no Gradle required)."""
from pathlib import Path
import argparse
import hashlib
import os
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

P = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--keystore', default=os.environ.get('DICT_KEYSTORE'), help='Existing signing keystore; omit for unsigned APK')
parser.add_argument('--alias', default=os.environ.get('DICT_KEY_ALIAS', 'ksm'))
parser.add_argument('--regenerate-icons', action='store_true', help='Regenerate checked-in icon assets; requires CairoSVG')
args = parser.parse_args()
env = os.environ.copy()
if env.get('JAVA_HOME'):
    env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env['PATH']

def run(*command):
    subprocess.run([str(x) for x in command], cwd=P, env=env, check=True)

def android_jar():
    if env.get('ANDROID_JAR'):
        return Path(env['ANDROID_JAR']).expanduser().resolve()
    for root in [env.get('ANDROID_SDK_ROOT'), env.get('ANDROID_HOME'), '/usr/lib/android-sdk']:
        if root:
            candidate = Path(root) / 'platforms/android-23/android.jar'
            if candidate.is_file():
                return candidate
    raise SystemExit('Set ANDROID_JAR to an installed Android platform android.jar')

platform = android_jar()
r8 = Path(env.get('R8_JAR', str(P / 'tools/r8.jar'))).expanduser().resolve()
stdlib = Path(env.get('KOTLIN_STDLIB', '/usr/share/java/kotlin-stdlib.jar')).expanduser().resolve()
stdlib_jdk7 = Path(env.get('KOTLIN_STDLIB_JDK7', str(stdlib.parent / 'kotlin-stdlib-jdk7.jar'))).expanduser().resolve()
for f in [platform, r8, stdlib, stdlib_jdk7]:
    if not f.is_file():
        raise SystemExit('Missing build dependency: ' + str(f))
for tool in ['java', 'kotlinc', 'aapt', 'zipalign']:
    if not shutil.which(tool, path=env['PATH']):
        raise SystemExit('Missing tool on PATH: ' + tool)
if args.keystore:
    keystore = Path(args.keystore).expanduser().resolve()
    if not keystore.is_file():
        raise SystemExit('Keystore does not exist; keep signing material outside this repository')
    if not env.get('DICT_STORE_PASS'):
        raise SystemExit('Set DICT_STORE_PASS in the environment before signing')
    if not shutil.which('apksigner', path=env['PATH']):
        raise SystemExit('Missing apksigner on PATH')

build = P / 'build'
assets = P / 'assets'
build.mkdir(exist_ok=True)
assets.mkdir(exist_ok=True)
version = ET.parse(P / 'AndroidManifest.xml').getroot().attrib['{http://schemas.android.com/apk/res/android}versionName']
source = (P.parent / '通用词库刷辅助码.html').read_bytes()
html = source.decode('utf-8')
assert html.count('</body>') == 1
(assets / 'index.html').write_text(html.replace('</body>', (P / 'bridge.js').read_text(encoding='utf-8') + '</body>'), encoding='utf-8')
(build / 'source.sha256').write_text(hashlib.sha256(source).hexdigest(), encoding='utf-8')
if args.regenerate_icons:
    run('python3', 'generate_icons.py')
    run('python3', 'generate_installer_icons.py')
run('kotlinc', 'src/local/ksm/aux/MainActivity.kt', '-classpath', platform, '-jvm-target', '1.8', '-d', build / 'app.jar')
run('java', '-cp', r8, 'com.android.tools.r8.D8', '--lib', platform, '--min-api', '23', '--output', build, build / 'app.jar', stdlib, stdlib_jdk7)
run('aapt', 'package', '-f', '-M', 'AndroidManifest.xml', '-S', 'res', '-A', 'assets', '-I', platform, '-F', build / 'unsigned.apk')
with zipfile.ZipFile(build / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(build / 'classes.dex', 'classes.dex')
unsigned = build / ('dict-' + version + '-unsigned.apk')
run('zipalign', '-f', '4', build / 'unsigned.apk', unsigned)
result = unsigned
if args.keystore:
    result = build / ('dict-' + version + '.apk')
    command = ['apksigner', 'sign', '--ks', keystore, '--ks-key-alias', args.alias, '--ks-pass', 'env:DICT_STORE_PASS']
    if env.get('DICT_KEY_PASS'):
        command += ['--key-pass', 'env:DICT_KEY_PASS']
    run(*command, '--out', result, unsigned)
    run('apksigner', 'verify', '--verbose', result)
run('zipalign', '-c', '4', result)
run('aapt', 'dump', 'badging', result)
print('Built:', result)
