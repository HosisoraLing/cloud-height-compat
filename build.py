# SPDX-License-Identifier: AGPL-3.0-only
"""Build against an installed NeoForge 1.21.1 runtime without launching Minecraft."""
import argparse
from pathlib import Path
import shutil
import subprocess
import zipfile
import os

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--minecraft', type=Path, required=True, help='Minecraft root containing libraries/')
parser.add_argument('--iris', type=Path, required=True, help='Iris NeoForge 1.8.x jar')
parser.add_argument('--sodium', type=Path, required=True, help='Sodium NeoForge 0.8.x jar')
parser.add_argument('--jdk', type=Path, help='JDK 21 home; defaults to PATH')
parser.add_argument('--test-shader', type=Path, action='append', default=[], help='BSL zip; repeatable')
args = parser.parse_args()
root = Path(__file__).resolve().parent
build = root / 'build'
classes = build / 'classes'
classes.mkdir(parents=True, exist_ok=True)
libs = sorted((args.minecraft / 'libraries').rglob('*.jar'))
patched = [p for p in libs if p.name.startswith('neoforge-21.1.') and p.name.endswith('-client.jar')]
if not patched or not args.iris.is_file() or not args.sodium.is_file():
    parser.error('Requires a complete NeoForge 1.21.1 client installation, Iris and Sodium jars')
nested = []
for dependency in [args.iris, args.sodium]:
    with zipfile.ZipFile(dependency) as z:
        for name in z.namelist():
            if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                destination = build / 'dependencies' / dependency.stem / Path(name).name
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(z.read(name))
                nested.append(destination)
classpath = os.pathsep.join(str(p.resolve()) for p in [*patched, args.iris, args.sodium, *nested, *libs])

def tool(name):
    found = args.jdk / 'bin' / (name + ('.exe' if os.name == 'nt' else '')) if args.jdk else shutil.which(name)
    if not found:
        raise SystemExit(f'Missing {name}; install JDK 21 or provide --jdk')
    return str(found)

def run(command):
    # Background compilation should not compete with an active game for scheduling priority.
    result = subprocess.run(command, cwd=root, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, encoding='utf-8', errors='replace',
                            creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS if os.name == 'nt' else 0)
    print(result.stdout, end='')
    with (build / 'build.log').open('a', encoding='utf8') as log:
        log.write(result.stdout)
    if result.returncode:
        raise SystemExit(result.returncode)

def compile_java(sources, destination):
    javac_args = ['--release', '21', '-encoding', 'UTF-8', '-proc:none', '-cp', classpath,
                  '-d', str(destination), *map(str, sources)]
    argfile = build / 'javac.args'
    argfile.write_text('\n'.join('"' + arg.replace('\\', '/').replace('"', '\\"') + '"'
                                 for arg in javac_args), encoding='utf8')
    run([tool('javac'), '-J-Xmx384m', '@' + str(argfile)])

(build / 'build.log').write_text('', encoding='utf8')
compile_java(sorted((root / 'src/main/java').rglob('*.java')), classes)
jar = build / 'cloud-height-compat-1.1.1-neoforge-1.21.1.jar'
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\n\n')
    z.write(root / 'LICENSE', 'LICENSE')
    for directory in [classes, root / 'src/main/resources']:
        for path in sorted(directory.rglob('*')):
            if path.is_file():
                z.write(path, path.relative_to(directory).as_posix())
print(f'Built {jar}')
if args.test_shader:
    test_classes = build / 'test-classes'
    test_classes.mkdir(exist_ok=True)
    classpath = str(classes) + os.pathsep + classpath
    compile_java(sorted((root / 'tests').glob('*.java')), test_classes)
    java_args = ['-Xmx256m', '-ea', '-cp', str(test_classes) + os.pathsep + classpath,
                 'Checks', *[str(p.resolve()) for p in args.test_shader]]
    java_argfile = build / 'java.args'
    java_argfile.write_text('\n'.join('"' + arg.replace('\\', '/') + '"' for arg in java_args), encoding='utf8')
    run([tool('java'), '@' + str(java_argfile)])
    for mode in [[], ['reload']]:
        menu_args = ['-Xmx256m', '-ea', '-cp', str(test_classes) + os.pathsep + classpath,
                     'SodiumChecks', str(build / 'menu-test/client.toml'), *mode]
        java_argfile.write_text('\n'.join('"' + arg.replace('\\', '/') + '"' for arg in menu_args), encoding='utf8')
        run([tool('java'), '@' + str(java_argfile)])
