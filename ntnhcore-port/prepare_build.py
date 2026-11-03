from pathlib import Path
import argparse,hashlib,json,shutil
p=argparse.ArgumentParser();p.add_argument('--mods-dir',required=True,type=Path);a=p.parse_args()
r=Path(__file__).resolve().parent
for item in json.loads((r/'BUILD_DEPENDENCIES.json').read_bytes()):
    q=a.mods_dir/item['filename']
    if not q.exists():
        q=next((x for x in a.mods_dir.glob('*.jar') if hashlib.sha256(x.read_bytes()).hexdigest()==item['sha256']),q)
    assert q.exists() and hashlib.sha256(q.read_bytes()).hexdigest()==item['sha256'],item
    (r/'libs').mkdir(exist_ok=True);shutil.copy2(q,r/'libs'/item['filename'])
print('Compile-only dependencies copied and verified. Run gradlew.bat --offline build with Java 17.')
