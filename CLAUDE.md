# CLAUDE.md — Dokumentasi Perubahan & Arsitektur Proyek

## Ringkasan Proyek

**url_porn_autoblocklist** adalah aplikasi Android berbasis Accessibility Service yang memantau URL di browser secara real-time dan memblokir konten pornografi menggunakan model ML TensorFlow Lite.

- **Platform**: Android (Kotlin + Jetpack Compose)
- **DI**: Dagger Hilt
- **ML Runtime**: TensorFlow Lite
- **Model tersedia**: CNN-1D dan Random Forest

---

## Arsitektur Sistem

```
Browser (URL berubah)
        ↓
UrlBlockerAccessibilityService   ← memantau AccessibilityEvent
        ↓
ClassifierManager                ← memilih classifier aktif
        ↓
UrlClassifier (CNN-1D)           atau   RandomForestClassifier (RF)
        ↓                                       ↓
Tokenisasi karakter domain              Ekstraksi 7 fitur leksikal
        ↓                                       ↓
CNN1D.tflite / url_classifier.tflite    url_classifier_rf.tflite / RandomForest.tflite
        ↓
score > 0.7 ?
   ├── Ya  → BlockOverlayActivity (layar merah blokir)
   └── Tidak → DetectionInfoOverlayActivity (debug info, 15 detik)
```

---

## File Model di Assets

| File | Ukuran | Keterangan |
|------|--------|------------|
| `CNN1D.tflite` | ~75 KB | Model CNN-1D utama |
| `url_classifier.tflite` | ~274 KB | Fallback CNN-1D |
| `url_classifier_rf.tflite` | ~128 KB | Model RF utama (aktif) |
| `RandomForest.tflite` | ~2.9 MB | Fallback RF — bisa error GATHER jika fitur tidak cocok |

---

## Perubahan yang Dilakukan

### 1. `.idea/gradle.xml` — Perbaikan JDK Gradle

**Masalah**: Setelah clone dari GitHub, muncul error `Invalid Gradle JDK configuration found` karena path JDK di-hardcode ke mesin lama (`C:\Gradle\gradle-8.14`).

**Perbaikan**:
- Hapus `gradleHome` yang hardcoded
- Set `gradleJvm` ke path JDK embedded Android Studio yang benar

**Fungsi**: Agar project bisa di-build di mesin manapun tanpa perlu mengedit konfigurasi manual.

---

### 2. `AndroidManifest.xml` — Registrasi Activity Baru

**Perubahan**: Menambahkan deklarasi `DetectionInfoOverlayActivity`:

```xml
<activity
    android:name=".presentation.ui.DetectionInfoOverlayActivity"
    android:exported="false"
    android:theme="@style/Theme.URLAutoBlocklist"
    android:excludeFromRecents="true"
    android:taskAffinity=""
    android:launchMode="singleInstance" />
```

**Fungsi**: Mendaftarkan activity overlay debug ke sistem Android agar bisa dipanggil dari Accessibility Service.

---

### 3. `DetectionInfoOverlayActivity.kt` — Activity Debug Overlay (FILE BARU)

**Lokasi**: `app/src/main/java/com/lawanpmo/autoblocklist/presentation/ui/DetectionInfoOverlayActivity.kt`

**Fungsi**: Menampilkan overlay informasi klasifikasi untuk URL yang **aman** (tidak diblokir) selama 15 detik. Digunakan untuk verifikasi apakah pipeline deteksi URL berjalan.

**Konten overlay**:
- Kartu Info Klasifikasi: domain, score, model aktif, waktu inferensi
- Kartu Feature Extraction (khusus Random Forest): tabel F1–F7 dengan nilai aktual
- Kartu Keterangan Fitur: legenda penjelasan tiap fitur
- Countdown 15 detik lalu tutup otomatis
- Warna hijau = URL aman, merah = terdeteksi porno

**Extra yang diterima dari Intent**:

| Extra | Tipe | Keterangan |
|-------|------|------------|
| `EXTRA_DOMAIN` | String | URL asli yang dideteksi |
| `EXTRA_EXTRACTED_DOMAIN_NAME` | String | Domain setelah normalisasi |
| `EXTRA_SCORE` | Float | Skor probabilitas (0.0–1.0) |
| `EXTRA_IS_ADULT` | Boolean | Hasil klasifikasi |
| `EXTRA_MODEL` | String | Nama model yang dipakai |
| `EXTRA_INFERENCE_TIME_MS` | Long | Waktu inferensi dalam ms |
| `EXTRA_LEXICAL_FEATURES` | LexicalFeatures (Parcelable) | Fitur RF (null jika CNN) |

