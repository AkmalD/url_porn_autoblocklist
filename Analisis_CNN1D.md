# Analisis Model CNN-1D: URL Classifier v8 (Domain Focused)

## 1. Ringkasan Proyek
Model v8 dirancang untuk mengatasi kelemahan pada versi sebelumnya (v7), terutama ketergantungan berlebih pada Top-Level Domain (TLD) dan tingginya angka *false positive* pada brand ternama. Fokus utama versi ini adalah **Domain Name Awareness**, di mana model harus mampu mengenali karakteristik nama domain meskipun tanpa akhiran TLD.

## 2. Arsitektur Model (CNN-1D)
Model menggunakan pendekatan *Character-level Convolutional Neural Network* (CNN-1D) dengan optimasi *multi-scale feature extraction*:

- **Embedding Layer**: Mengonversi indeks karakter menjadi vektor padat berdimensi 32.
- **Multi-scale Convolutional**: Menggunakan tiga filter paralel dengan ukuran kernel berbeda (3, 5, dan 7). Hal ini memungkinkan model menangkap pola n-gram pendek (seperti "sex") hingga yang lebih panjang secara simultan.
- **Concatenation**: Menggabungkan fitur-fitur dari berbagai skala kernel.
- **Secondary Conv Layers**: Lapisan konvolusi tambahan (128 dan 64 filter) untuk mempelajari abstraksi fitur yang lebih kompleks.
- **Global Max Pooling**: Digunakan untuk membuat model bersifat *position-independent*, artinya kata kunci berbahaya akan terdeteksi di manapun posisinya dalam URL.
- **Fully Connected Layers**: Dense layer dengan 64 unit dan *dropout* untuk mencegah *overfitting*.
- **Output Layer**: Single neuron dengan aktivasi Sigmoid untuk klasifikasi biner (0: Normal, 1: Adult).

## 3. Strategi Pelatihan & Dataset
Beberapa teknik kunci yang diterapkan dalam v8:
- **Domain Name Augmentation**: Melatih model menggunakan domain lengkap (misal: `pornhub.com`) sekaligus nama intinya saja (misal: `pornhub`).
- **Critical Oversampling**: Melakukan *oversampling* (50x) pada brand populer (YouTube, Netflix, dll) dan kata kunci dewasa kritikal (XNXX, Bokep, dll) untuk memastikan akurasi tinggi pada kasus-kasus umum.
- **Balanced Dataset**: Menyeimbangkan jumlah sampel antara kelas Normal (dari Tranco dataset) dan Adult (dari UT1 domains).
- **Tokenization**: Menggunakan 41 karakter dasar (`a-z`, `0-9`, `.`, `-`, `_`) dengan *character-level indexing*.

## 4. Evaluasi & Target Performa
Target utama yang ingin dicapai:
- **Normal Detection**: > 98%
- **Adult Detection**: > 95%
- **Specific Case**:
    - `xnxx` (tanpa TLD) -> skor > 0.7
    - `youtube` (tanpa TLD) -> skor < 0.3

## 5. Implementasi ke Android (TFLite)
Model dikonversi ke format `.tflite` menggunakan *float16 quantization* untuk efisiensi memori pada perangkat mobile. Hal-hal yang perlu disesuaikan pada kode Android (`UrlClassifier.kt`):
- **MAX_LEN**: Harus sesuai dengan nilai persentil ke-95 dari data training (tercatat dalam metadata).
- **VOCAB_SIZE**: 41-43 karakter (termasuk PAD dan UNK).
- **Threshold**: Direkomendasikan 0.5, atau 0.7 untuk deteksi yang lebih konservatif.

## 6. Kesimpulan
Strategi v8 dengan fokus pada *Domain Name* dan arsitektur *Multi-scale CNN* secara signifikan meningkatkan ketahanan model terhadap variasi TLD dan mengurangi ketergantungan pada *whitelist* hardcode.
