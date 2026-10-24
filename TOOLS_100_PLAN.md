# ToolBox v7 — 100+ Tools Expansion Plan
_Pushpendra ka order 2026-10-23 22:36: "59 nhi, aur add kro — 100+; PDF se related jitne ho sake, 30-35, important sab."_
_Saath me v7 me: final naam "ToolBox: All Tools, AI & PDF" + naya home (category-section tile grid)._
_Niyam wahi: har tool asal me kaam karega; jo basic/had wala hai, UI me sach likha jayega — nakli tool nahi banega._

## PDF: 12 → 35 (+23)
Maujooda 12: Compress, Merge, Split, JPG to PDF, PDF to Images, Rotate, Reorder, Delete Pages, Extract Pages, Watermark, Page Numbers, Protect.
Naye 23:
1. **Compress PDF to KB** — target size (form upload wala); pages ko image bana kar quality ghata-badha kar nishana; UI me sach: result image-based hoga.
2. **Unlock PDF** — sirf tab jab password pata ho / sirf restrictions hon (crack nahi — saaf likha jayega).
3. **PDF to Text** — text nikaal kar .txt + copy.
4. **Scanned PDF to Text (OCR)** — scan wale PDF ke liye on-device OCR (ML Kit), Hindi+English.
5. **Text to PDF** — likha/paste text → saaf PDF.
6. **PDF to Word** — .docx banta hai; formatting basic rahegi (UI me likha jayega).
7. **Extract Images from PDF** — PDF ke andar ki asli images alag files me.
8. **PDF Info & Metadata** — pages, size, title, author, version, dates.
9. **Edit Metadata** — title/author/subject/keywords badlo.
10. **Remove Metadata** — saari hidden jaankari saaf.
11. **Resize Pages** — A4 / Letter / A5 me page size badlo.
12. **Crop PDF Pages** — chaaro taraf se margin kaato.
13. **Grayscale PDF** — rang → black & white (print kharcha bachane); image-based result, UI me note.
14. **Sign PDF** — photo/draw ki hui signature page par lagao. Sach: ye visual stamp hai, digital/cryptographic signature nahi.
15. **Add Image to PDF** — logo/photo stamp har page ya chuni jagah.
16. **Header & Footer** — har page upar/neeche apna text.
17. **PDF N-Up** — 2/4/6 pages ek sheet par (notes print ke liye).
18. **Flatten PDF** — form/annotations ko page me pakka karo.
19. **Repair PDF** — kharab PDF ko dobara save karke theek karne ki koshish (jo khulega wahi bachega — sach likha jayega).
20. **Reverse Page Order** — peeche se aage.
21. **Insert Blank Page** — beech me khaali page jodo.
22. **Split by Size** — badi PDF ko MB ke hisaab se tukdo me.
23. **Fill PDF Form** — form wale PDF ke text fields bharo (jin PDFs me asli form fields hon).

## Image: 12 → 18 (+6)
24. **Image to Exact KB** — target KB par photo (form wala hero tool).
25. **Batch Image Compressor** — ek saath kai photos compress.
26. **Collage Maker** — grid collage (2–9 photos).
27. **Photo Filters** — basic rang filters pack.
28. **EXIF Remover** — location/camera jaankari hatao (privacy).
29. **Photo Watermark** — photo par apna naam/logo text.

## Video: 8 → 11 (+3)
30. **Video Speed Changer** (0.5x–2x).
31. **Video Rotate & Flip**.
32. **Video Crop / Aspect Ratio** (9:16, 1:1, 16:9 — reels/shorts ke liye).

## Audio: 6 → 9 (+3)
33. **Voice Recorder** — record karke save/share.
34. **Audio Compressor** — size ghatao (bitrate).
35. **Audio Fade In / Out**.

## Text: 8 → 12 (+4)
36. **Password Generator** — majboot password (lambaai/symbols options).
37. **QR & Barcode Generator** — text/link se QR + barcode banao.
38. **Text Encrypt / Decrypt** — password se AES lock (note bhejane ke kaam).
39. **Lorem Ipsum Generator** — dummy text (designers ke liye).

## Calculators: 6 → 10 (+4)
40. **BMI Calculator**.
41. **SIP Calculator** — mutual fund return estimate (India me khoob khoja jata hai).
42. **Tip Calculator**.
43. **Date Difference Calculator** — do tareekhon ke beech din/mahine/saal.

## AI: 7 (waisa hi)
Background Remover, OCR, Face Blur, Photo Labeler, QR/Barcode Scanner, Offline Translator, Voice to Text.

## Kul hisaab
PDF 35 + AI 7 + Image 18 + Video 11 + Audio 9 + Text 12 + Calc 10 = **102 tools** ✅ (100 se 2 upar ka buffer — koi tool build me atke to bhi 100+ bane rahenge; final ginti jo asal me ship hogi wahi listing me likhi jayegi.)

## Build kram
1. PDF batch (core `PdfTools2.kt` + screens)
2. Baaki categories batch
3. Naya home + naam/branding + Tools.kt registry (require count update)
4. Compile → manual APK pipeline → dex scan → v7 delivery (direct link + size)