**Composable di dalamnya**: `SectionCard`, `FeatureTableHeader`, `FeatureRow`, `LegendRow`, `InfoRow`

> **Catatan**: `SectionCard` menggunakan `content: @Composable () -> Unit` (bukan `Column.() -> Unit`) karena `Column` adalah fungsi, bukan class/interface.

---

### 4. `IUrlClassifier.kt` — Tambah Field `extractedDomainName`

**Lokasi**: `app/src/main/java/com/lawanpmo/autoblocklist/domain/repository/IUrlClassifier.kt`

**Perubahan**: Menambahkan field `extractedDomainName: String? = null` pada `UrlClassificationResult`.

**Fungsi**: Menyimpan nama domain yang benar-benar dimasukkan ke model (setelah normalisasi URL) agar bisa ditampilkan di overlay debug untuk transparansi proses.

---

### 5. `UrlClassifier.kt` — Isi `extractedDomainName`

**Lokasi**: `app/src/main/java/com/lawanpmo/autoblocklist/data/classifier/UrlClassifier.kt`

**Perubahan**: Mengisi `extractedDomainName = domainName` pada hasil `UrlClassificationResult`.

**Fungsi**: Classifier CNN-1D sekarang ikut melaporkan domain yang diekstrak agar overlay debug bisa menampilkannya.

---

### 6. `ClassifierManager.kt` — Fallback Path Model

**Lokasi**: `app/src/main/java/com/lawanpmo/autoblocklist/data/classifier/ClassifierManager.kt`

**Masalah**: Kode lama mencari file `cnn1d_model.tflite` dan `rf_model.tflite` yang tidak ada di assets. Inferensi selalu menghasilkan score 0.0 dan waktu 0ms.

**Perbaikan**: Mengubah dari path tunggal menjadi **daftar fallback**:

```kotlin
val modelPaths = when (modelType) {
    MLModelType.CNN_1D       -> listOf("CNN1D.tflite", "url_classifier.tflite")
    MLModelType.RANDOM_FOREST -> listOf("url_classifier_rf.tflite", "RandomForest.tflite")
}
val loaded = modelPaths.any { path -> newClassifier.loadModel(path) }
```

**Fungsi**: Model dimuat dari path pertama yang berhasil. Untuk RF, `url_classifier_rf.tflite` (127 KB, bekerja normal) menjadi prioritas utama, `RandomForest.tflite` (2.9 MB, rentan error GATHER) sebagai fallback.

---

### 7. `RandomForestClassifier.kt` — Perbaikan Besar

**Lokasi**: `app/src/main/java/com/lawanpmo/autoblocklist/data/classifier/RandomForestClassifier.kt`

#### 7a. Pindah ekstraksi fitur ke luar try-catch

**Masalah**: `extractLexicalFeatures()` dipanggil di dalam blok `try`. Ketika inferensi gagal dan masuk `catch`, `features` tidak tersedia → overlay debug tidak menampilkan tabel fitur.

**Perbaikan**: Pindahkan pemanggilan `extractLexicalFeatures()` ke sebelum blok `try`, sehingga tersedia di semua jalur return:

```kotlin
val features = extractLexicalFeatures(fullDomain)  // di luar try-catch
val interp = interpreter
if (interp == null) return UrlClassificationResult(..., lexicalFeatures = features)
return try {
    // inference
    UrlClassificationResult(..., lexicalFeatures = features)
} catch (e: Throwable) {
    UrlClassificationResult(..., lexicalFeatures = features)  // tetap ada!
}
```

#### 7b. Perbaikan Feature Mismatch F5 dan F7 (KRITIS)

**Masalah**: Terjadi mismatch antara format fitur saat training Python dan saat inferensi Android:

| Fitur | Training Python | Android (sebelum) | Android (sesudah) |
|-------|----------------|-------------------|--------------------|
| F5 `suspicious_word_count` | INTEGER count (0, 1, 2, ...) | BOOLEAN (0 atau 1) | INTEGER count ✓ |
| F7 `max_sequential_digits` | INTEGER max length (0, 1, 2, 3, ...) | BOOLEAN (0 atau 1) | INTEGER max length ✓ |

Mismatch ini menyebabkan model `RandomForest.tflite` melempar error: `gather index out of bounds. Node number 13 (GATHER) failed to invoke`.

**Perbaikan `extractLexicalFeatures()`**:

