#!/usr/bin/env python3
"""Build the private Kaggle source archive from the repository working tree."""
import argparse
import json
import stat
import subprocess
import zipfile
from pathlib import Path, PurePosixPath

MANIFEST = "loadtest-bundle.json"
OMIT_DIRECTORIES = {".git", "target", "secrets"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-o", "--output", type=Path, default=Path("source.zip"),
                        help="ZIP to write (default: source.zip in the repository root)")
    args = parser.parse_args()

    root = Path.cwd().resolve()
    top = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"],
                                       cwd=root, text=True).strip()).resolve()
    if top != root:
        raise SystemExit("Run loadtest/bundle.py from the repository root")
    requested = args.output
    if requested.is_symlink():
        raise SystemExit(f"Output is a symlink, refusing to follow it: {requested}; choose an unused path")
    output = requested.resolve()
    if output.exists():
        raise SystemExit(f"Output already exists: {output}; choose an unused path")
    source_commit = subprocess.check_output(["git", "rev-parse", "HEAD"],
                                            cwd=root, text=True).strip()
    names = subprocess.check_output(
        ["git", "ls-files", "-co", "--exclude-standard", "-z"], cwd=root).decode().split("\0")

    def has_symlink_parent(relative):
        current = root
        for part in relative.parts:
            current = current / part
            if current.is_symlink():
                return True
        return False

    written = 0
    try:
        bundle = zipfile.ZipFile(output, "x", zipfile.ZIP_DEFLATED)
    except FileExistsError as error:
        raise SystemExit(f"Output already exists: {output}; choose an unused path") from error
    with bundle:
        for name in filter(None, names):
            relative = PurePosixPath(name)
            if (relative.is_absolute() or ".." in relative.parts or relative.name == MANIFEST
                    or any(part in OMIT_DIRECTORIES or part.startswith(".env")
                           for part in relative.parts)):
                continue
            path = root.joinpath(*relative.parts)
            if has_symlink_parent(relative):
                continue
            try:
                resolved = path.resolve()
                resolved.relative_to(root)
            except (OSError, ValueError):
                continue  # missing file or a symlink parent escaping the repository
            if resolved == output:
                continue
            try:
                mode = path.lstat().st_mode
            except FileNotFoundError:
                continue
            if not stat.S_ISREG(mode) or path.is_symlink():
                continue
            info = zipfile.ZipInfo(relative.as_posix())
            info.create_system = 3
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (stat.S_IFREG | (0o755 if mode & 0o111 else 0o644)) << 16
            bundle.writestr(info, path.read_bytes())
            written += 1
        bundle.writestr(MANIFEST, json.dumps({
            "snapshotMode": "working-tree",
            "sourceCommit": source_commit,
        }))
    print(f"Wrote {output} with {written} files from {source_commit}")


if __name__ == "__main__":
    main()
