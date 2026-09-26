#!/usr/bin/env python3
"""Build the bundled Mishna content from the Sefaria public export.

Outputs:
  app/src/main/assets/content/mishna.db   SQLite DB read by Room (prepackaged)
  core/src/main/kotlin/.../Catalog.kt      Tractate names and chapter sizes for the pure-Kotlin core

Usage: python3 tools/build_content.py
"""
import json
import os
import re
import sqlite3
import sys
import urllib.parse
import urllib.request

EXPORT = "https://storage.googleapis.com/sefaria-export/json/"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_PATH = os.path.join(ROOT, "app/src/main/assets/content/mishna.db")
CATALOG_KT = os.path.join(ROOT, "core/src/main/kotlin/app/mishna/core/content/Catalog.kt")
CACHE = os.path.join(ROOT, "tools/.cache")
DB_VERSION = 1

# (Sefaria title, Hebrew name) in study order, grouped by seder.
SEDARIM = [
    ("Zeraim", "זרעים", [
        ("Berakhot", "ברכות"), ("Peah", "פאה"), ("Demai", "דמאי"), ("Kilayim", "כלאים"),
        ("Sheviit", "שביעית"), ("Terumot", "תרומות"), ("Maasrot", "מעשרות"),
        ("Maaser Sheni", "מעשר שני"), ("Challah", "חלה"), ("Orlah", "ערלה"), ("Bikkurim", "ביכורים")]),
    ("Moed", "מועד", [
        ("Shabbat", "שבת"), ("Eruvin", "עירובין"), ("Pesachim", "פסחים"), ("Shekalim", "שקלים"),
        ("Yoma", "יומא"), ("Sukkah", "סוכה"), ("Beitzah", "ביצה"), ("Rosh Hashanah", "ראש השנה"),
        ("Ta'anit", "תענית"), ("Megillah", "מגילה"), ("Moed Katan", "מועד קטן"), ("Chagigah", "חגיגה")]),
    ("Nashim", "נשים", [
        ("Yevamot", "יבמות"), ("Ketubot", "כתובות"), ("Nedarim", "נדרים"), ("Nazir", "נזיר"),
        ("Sotah", "סוטה"), ("Gittin", "גיטין"), ("Kiddushin", "קידושין")]),
    ("Nezikin", "נזיקין", [
        ("Bava Kamma", "בבא קמא"), ("Bava Metzia", "בבא מציעא"), ("Bava Batra", "בבא בתרא"),
        ("Sanhedrin", "סנהדרין"), ("Makkot", "מכות"), ("Shevuot", "שבועות"), ("Eduyot", "עדיות"),
        ("Avodah Zarah", "עבודה זרה"), ("Avot", "אבות"), ("Horayot", "הוריות")]),
    ("Kodashim", "קדשים", [
        ("Zevachim", "זבחים"), ("Menachot", "מנחות"), ("Chullin", "חולין"), ("Bekhorot", "בכורות"),
        ("Arakhin", "ערכין"), ("Temurah", "תמורה"), ("Keritot", "כריתות"), ("Meilah", "מעילה"),
        ("Tamid", "תמיד"), ("Middot", "מידות"), ("Kinnim", "קינים")]),
    ("Tahorot", "טהרות", [
        ("Kelim", "כלים"), ("Oholot", "אהלות"), ("Negaim", "נגעים"), ("Parah", "פרה"),
        ("Tahorot", "טהרות"), ("Mikvaot", "מקוואות"), ("Niddah", "נדה"), ("Makhshirin", "מכשירין"),
        ("Zavim", "זבים"), ("Tevul Yom", "טבול יום"), ("Yadayim", "ידים"), ("Oktzin", "עוקצין")]),
]

# Pirkei Avot chapter 6 (Kinyan Torah) is a baraita, not part of the Mishna.
AVOT_CHAPTERS = 5

BARTENURA = 1
IKAR_TYT = 2


def title(tractate):
    return "Pirkei Avot" if tractate == "Avot" else f"Mishnah {tractate}"


def path(categories, book):
    parts = [*categories, book, "Hebrew", "merged.json"]
    return EXPORT + "/".join(urllib.parse.quote(p) for p in parts)