```kotlin
// F5: SEBELUM (salah)
val hasSuspicious = SUSPICIOUS_WORDS.any { lower.contains(it) }

// F5: SESUDAH (benar — sesuai training)
val suspiciousWordCount = SUSPICIOUS_WORDS.count { lower.contains(it) }

// F7: SEBELUM (salah)
val hasSequential = Regex("\\d{3,}").containsMatchIn(lower)

// F7: SESUDAH (benar — sesuai training)
val maxSequentialDigits = Regex("\\d+").findAll(lower).maxOfOrNull { it.value.length } ?: 0
```

**Perbaikan `buildInputBuffer()`**:

```kotlin
// SEBELUM
if (features.hasSuspiciousWords) 1.0f else 0.0f,
if (features.hasSequentialDigits) 1.0f else 0.0f

// SESUDAH
features.suspiciousWordCount.toFloat(),
features.maxSequentialDigits.toFloat()
```

---

### 8. `BlockedDomainRecord.kt` — Perubahan Field `LexicalFeatures`

**Lokasi**: `app/src/main/java/com/lawanpmo/autoblocklist/data/model/BlockedDomainRecord.kt`

**Perubahan**: Mengubah tipe field di data class `LexicalFeatures`:

```kotlin
// SEBELUM
val hasSuspiciousWords: Boolean,  // F5
val hasSequentialDigits: Boolean  // F7

// SESUDAH
val suspiciousWordCount: Int,     // F5 — jumlah kata mencurigakan
val maxSequentialDigits: Int      // F7 — panjang max digit berurutan
```

**Fungsi**: Menyinkronkan model data Android dengan format fitur yang digunakan saat training. `@Parcelize` dan `@Serializable` tetap bekerja karena tipe berubah ke Int (masih primitive).

---

### 9. `UrlBlockerAccessibilityService.kt` — Tambah Debug Overlay untuk URL Aman

**Lokasi**: `app/src/main/java/com/lawanpmo/autoblocklist/service/UrlBlockerAccessibilityService.kt`

**Perubahan**:
- Menambahkan pemanggilan `showDetectionInfoOverlay()` pada branch `else` (URL aman) di `processUrl()`
- Menambahkan method `showDetectionInfoOverlay()` yang membangun Intent dan mengirimkan semua data hasil klasifikasi, termasuk `lexicalFeatures` (Parcelable)

**Fungsi**: Setiap URL yang terdeteksi tapi diklasifikasikan aman akan memunculkan overlay debug 15 detik. Berguna untuk memverifikasi bahwa pipeline deteksi URL berjalan dan untuk mengamati nilai fitur yang dihasilkan.

---

### 10. `DetectionInfoOverlayActivity.kt`, `EvaluationScreen.kt`, `HomeScreen.kt` — Update Tampilan Fitur

**Masalah**: Ketiga file masih mereferensikan field lama `hasSuspiciousWords` (Boolean) dan `hasSequentialDigits` (Boolean) yang sudah dihapus.

**Perbaikan tampilan**:

| Sebelum | Sesudah |
|---------|---------|
| "Ada" / "Tidak" | "2 kata" / "0 kata" |
| "Ya (1)" / "Tidak (0)" | "3 digit" / "0 digit" |
| Vector: `..., 1, ..., 0]` | Vector: `..., 2, ..., 3]` |

**Fungsi**: UI sekarang menampilkan nilai integer aktual seperti yang dikirim ke model, sehingga lebih informatif dan jujur terhadap proses inferensi yang sebenarnya.

---

## Fitur Leksikal Random Forest (F1–F7)

Sesuai notebook training `rf_training.ipynb` dan `tuning_randomforest_claude.ipynb`:

| Kode | Nama Python | Nama Android | Deskripsi | Tipe |
|------|-------------|-------------|-----------|------|
| F1 | `domain_length` | `domainLength` | Panjang string domain | Int |
| F2 | `digit_count` | `numDigits` | Jumlah karakter angka | Int |
| F3 | `dot_count` | `numDots` | Jumlah titik `.` | Int |
| F4 | `delimiter_count` | `numDelimiters` | Jumlah karakter non-alphanumeric selain `.` | Int |
| F5 | `suspicious_word_count` | `suspiciousWordCount` | **Jumlah** kata mencurigakan yang ditemukan | Int |
| F6 | `digit_letter_ratio` | `digitToLetterRatio` | Rasio digit terhadap huruf | Float |
| F7 | `max_sequential_digits` | `maxSequentialDigits` | **Panjang maksimal** urutan digit berurutan | Int |

> **Catatan training**: Tidak ada scaling yang diterapkan — `X_train_scaled = X_train` (comment "tidak perlu scaling"). `StandardScaler` diimport tetapi tidak digunakan.

