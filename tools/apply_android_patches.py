#!/usr/bin/env python3
"""Apply pinned Android/Hero adaptations, refusing unknown dependency drift."""
from pathlib import Path
import subprocess
import sys
ROOT = Path(__file__).resolve().parents[1]

def apply(repo, patch, revision):
    base = subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip()
    if base != revision:
        sys.exit(f'{repo.name} revision changed; review patches before building')
    def check(*args):
        return subprocess.run(['git', '-C', str(repo), 'apply', '--check', *args, str(patch)], capture_output=True)
    if check('--reverse').returncode == 0:
        print(f'{repo.name} patch already applied')
    elif check().returncode == 0:
        subprocess.run(['git', '-C', str(repo), 'apply', str(patch)], check=True)
        print(f'Applied {repo.name} patch')
    else:
        sys.exit(f'{repo.name} has conflicting changes; patch was not applied')

if __name__ == '__main__':
    apply(ROOT/'lib/rt64', ROOT/'android/patches/rt64-hero.patch', '5bec328c15ab02ab70154d32aa367f3cdb83ab38')
    apply(ROOT/'lib/RecompFrontend', ROOT/'android/patches/recompfrontend.patch', 'b3b7ebb4ec1a8a763c0191486f1b3329f9499a48')
