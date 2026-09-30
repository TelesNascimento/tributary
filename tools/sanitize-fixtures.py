import hashlib
import json
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RAW = os.path.join(ROOT, "src", "test", "resources", "fixtures", "raw")
OUT = os.path.join(ROOT, "src", "test", "resources", "fixtures")
MAX_ITEMS = 6

RULES_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sanitize-rules.local.json")
EXAMPLE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sanitize-rules.example.json")


def load_rules():
    path = RULES_FILE if os.path.exists(RULES_FILE) else EXAMPLE_FILE
    with open(path, encoding="utf-8") as f:
        return json.load(f)


RULES = load_rules()
BANNED = re.compile(RULES["banned"], re.I)


def digest(kind, value, modulo):
    return int(hashlib.sha1((kind + "|" + value).encode("utf-8")).hexdigest(), 16) % modulo


class Mapper:
    """Fake values derived from a hash of the real value: stable across recaptures and file order."""

    def user(self, uid):
        return f"U{digest('user', uid, 10**6):06d}"

    def user_name(self, name, uid):
        return f"User {digest('user', uid, 100):02d} {self.user(uid)}"

    def uuid(self, value):
        return "_" + f"FAKEUUID{digest('uuid', value, 10**14):014d}"

    def number(self, value):
        return str(9000000 + digest("number", value, 10**6))

    def comment(self, value):
        return f"change set comment {digest('comment', value, 10**4):04d}"

    def summary(self, value):
        return f"Sample work item {digest('summary', value, 10**4):04d}"

    def class_name(self, value):
        return f"Sample{digest('class', value, 10**4):04d}"

    def line(self, value):
        return f"source line {digest('line', value, 10**5):05d}"


M = Mapper()
SYSTEM_CODES = RULES["system_codes"]

NAME_WITH_ID = re.compile(r"(?:[A-Za-zÀ-ÿ]+ ){1,7}([A-Z]\d{6})\b")
BARE_ID = re.compile(r"(?<![A-Za-z0-9])[DFB]\d{6}(?!\d)")
UUID = re.compile(r"(?<![A-Za-z0-9])_[A-Za-z0-9_-]{22}(?![A-Za-z0-9_-])")
BIG_NUMBER = re.compile(r"(?<![\d.A-Za-z])(?<!20\d{6}_)(?!20\d{6}(?!\d))\d{6,8}(?![\d.])")
SYSTEM = re.compile(r"(?<![A-Za-z0-9])S(\d{3})(?![0-9])")
SYSTEM_LOWER = re.compile(r"(?<![A-Za-z0-9])s(\d{3})(?![0-9])")
JAVA_FILE = re.compile(r"([^/\\]+)\.java$")

TEXT_RULES = [(re.compile(pattern, re.I), replacement) for pattern, replacement in RULES["text_rules"]]


def text(value):
    def name(m):
        return M.user_name(m.group(0), m.group(1))

    value = NAME_WITH_ID.sub(name, value)
    value = BARE_ID.sub(lambda m: M.user(m.group(0)), value)
    value = UUID.sub(lambda m: M.uuid(m.group(0)), value)
    value = BIG_NUMBER.sub(lambda m: M.number(m.group(0)), value)
    value = SYSTEM.sub(lambda m: SYSTEM_CODES.get(m.group(1), "P900"), value)
    value = SYSTEM_LOWER.sub(lambda m: SYSTEM_CODES.get(m.group(1), "P900").lower(), value)
    for pattern, replacement in TEXT_RULES:
        value = pattern.sub(replacement, value)
    return value


def walk(node, key=None):
    if isinstance(node, dict):
        return {k: walk(v, k) for k, v in node.items()}
    if isinstance(node, list):
        items = node[:MAX_ITEMS]
        return [walk(v, key) for v in items]
    if isinstance(node, str):
        if key == "comment":
            return M.comment(node)
        if key == "line":
            return M.line(node)
        if key == "path":
            node = JAVA_FILE.sub(lambda m: m.group(0) if m.group(1) == "package-info"
                                 else M.class_name(m.group(1)) + ".java", node)
        if key == "workitem-label":
            match = re.match(r'^(\d+) "(.*)"$', node)
            if match:
                return f'{M.number(match.group(1))} "{M.summary(match.group(2))}"'
            return M.summary(node)
        return text(node)
    if isinstance(node, int) and not isinstance(node, bool) and 100000 <= node <= 99999999:
        return int(M.number(str(node)))
    return node


def sanitize_json(raw):
    return json.dumps(walk(json.loads(raw)), indent=4, ensure_ascii=True)


def sanitize_text(raw):
    return "\n".join(text(line) for line in raw.split("\n"))


def main():
    if not os.path.isdir(RAW):
        sys.exit(f"missing {RAW}")
    failures = 0
    for name in sorted(os.listdir(RAW)):
        if name.endswith(".err"):
            continue
        with open(os.path.join(RAW, name), encoding="utf-8-sig") as f:
            raw = f.read()
        try:
            result = sanitize_json(raw) if name.endswith(".json") else sanitize_text(raw)
        except json.JSONDecodeError as e:
            print(f"SKIP {name}: not JSON ({e})")
            continue
        with open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as f:
            f.write(result + ("\n" if not result.endswith("\n") else ""))
        bad = [line for line in result.split("\n") if BANNED.search(line)]
        status = "OK" if not bad else f"LEAK x{len(bad)}"
        failures += bool(bad)
        print(f"{status:9} {name} ({len(result)} chars)")
        for line in bad[:3]:
            print("    ", line.strip()[:140])
    sys.exit(1 if failures else 0)


main()