---

## Alur Inferensi CNN-1D

1. URL dinormalisasi → ambil domain inti saja (contoh: `play.google.com` → `google`)
2. Setiap karakter di-encode dengan `CHAR_TO_IDX` (a=2..z=27, 0=28..9=37, `.`=38, `-`=39, `_`=40, PAD=0, UNK=1)
3. Sequence di-pad atau dipotong ke panjang `modelInputLen` (dideteksi dari model saat load, default 34)
4. Buffer dikirim sebagai INT32 atau FLOAT32 sesuai dtype input tensor model (adaptif)
5. Masukkan ke `url_classifier.tflite` → output skor probabilitas porno
6. Threshold: `score > 0.7` → adult

## Alur Inferensi Random Forest

1. URL dinormalisasi → domain penuh (contoh: `youporn.com`)
2. Ekstrak 7 fitur leksikal (F1–F7) sebagai float array
3. Buat `ByteBuffer` dengan ukuran `modelInputElements * 4` byte
4. Run interpreter → output skor probabilitas
5. Threshold: `score > 0.7` → adult

---

## Cara Build

```bash
# Pastikan Android Studio dengan JDK 17 terinstall
# Buka project di Android Studio → Sync Gradle → Run
```

**Syarat**: Android Studio dengan embedded JDK. Jangan hardcode path `gradleHome` di `.idea/gradle.xml`.

---

## Perubahan Sesi Terakhir (Diagnostik Akurasi Model)

### 11. `UrlClassifier.kt` — Adaptive Input Type & Length

**Masalah**: Hampir semua URL (termasuk URL porno) diklasifikasikan aman. Dugaan kuat: model TFLite mengharapkan input INT32 (untuk Embedding lookup) tapi kode mengirim FLOAT32, menyebabkan exception yang tertangkap silently → score=0 → selalu aman.

**Perubahan**:
- Tambah field `modelInputLen` dan `modelInputIsInt` yang diisi saat `loadModel()`
- `loadModel()` sekarang log `dataType()` dan `shape()` dari model — wajib dicek di logcat
- `tokenize()` menggunakan `modelInputLen` (dari model aktual, bukan hardcode 34)
- `prepareInputBuffer()` adaptif: kirim `putInt()` jika model INT32, atau `putFloat()` jika FLOAT32

**Fungsi**: Kode sekarang otomatis menyesuaikan format buffer dengan kebutuhan model yang di-load, bukan asumsi.

### 12. `ClassifierManager.kt` — Urutan Prioritas Model

| Model | Prioritas | Alasan |
|-------|-----------|--------|
| CNN-1D | `url_classifier.tflite` (273 KB) dulu | Lebih besar = arsitektur lebih lengkap (multi-scale CNN v8) |
| CNN-1D | `CNN1D.tflite` (75 KB) fallback | Mungkin versi lebih lama/simpel |
| RF | `url_classifier_rf.tflite` (128 KB) dulu | Bekerja normal tanpa GATHER error |
| RF | `RandomForest.tflite` (2.9 MB) fallback | Punya GATHER error saat inferensi, bukan saat load |

> **Penting**: GATHER error terjadi saat **inferensi**, bukan saat load. Jadi jika model berhasil di-load, semua inferensi tetap diarahkan ke model itu. Pastikan model yang di-load memang bekerja benar.

---

## Catatan Penting untuk Developer

1. **Cek logcat tag `UrlClassifier`** setelah build: lihat `Input dtype` — jika INT32 dan kode sebelumnya kirim FLOAT32, itulah penyebab score selalu 0.
2. **Cek logcat tag `UrlClassifier`**: bandingkan `Model MAX_LEN` vs `Code MAX_LEN` — harus sama.
3. Jika melatih ulang model RF, pastikan F5 (`suspicious_word_count`) dan F7 (`max_sequential_digits`) di dataset Python menggunakan nilai **integer count**, bukan boolean.
4. `RandomForest.tflite` gagal dengan error `gather index out of bounds` saat inferensi. Perlu konversi TFLite ulang menggunakan pipeline yang sesuai.
5. Tidak ada notebook training untuk CNN-1D di repo ini. Jika `url_classifier.tflite` tidak akurat, perlu dibuat notebook training CNN-1D baru.
6. `DetectionInfoOverlayActivity` adalah overlay **debug** — pertimbangkan untuk mematikannya di build release.
7. `LexicalFeatures` adalah `@Parcelize` dan `@Serializable` — perubahan field harus backward-compatible jika ada data yang disimpan di database.
