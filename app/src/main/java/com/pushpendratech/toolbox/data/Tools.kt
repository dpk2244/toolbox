package com.pushpendratech.toolbox.data

enum class Category(val title: String, val emoji: String, val blurb: String) {
    PDF("PDF Tools", "📄", "Compress, merge, split, protect & more"),
    IMAGE("Image Tools", "🖼️", "Compress, convert, crop & edit photos"),
    VIDEO("Video Tools", "🎬", "Compress, trim, convert videos"),
    AUDIO("Audio Tools", "🎵", "Trim, merge & convert audio"),
    TEXT("Text Tools", "✍️", "Count, clean & convert text"),
    CALC("Calculators", "🧮", "GST, EMI, age, units & more"),
    SENSORS("Sensor Tools", "🧭", "Level, compass, sound, light & more"),
    AI("AI Tools", "🤖", "On-device AI — offline & private")
}

data class Tool(
    val id: String, val category: Category, val name: String, val description: String,
    val popular: Boolean = false
)

object Tools {
    val all: List<Tool> = listOf(
        // PDF — 12
        Tool("pdf_compress", Category.PDF, "Compress PDF", "Reduce PDF file size", popular = true),
        Tool("pdf_merge", Category.PDF, "Merge PDF", "Combine multiple PDFs into one", popular = true),
        Tool("pdf_split", Category.PDF, "Split PDF", "Split by page range into a new PDF"),
        Tool("jpg_to_pdf", Category.PDF, "JPG to PDF", "Turn images into a PDF document", popular = true),
        Tool("pdf_to_images", Category.PDF, "PDF to Images", "Export PDF pages as PNG images"),
        Tool("pdf_rotate", Category.PDF, "Rotate PDF", "Rotate pages 90°, 180° or 270°"),
        Tool("pdf_reorder", Category.PDF, "Reorder PDF Pages", "Change the order of pages"),
        Tool("pdf_delete_pages", Category.PDF, "Delete PDF Pages", "Remove selected pages"),
        Tool("pdf_extract", Category.PDF, "Extract PDF Pages", "Save selected pages as a new PDF"),
        Tool("pdf_watermark", Category.PDF, "Add Watermark", "Add text watermark to pages"),
        Tool("pdf_page_numbers", Category.PDF, "Add Page Numbers", "Stamp page numbers on a PDF"),
        Tool("pdf_protect", Category.PDF, "Protect PDF", "Encrypt a PDF with a password"),
        // PDF — v7 batch (+23 → 35)
        Tool("pdf_compress_kb", Category.PDF, "Compress PDF to KB", "Shrink a PDF to an exact KB target size"),
        Tool("pdf_unlock", Category.PDF, "Unlock PDF", "Remove password protection (known password)"),
        Tool("pdf_to_text", Category.PDF, "PDF to Text", "Extract the text stored in a PDF"),
        Tool("pdf_ocr_text", Category.PDF, "Scanned PDF to Text (OCR)", "Read scanned PDFs with on-device OCR"),
        Tool("pdf_text_to_pdf", Category.PDF, "Text to PDF", "Turn typed or pasted text into a PDF"),
        Tool("pdf_to_word", Category.PDF, "PDF to Word", "Convert PDF text to a Word (.docx) file"),
        Tool("pdf_extract_images", Category.PDF, "Extract PDF Images", "Save images embedded in a PDF"),
        Tool("pdf_info", Category.PDF, "PDF Info & Metadata", "Pages, size, title, author, dates & more"),
        Tool("pdf_edit_metadata", Category.PDF, "Edit PDF Metadata", "Change title, author, subject, keywords"),
        Tool("pdf_remove_metadata", Category.PDF, "Remove PDF Metadata", "Clear hidden document information"),
        Tool("pdf_resize_pages", Category.PDF, "Resize PDF Pages", "Change page size to A4, Letter or A5"),
        Tool("pdf_crop", Category.PDF, "Crop PDF Pages", "Trim margins from every page"),
        Tool("pdf_grayscale", Category.PDF, "Grayscale PDF", "Convert a PDF to black & white"),
        Tool("pdf_sign", Category.PDF, "Sign PDF", "Stamp your signature image on a page"),
        Tool("pdf_add_image", Category.PDF, "Add Image to PDF", "Stamp a logo or photo on pages"),
        Tool("pdf_header_footer", Category.PDF, "Header & Footer", "Add header/footer text to every page"),
        Tool("pdf_nup", Category.PDF, "PDF N-Up", "Put 2, 4 or 6 pages on one sheet"),
        Tool("pdf_flatten", Category.PDF, "Flatten PDF", "Bake form values into the pages"),
        Tool("pdf_repair", Category.PDF, "Repair PDF", "Rebuild a damaged PDF (best effort)"),
        Tool("pdf_reverse", Category.PDF, "Reverse Page Order", "Last page first, first page last"),
        Tool("pdf_insert_blank", Category.PDF, "Insert Blank Page", "Add a blank page anywhere"),
        Tool("pdf_split_size", Category.PDF, "Split PDF by Size", "Split into parts under a size limit"),
        Tool("pdf_fill_form", Category.PDF, "Fill PDF Form", "Fill a PDF form's text fields"),
        // IMAGE — 12
        Tool("img_compress", Category.IMAGE, "Image Compressor", "Reduce image size with quality control", popular = true),
        Tool("img_resize", Category.IMAGE, "Image Resizer", "Resize by pixels or percentage"),
        Tool("jpg_to_png", Category.IMAGE, "JPG to PNG", "Convert JPEG images to PNG"),
        Tool("png_to_jpg", Category.IMAGE, "PNG to JPG", "Convert PNG to JPEG, flatten transparency"),
        Tool("webp_convert", Category.IMAGE, "WebP Converter", "Convert images to / from WebP"),
        Tool("img_crop", Category.IMAGE, "Image Cropper", "Crop with free or fixed aspect ratios"),
        Tool("img_rotate", Category.IMAGE, "Image Rotator", "Rotate by 90°, 180° or 270°"),
        Tool("img_flip", Category.IMAGE, "Image Flipper", "Flip horizontal or vertical"),
        Tool("img_to_pdf", Category.IMAGE, "Image to PDF", "Multiple images into one PDF", popular = true),
        Tool("passport_photo", Category.IMAGE, "Passport Photo Tool", "Crop & size photos (35×45mm etc.)"),
        Tool("color_picker", Category.IMAGE, "Image Color Picker", "Tap a pixel, get RGB & HEX"),
        Tool("exif_viewer", Category.IMAGE, "EXIF Metadata Viewer", "See camera, size & date info"),
        // IMAGE — v7 batch (+6 → 18)
        Tool("img_exact_kb", Category.IMAGE, "Image to Exact KB", "Compress a photo to an exact target KB (forms)", popular = true),
        Tool("img_batch_compress", Category.IMAGE, "Batch Image Compressor", "Compress many photos at once"),
        Tool("img_collage", Category.IMAGE, "Collage Maker", "Grid or strip collage from 2–9 photos"),
        Tool("img_filters", Category.IMAGE, "Photo Filters", "B&W, Sepia, Vintage, Cool, Warm & more"),
        Tool("img_exif_remover", Category.IMAGE, "EXIF Remover", "Strip camera & GPS metadata from a photo"),
        Tool("img_watermark", Category.IMAGE, "Photo Watermark", "Stamp your name or text on a photo"),
        // VIDEO — 8
        Tool("video_compress", Category.VIDEO, "Video Compressor", "Re-encode at a lower bitrate", popular = true),
        Tool("video_trim", Category.VIDEO, "Video Trimmer", "Cut a segment without re-encoding"),
        Tool("video_to_mp3", Category.VIDEO, "Video to MP3", "Extract the audio track (M4A/AAC — see note)"),
        Tool("video_to_gif", Category.VIDEO, "Video to GIF", "Turn a short clip into an animated GIF"),
        Tool("video_frames", Category.VIDEO, "Extract Video Frames", "Save a frame at any timestamp as an image"),
        Tool("video_mute", Category.VIDEO, "Mute Video", "Remove the audio track, keep video as-is"),
        Tool("video_resolution", Category.VIDEO, "Change Video Resolution", "Re-encode to 720p / 480p / 360p"),
        Tool("video_estimator", Category.VIDEO, "Video Size Estimator", "Estimate output size from bitrate & duration"),
        // VIDEO — v7 batch (+3 → 11)
        Tool("video_speed", Category.VIDEO, "Video Speed Changer", "Play at 0.5×–2× — picture & sound stay in sync"),
        Tool("video_rotate_flip", Category.VIDEO, "Video Rotate & Flip", "Rotate 90°, 180°, 270° or mirror a video"),
        Tool("video_crop_aspect", Category.VIDEO, "Video Crop / Aspect Ratio", "Centre-crop to 9:16, 1:1, 4:5 or 16:9"),
        // AUDIO — 6
        Tool("audio_trim", Category.AUDIO, "Audio Trimmer", "Cut a segment without re-encoding"),
        Tool("audio_merge", Category.AUDIO, "Audio Merger", "Join multiple audio files into one"),
        Tool("audio_convert", Category.AUDIO, "Audio Converter", "Convert audio to WAV or M4A (AAC)"),
        Tool("audio_volume", Category.AUDIO, "Audio Volume Changer", "Boost or lower volume (WAV / PCM)"),
        Tool("audio_speed", Category.AUDIO, "Audio Speed Changer", "Change speed — resample (pitch shifts)"),
        Tool("audio_inspector", Category.AUDIO, "Audio Format Inspector", "Format, duration, sample rate, bitrate"),
        // AUDIO — v7 batch (+3 → 9)
        Tool("audio_recorder", Category.AUDIO, "Voice Recorder", "Record voice & save as M4A"),
        Tool("audio_compress", Category.AUDIO, "Audio Compressor", "Re-encode audio at a lower bitrate (M4A)"),
        Tool("audio_fade", Category.AUDIO, "Audio Fade In / Out", "Smooth volume ramps at the start & end"),
        // TEXT — 8
        Tool("word_counter", Category.TEXT, "Word Counter", "Words, characters, lines & reading time", popular = true),
        Tool("char_counter", Category.TEXT, "Character Counter", "Characters with / without spaces"),
        Tool("case_converter", Category.TEXT, "Case Converter", "UPPER, lower, Title & Sentence case"),
        Tool("dedup_lines", Category.TEXT, "Remove Duplicate Lines", "Keep only the first occurrence"),
        Tool("remove_spaces", Category.TEXT, "Remove Extra Spaces", "Trim & collapse repeated spaces"),
        Tool("text_reverser", Category.TEXT, "Text Reverser", "Reverse characters or reverse lines"),
        Tool("text_slug", Category.TEXT, "Text to Slug", "Make URL-friendly slugs"),
        Tool("find_replace", Category.TEXT, "Find and Replace", "Search & replace with options"),
        // TEXT — v7 batch (+4 → 12)
        Tool("password_generator", Category.TEXT, "Password Generator", "Strong random passwords, made on-device"),
        Tool("qr_generator", Category.TEXT, "QR & Barcode Generator", "Create QR, Code 128 & EAN-13 codes"),
        Tool("text_encrypt", Category.TEXT, "Text Encrypt / Decrypt", "Lock text with AES-256 and your password"),
        Tool("lorem_ipsum", Category.TEXT, "Lorem Ipsum Generator", "Dummy placeholder text for designs"),
        // CALC — 6
        Tool("calc_percentage", Category.CALC, "Percentage Calculator", "%, change & difference", popular = true),
        Tool("calc_gst", Category.CALC, "GST Calculator", "Add / remove GST at any rate (India)"),
        Tool("calc_emi", Category.CALC, "EMI Calculator", "Monthly EMI, interest & total payment"),
        Tool("calc_discount", Category.CALC, "Discount Calculator", "Discount amount & final price"),
        Tool("calc_age", Category.CALC, "Age Calculator", "Exact age from date of birth"),
        Tool("calc_units", Category.CALC, "Unit Converter", "Length, weight, temp, area, volume, data"),
        // CALC — v7 batch (+4 → 10)
        Tool("calc_bmi", Category.CALC, "BMI Calculator", "Body mass index & healthy weight range"),
        Tool("calc_sip", Category.CALC, "SIP Calculator", "Mutual fund SIP maturity estimate"),
        Tool("calc_tip", Category.CALC, "Tip Calculator", "Tip amount & split the bill"),
        Tool("calc_date_diff", Category.CALC, "Date Difference Calculator", "Days, months & years between two dates"),
        // AI — on-device (ML Kit), batch 1
        Tool("ai_bg_remover", Category.AI, "AI Background Remover", "Remove photo background (portraits)", popular = true),
        Tool("ai_ocr", Category.AI, "AI Image to Text (OCR)", "Extract text — English & Hindi", popular = true),
        Tool("ai_face_blur", Category.AI, "AI Face Blur", "Detect faces & blur them for privacy"),
        // AI — batch 2
        Tool("ai_labeler", Category.AI, "AI Photo Labeler", "What is in this photo? AI labels"),
        Tool("ai_barcode_scan", Category.AI, "AI QR & Barcode Scanner", "Scan codes from a photo"),
        Tool("ai_translator", Category.AI, "AI Offline Translator", "Translate text — offline after one-time pack", popular = true),
        Tool("ai_voice_text", Category.AI, "AI Voice to Text", "Bolkar likho — speech to text"),
        // SENSORS — v1.4.0 (first sensor batch; Pushpendra's pick from the competitor study)
        Tool("sensor_level", Category.SENSORS, "Spirit Level", "Bubble level with digital tilt angles", popular = true),
        Tool("sensor_compass", Category.SENSORS, "Compass", "Magnetic compass with degree readout"),
        Tool("sensor_sound", Category.SENSORS, "Sound Meter", "Measure ambient noise in decibels (estimate)"),
        Tool("sensor_light", Category.SENSORS, "Light Meter", "Measure brightness in lux"),
        Tool("sensor_metal", Category.SENSORS, "Metal Detector", "Find metal with the magnetic sensor"),
        Tool("sensor_pressure", Category.SENSORS, "Barometer", "Air pressure & estimated altitude")
    )
    init { require(all.size == 108) { "ToolBox must contain exactly 108 tools, found ${all.size}" } }
    fun byId(id: String): Tool = all.first { it.id == id }
    fun byCategory(c: Category): List<Tool> = all.filter { it.category == c }
    fun search(q: String): List<Tool> {
        val needle = q.trim().lowercase(); if (needle.isEmpty()) return emptyList()
        return all.filter { it.name.lowercase().contains(needle) || it.description.lowercase().contains(needle) || it.category.title.lowercase().contains(needle) }
    }
    val popular: List<Tool> get() = all.filter { it.popular }
}
