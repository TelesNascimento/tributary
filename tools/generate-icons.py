import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "icons")
META = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "META-INF")
BRAND = os.path.join(os.path.dirname(__file__), "..", "docs", "branding")

PALETTE = {
    "neutral": ("#6C707E", "#CED0D6"),
    "blue": ("#3574F0", "#548AF7"),
    "green": ("#208A3C", "#5FB865"),
    "red": ("#E55765", "#DB5C5C"),
    "amber": ("#D9822B", "#F0A45B"),
}

S = 'stroke="{c}" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" fill="none"'


def svg(size, body, color_key, dark):
    c = PALETTE[color_key][1 if dark else 0]
    body = body.replace("{c}", c)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
            f'viewBox="0 0 {size} {size}">{body}</svg>\n')


ICONS16 = {
    "workspace": ("neutral", f'<rect x="2.75" y="3.75" width="10.5" height="8.5" rx="1.5" {S}/>'
                             f'<path d="M2.75 6.75h10.5" {S}/>'),
    "stream": ("blue", f'<path d="M2 5.5c2-2 3.5 2 6 0s4 2 6 0" {S}/>'
                       f'<path d="M2 10.5c2-2 3.5 2 6 0s4 2 6 0" {S}/>'),
    "component": ("neutral", f'<path d="M8 2.5l5 2.5v6l-5 2.5-5-2.5V5z" {S}/>'
                             f'<path d="M3 5l5 2.5L13 5M8 7.5v6" {S}/>'),
    "changeSet": ("neutral", f'<circle cx="8" cy="8" r="2.5" {S}/><path d="M8 2v3.5M8 10.5V14" {S}/>'),
    "changeSetCurrent": ("blue", '<circle cx="8" cy="8" r="3" fill="{c}"/>'
                                 f'<path d="M8 2v3M8 11v3" {S}/>'),
    "baseline": ("neutral", f'<path d="M4 14V2.5" {S}/><path d="M4 3h8l-2 2.75L12 8.5H4" {S}/>'),
    "snapshot": ("neutral", f'<rect x="2.75" y="4.75" width="10.5" height="7.5" rx="1.5" {S}/>'
                            f'<circle cx="8" cy="8.5" r="2" {S}/><path d="M6 4.5l.75-1.5h2.5L10 4.5" {S}/>'),
    "workItem": ("neutral", f'<rect x="3.75" y="3.25" width="8.5" height="10" rx="1.5" {S}/>'
                            f'<path d="M6.5 2.5h3v1.5h-3zM6 7.5h4M6 10h4" {S}/>'),
    "incoming": ("green", f'<path d="M8 2.5v7M5 6.75l3 3 3-3" {S}/><path d="M3 12.5h10" {S}/>'),
    "outgoing": ("blue", f'<path d="M8 9.5v-7M5 5.25l3-3 3 3" {S}/><path d="M3 12.5h10" {S}/>'),
    "conflict": ("red", f'<path d="M8 2.5l6 10.5H2z" {S}/><path d="M8 6.5v3" {S}/>'
                        '<circle cx="8" cy="11.25" r=".85" fill="{c}"/>'),
    "suspended": ("amber", f'<path d="M6 3.5v9M10 3.5v9" {S}/>'),
}

TOOLWINDOW = ("neutral", f'<path d="M3 1.75c0 3.75 3.5 4.5 3.5 7" {S}/>'
                          f'<path d="M10 1.75c0 3.75-3.5 4.5-3.5 7" {S}/>'
                          f'<path d="M6.5 8.75v3.5" {S}/><circle cx="6.5" cy="8.75" r="1.5" {S}/>')

os.makedirs(OUT, exist_ok=True)
for name, (color, body) in ICONS16.items():
    for dark in (False, True):
        with open(os.path.join(OUT, f"{name}{'_dark' if dark else ''}.svg"), "w", encoding="utf-8") as f:
            f.write(svg(16, body, color, dark))
for dark in (False, True):
    with open(os.path.join(OUT, f"toolWindow{'_dark' if dark else ''}.svg"), "w", encoding="utf-8") as f:
        f.write(svg(13, TOOLWINDOW[1], TOOLWINDOW[0], dark))

for name in ("pluginIcon.svg", "pluginIcon_dark.svg"):
    src = os.path.join(BRAND, name)
    with open(src, encoding="utf-8") as r, open(os.path.join(META, name), "w", encoding="utf-8") as w:
        w.write(r.read())

names = sorted(ICONS16) + ["toolWindow"]
REL = "../../src/main/resources/icons/"
rows = "".join(
    f'<div class="c"><img src="{REL}{n}.svg" width="32"><img src="{REL}{n}.svg" width="16"><span>{n}</span></div>'
    for n in names)
rows_d = rows.replace('.svg"', '_dark.svg"')
html = ("<!doctype html><meta charset='utf-8'><style>body{margin:0;font:12px system-ui;display:flex}"
        ".p{padding:14px;display:grid;grid-template-columns:repeat(4,150px);gap:10px}"
        ".l{background:#fff;color:#222}.d{background:#2b2d30;color:#ccc}"
        ".c{display:flex;gap:8px;align-items:center}</style>"
        f"<div class='p l'>{rows}</div><div class='p d'>{rows_d}</div>")
with open(os.path.join(BRAND, "icons-preview.html"), "w", encoding="utf-8") as f:
    f.write(html)
print(f"wrote {len(ICONS16) * 2 + 2} icons")
