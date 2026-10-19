Android app to scan covers of theses, seminars, CDs and books with **100% on-device OCR**, review and correct the extracted fields, and export everything to a single JSON file.

### ✨ Highlights

- **On-device OCR** (ML Kit, model bundled) — no Google Play Services and **no internet permission**
- **Camera scanning and gallery import** — capture covers one by one or batch-import existing photos
- **Editable fields** — title, authors, advisors/tutors, institution, faculty/area, date/place, other texts, notes and the full OCR text
- **Single JSON export** — all documents in one file, through the system save dialog
- **Team workflow** — import the JSON exports from several phones and merge them (already known documents are skipped); `scripts/consolidar.py` turns the exports into CSV or an SQLite database
- **Spanish and English covers** — roles (Author, Advisor, Supervisor…), work types (Thesis, Dissertation…) and dates in both languages; a book with ISBN/Editorial is no longer mistaken for a thesis
- **Safe storage** — every saved document is written immediately to the app's internal storage, and the pending review queue survives an accidental close or a dead battery

### 📱 Install

- Requires Android 7.0 (API 24) or later
- Universal APK for `arm64-v8a` and `armeabi-v7a` (covers virtually all phones; x86 emulators are not supported in release builds)
- `adb install -r CoverScan-<version>.apk`, or copy the APK to the phone and open it

Signed release build — the same certificate will sign future updates.

---
