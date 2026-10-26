# ToolBox — Feedback & Competitor Notes

## 2026-10-25 — Competitor study (Pushpendra shared both Play links, 2026-10-24 night)
- SHZ ToolBox (ir.shahbaz.SHZToolBox_demo): 50+ tools, 41K+ ratings, 3.9 stars. Users love spirit level/sensor tools; reviews complain Arabic text leaks into English UI; collects personal info.
- ToolBox Pro (com.toolboxpro.app): 110+ tools, MD3, 51 ratings (4.6). Headline = Device Diagnostics: 23 hardware tests + wizard + PDF report (used-phone buyer niche), freemium.
- Ideas noted for ToolBox v1.3: (1) sensor/hardware tools (spirit level, compass, sound meter), (2) a diagnostics-style phone-check wizard — strong video topic for Pushpendra.

## 2026-10-25 — Pushpendra's liked feature (from SHZ screenshot he sent)
- TOP CATEGORY TABS: SHZ home has a category row right under the search bar — All | Health | Engineering | Entertainment | ... — one tap filters/jumps to that category's tools. Below it, sections (Favorite, Daily Tools, Engineering) with see-all arrows.
- Pushpendra explicitly liked this ("upar hi option hai alag alag categories ka") and asked to note it.
- PLAN for our ToolBox v1.3 home: add a category chips row under the search bar — All + Favorites + our categories (PDF, AI, Image, Video, Audio, Text, Calculator, + Sensors when added). Tapping a chip filters the tool grid to that category. Keep our bottom tabs as they are.
- STATUS 2026-10-25: DONE — built as ToolBox v1.3.0 (chips: All / ⭐ Favorites / PDF / AI / Image / Video / Audio / Text / Calc), all ship gates passed, sent to Pushpendra for device check. His plan: if approved, v1.3.0 = Play-final + GitHub next update. Sensor tools + diagnostics wizard remain queued after this.

## 2026-10-25 — v1.3.0 device check (Pushpendra's screenshot)
- Chips row renders and works correctly on device (All / Favorites / PDF / AI visible, home sections intact).
- HIS CATCH: duplication — the ⭐ Favorites chip (top) and the Favorites bottom tab now lead to the same place. He asked what can be done with the bottom bar ("neeche kya kar sakte hai, koi option hai").
- Options put to him: (1) drop the Favorites bottom tab (3 tabs: Home/All Tools/Settings) — recommended; (2) replace Favorites tab with a Recent tab; (3) leave as is.
- HIS PICK (2026-10-25 07:41): the reverse — remove the Favorites CHIP from the top row, keep the bottom tab. Shipped as v1.3.1 the same morning (all gates green); chips are categories-only now.
