# משנה יומית

אפליקציית Android ללימוד משנה יומי, רציף, מברכות ועד עוקצין.

| מסמך | תוכן |
|---|---|
| [SPEC.md](SPEC.md) | אפיון |
| [PLAN.md](PLAN.md) | ארכיטקטורה ושלבי פיתוח |
| [DESIGN.md](DESIGN.md) | תכנון מסכים ועיצוב |
| [design/prototype.html](design/prototype.html) | אב טיפוס לחיץ |

## מבנה
- `core/` — לוגיקה ב-Kotlin טהור: אינדקס המשניות, תוכנית הלימוד, רצפים, זריחה ולוח עברי. נבדק בלי Android SDK.
- `app/` — אפליקציית Android (Compose, Room).
- `tools/build_content.py` — בונה את מאגר המשניות והפירושים מהייצוא הפומבי של Sefaria
  (טקסט: תורת אמת) ואת `Catalog.kt`.

## פקודות
```sh
# בדיקות הליבה (לא צריך Android SDK)
MISHNA_CORE_ONLY=1 ./gradlew :core:test

# APK לבדיקה (צריך Android SDK)
./gradlew :app:assembleDebug

# בנייה מחדש של מאגר התוכן
python3 tools/build_content.py
```

כל push בונה APK ב-GitHub Actions. הקובץ נמצא תחת Artifacts בריצה (`mishna-debug-apk`).
