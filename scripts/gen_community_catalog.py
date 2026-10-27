#!/usr/bin/env python3
"""Generates community/themes.json for the Onyx Keyboard community archive.

Replicates OnyxThemeCodec.encode() exactly:
  ONYX1.<base64url(utf8(json))>
  json = {"v":1,"name":...,"surfaceTop":"AARRGGBB",...,"particle":...,
          "shape":{"r":<int>,"s":<int>}}
so the app's decoder (and the OLD v1 palette-only decoders) accept it.
"""
import base64
import json
import os

# (name, by, surfaceTop, surfaceBottom, keyFill, keyText, accent, glow, particle, radius, space)
ENTRIES = [
    # A subset of the bundled shelf travels in the online catalog too, so
    # users can also see refreshed versions with shape info.
    ("نيون العاصمة", "فريق Onyx", 0xFF0A0912, 0xFF161024, 0xFF221A38, 0xFFF1EAFF, 0xFFC084FC, 0xFF9333EA, 0xFFC084FC, 18, 1),
    ("ميناء الليل", "فريق Onyx", 0xFF03121A, 0xFF072433, 0xFF0E3548, 0xFFE0F5FF, 0xFF22D3EE, 0xFF0891B2, 0xFF22D3EE, 26, 0),
    ("صحراء منتصف الليل", "فريق Onyx", 0xFF120E06, 0xFF20180B, 0xFF33270F, 0xFFFFF3D9, 0xFFFBBF24, 0xFFB45309, 0xFFFBBF24, 8, 0),
    ("زمرّد الرمال", "فريق Onyx", 0xFF071510, 0xFF0C241A, 0xFF123B2C, 0xFFE8FFF5, 0xFF34D399, 0xFF059669, 0xFF34D399, 14, 2),
    ("بحر القرمزي", "فريق Onyx", 0xFF170A0E, 0xFF27101A, 0xFF3D1526, 0xFFFFEAF1, 0xFFFB7185, 0xFFBE123C, 0xFFFB7185, 14, 0),
    ("بنفسج المجرّة", "فريق Onyx", 0xFF0B0620, 0xFF160D38, 0xFF241457, 0xFFF0EAFF, 0xFFA78BFA, 0xFF6D28D9, 0xFFA78BFA, 22, 0),
    ("قهوة الصباح", "فريق Onyx", 0xFFF7F0E8, 0xFFEFE2D4, 0xFFFFFFFF, 0xFF3E2C1E, 0xFFA16207, 0xFFD6A756, 0xFFC08A3E, 14, 1),
    ("سماوي هادئ", "فريق Onyx", 0xFFEFF6FD, 0xFFDDEBFA, 0xFFFFFFFF, 0xFF1D3348, 0xFF0284C7, 0xFF7CC4EF, 0xFF42A5F5, 14, 0),
    ("خوخة الورد", "فريق Onyx", 0xFFFBF0F2, 0xFFF6DEE4, 0xFFFFFFFF, 0xFF3E222C, 0xFFDB2777, 0xFFF58EA8, 0xFFEC6E9E, 14, 0),
    ("نعناع الحديقة", "فريق Onyx", 0xFFEFFAF3, 0xFFDCF2E7, 0xFFFFFFFF, 0xFF1D3A2B, 0xFF059669, 0xFF6EE7B7, 0xFF34D399, 14, 0),
]


def encode(name, by, *vals):
    st, sb, kf, kt, ac, gl, pa, radius, space = vals
    payload = {
        "v": 1,
        "name": name,
        "surfaceTop": f"{st:08X}",
        "surfaceBottom": f"{sb:08X}",
        "keyFill": f"{kf:08X}",
        "keyText": f"{kt:08X}",
        "accent": f"{ac:08X}",
        "glow": f"{gl:08X}",
        "particle": f"{pa:08X}",
        "shape": {"r": radius, "s": space},
    }
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    b64 = base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")
    return "ONYX1." + b64


def main():
    themes = [{"code": encode(*e), "by": e[1]} for e in ENTRIES]
    doc = {
        "updated": "2026-10-27",
        "repo": "https://github.com/CTO-DRS/OnyxKeyboard",
        "how_to_contribute": "شارك رمز ONYX1. من محرر الثيمات ثم افتح PR يضيفه إلى themes[]",
        "themes": themes,
    }
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "community", "themes.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"wrote {os.path.normpath(out)} with {len(themes)} themes")
    print("first code:", themes[0]["code"][:60] + "...")


if __name__ == "__main__":
    main()
