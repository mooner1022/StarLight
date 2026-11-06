import json
import sys
from pathlib import Path


def main():
    repo, version, code, url, notes = sys.argv[1:6]
    repo = Path(repo)
    changes = repo / "changes"
    changelog = f"snapshot-{code}.md"

    for old in changes.glob("snapshot-*.md"):
        old.unlink()
    changes.mkdir(exist_ok=True)
    (changes / changelog).write_text(Path(notes).read_text(encoding="utf-8"), encoding="utf-8", newline="\n")

    path = repo / "version.json"
    versions = json.loads(path.read_text(encoding="utf-8"))
    versions["snapshot"] = {
        "version": version,
        "versionCode": int(code),
        "changelog": changelog,
        "downloadUrl": url,
    }
    path.write_text(json.dumps(versions, indent=4, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