def fetch(url):
    os.makedirs(CACHE, exist_ok=True)
    cached = os.path.join(CACHE, re.sub(r"[^A-Za-z0-9]+", "_", url[len(EXPORT):]))
    if not os.path.exists(cached):
        with urllib.request.urlopen(url) as r:
            data = r.read()
        with open(cached, "wb") as f:
            f.write(data)
    with open(cached, encoding="utf-8") as f:
        return json.load(f)["text"]


TAG = re.compile(r"<(?!/?b>)[^>]+>")
INVISIBLE = re.compile(r"[‎‏‪-‮﻿]")


def clean(s):
    s = INVISIBLE.sub("", TAG.sub("", s))
    return re.sub(r"\s+", " ", s).strip()


def main():
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    db = sqlite3.connect(DB_PATH)
    # Must match the Room entities in app/.../data/content exactly.
    db.executescript("""
        CREATE TABLE `masechet` (`id` INTEGER NOT NULL, `seder` INTEGER NOT NULL, `name` TEXT NOT NULL,
            `firstIndex` INTEGER NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`id`));
        CREATE TABLE `mishna` (`globalIndex` INTEGER NOT NULL, `masechetId` INTEGER NOT NULL,
            `perek` INTEGER NOT NULL, `mishna` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`globalIndex`));
        CREATE TABLE `commentary` (`globalIndex` INTEGER NOT NULL, `source` INTEGER NOT NULL,
            `text` TEXT NOT NULL, PRIMARY KEY(`globalIndex`, `source`));
    """)

    catalog = []
    gi = 0
    mid = 0
    missing = []
    for seder_idx, (seder, seder_he, tractates) in enumerate(SEDARIM):
        cat = ["Mishnah", f"Seder {seder}"]
        tractate_list = []
        for name, he in tractates:
            t = title(name)
            text = fetch(path(cat, t))
            if name == "Avot":
                text = text[:AVOT_CHAPTERS]
            comms = {}
            for src, (group, author) in {BARTENURA: ("Rishonim on Mishnah", "Bartenura"),
                                         IKAR_TYT: ("Acharonim on Mishnah", "Ikar Tosafot Yom Tov")}.items():
                try:
                    # Commentary titles drop the apostrophe (Ta'anit -> Taanit).
                    comms[src] = fetch(path(["Mishnah", group, author, f"Seder {seder}"], f"{author} on {t.replace(chr(39), '')}"))
                except Exception as e:  # noqa: BLE001
                    missing.append(f"{author} on {t}: {e}")
                    comms[src] = []
            first = gi
            sizes = []
            for p, perek in enumerate(text):
                sizes.append(len(perek))
                for m, mtext in enumerate(perek):
                    db.execute("INSERT INTO mishna VALUES (?,?,?,?,?)", (gi, mid, p + 1, m + 1, clean(mtext)))
                    for src, c in comms.items():
                        try:
                            parts = c[p][m]
                        except IndexError:
                            parts = []
                        if isinstance(parts, str):
                            parts = [parts]
                        body = "\n".join(x for x in (clean(s) for s in parts) if x)
                        if body:
                            db.execute("INSERT INTO commentary VALUES (?,?,?)", (gi, src, body))
                    gi += 1
            db.execute("INSERT INTO masechet VALUES (?,?,?,?,?)", (mid, seder_idx, he, first, gi - first))
            tractate_list.append((he, sizes))
            print(f"{he}: {gi - first}", file=sys.stderr)
            mid += 1
        catalog.append((seder_he, tractate_list))

    db.execute(f"PRAGMA user_version = {DB_VERSION}")
    db.commit()
    db.execute("VACUUM")
    db.close()
    write_catalog(catalog, gi)
    print(f"Total mishnayot: {gi}", file=sys.stderr)
    for m in missing:
        print("MISSING " + m, file=sys.stderr)


def write_catalog(catalog, total):
    lines = [
        "// Generated by tools/build_content.py. Do not edit.",
        "package app.mishna.core.content",
        "",
        "internal val SEDARIM: List<Seder> = listOf(",
    ]
    for seder_he, tractates in catalog:
        lines.append(f'    Seder("{seder_he}", listOf(')
        for he, sizes in tractates:
            lines.append(f'        Tractate("{he}", intArrayOf({", ".join(map(str, sizes))})),')
        lines.append("    )),")
    lines += [")", "", f"internal const val TOTAL_MISHNAYOT: Int = {total}", ""]
    os.makedirs(os.path.dirname(CATALOG_KT), exist_ok=True)
    with open(CATALOG_KT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


if __name__ == "__main__":
    main()
