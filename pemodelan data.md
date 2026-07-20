# IV.3.2.2 Perancangan Model Data

Perancangan model data bertujuan mendefinisikan struktur data yang mengalir dan dikelola di dalam Aplikasi Pendukung Eksperimen (APE). APE tidak menggunakan basis data relasional dalam operasionalnya. Proses klasifikasi berlangsung sepenuhnya di dalam memori (*in-memory*) secara *real-time*, sedangkan data yang perlu dipertahankan disimpan menggunakan SharedPreferences, yaitu mekanisme penyimpanan *key-value* bawaan Android dengan nilai berupa *string* berformat JSON. Oleh karena itu, perancangan model data disajikan dalam bentuk struktur data beserta aliran datanya, bukan diagram relasi antarentitas (ERD), karena tidak terdapat tabel maupun relasi basis data.

Data pada APE dikelompokkan menjadi dua jenis berdasarkan masa hidupnya. Pertama, data sementara (*in-memory*) yang hanya ada selama satu proses klasifikasi berlangsung, meliputi fitur leksikal dan hasil klasifikasi. Kedua, data permanen yang disimpan melalui SharedPreferences, meliputi riwayat domain yang diblokir dan preferensi model aktif. Aliran data antar struktur tersebut ditunjukkan pada Gambar IV.X.

**Gambar IV.X Aliran Data pada APE**

```
  URL mentah (String)
  dari Accessibility Service
           │
           ▼
  Normalisasi  ──►  domain ternormalisasi (String)
           │
    ┌──────┴───────────────┐
    │ (Random Forest)       │ (CNN-1D)
    ▼                       ▼
 LexicalFeatures        token karakter (IntArray)
 (7 fitur)                  │
    │                       │
    └──────────┬────────────┘
               ▼
       Inferensi model TFLite
               │
               ▼
     UrlClassificationResult        ◄── data sementara (in-memory)
               │
     (jika terklasifikasi pornografi & diblokir)
               ▼
     BlockedDomainRecord            ◄── disimpan permanen (SharedPreferences)
```

Penjelasan masing-masing struktur data inti diuraikan berikut ini.

## a. Lexical Features

Lexical Features merepresentasikan tujuh fitur leksikal yang diekstraksi dari domain ternormalisasi dan menjadi masukan model Random Forest. Struktur ini hanya dibentuk pada jalur Random Forest; pada jalur CNN-1D bernilai kosong (*null*) karena CNN-1D menerima masukan berupa token karakter. Struktur data Lexical Features ditunjukkan pada Tabel IV.X.

| Kode | Field | Tipe | Keterangan |
|------|-------|------|------------|
| F1 | domainLength | Int | Panjang karakter domain |
| F2 | numDigits | Int | Jumlah karakter angka |
| F3 | numDots | Int | Jumlah karakter titik |
| F4 | numDelimiters | Int | Jumlah karakter non-alfanumerik selain titik |
| F5 | suspiciousWordCount | Int | Jumlah kata sensitif (dari daftar) yang terdeteksi pada domain |
| F6 | digitToLetterRatio | Float | Rasio jumlah angka terhadap huruf |
| F7 | maxSequentialDigits | Int | Panjang deret angka berurutan terpanjang |

Pada saat inferensi, ketujuh fitur ini dikonversi menjadi larik *float* berdimensi [1 × 7] sebagai tensor masukan model Random Forest. Fitur bertipe Int tetap dikonversi menjadi Float karena antarmuka TFLite mengharuskan tipe data FLOAT32 yang seragam untuk seluruh elemen masukan.

## b. URL Classification Result

URL Classification Result merupakan struktur data keluaran dari proses inferensi, baik untuk model Random Forest maupun CNN-1D. Struktur ini bersifat sementara (*in-memory*) dan menjadi sumber informasi yang ditampilkan pada *overlay* hasil klasifikasi. Struktur datanya ditunjukkan pada Tabel IV.X.

| Field | Tipe | Keterangan |
|-------|------|------------|
| domain | String | Domain yang diklasifikasikan |
| extractedDomainName | String? | Nama domain yang menjadi masukan model |
| score | Float | Skor probabilitas keluaran model (0,0–1,0) |
| isAdult | Boolean | Hasil klasifikasi (pornografi atau aman) |
| inferenceTimeMs | Double | Waktu inferensi dalam milidetik |
| lexicalFeatures | LexicalFeatures? | Referensi fitur leksikal (hanya pada Random Forest, *null* pada CNN-1D) |

## c. Blocked Domain Record

Blocked Domain Record merepresentasikan satu catatan riwayat domain yang terklasifikasi sebagai pornografi dan diblokir. Struktur ini merupakan satu-satunya data hasil deteksi yang disimpan secara permanen, yaitu diserialisasi menjadi *string* JSON dan disimpan pada SharedPreferences dengan batas maksimum 1.000 catatan terbaru. Struktur datanya ditunjukkan pada Tabel IV.X.

| Field | Tipe | Keterangan |
|-------|------|------------|
| id | Long | *Identifier* unik catatan (*epoch millisecond*) |
| domain | String | Domain yang diblokir |
| timestamp | Long | Waktu pemblokiran |
| detectionTimeMs | Long | Waktu deteksi dalam milidetik |
| modelUsed | String | Model yang digunakan saat deteksi |
| score | Float | Skor prediksi |
| userConfirmed | Boolean | Status konfirmasi pengguna |
| lexicalFeatures | LexicalFeatures? | Fitur leksikal (hanya pada Random Forest) |

## d. Preferensi Model

Preferensi model merupakan data konfigurasi yang disimpan secara permanen melalui SharedPreferences untuk menyimpan pilihan model aktif dan status proteksi. Struktur datanya ditunjukkan pada Tabel IV.X.

| Key | Tipe | Nilai Awal | Keterangan |
|-----|------|------------|------------|
| selected_model_type | String | "CNN_1D" | Tipe model yang aktif |
| selected_cnn_file | String? | null | Nama berkas model CNN-1D terpilih |
| selected_rf_file | String? | null | Nama berkas model Random Forest terpilih |
| detection_enabled | Boolean | true | Status aktif/nonaktif proteksi |

Berdasarkan uraian di atas, ringkasan pengelolaan data APE menurut masa hidupnya disajikan pada Tabel IV.X.

| Struktur Data | Peran | Penyimpanan |
|---------------|-------|-------------|
| Lexical Features | Masukan model Random Forest | Sementara (*in-memory*) |
| URL Classification Result | Keluaran inferensi | Sementara (*in-memory*) |
| Blocked Domain Record | Riwayat domain diblokir | Permanen (SharedPreferences) |
| Preferensi Model | Konfigurasi model aktif | Permanen (SharedPreferences) |
