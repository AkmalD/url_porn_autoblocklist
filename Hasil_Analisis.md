# Hasil Analisis Proyek: URL AutoBlocklist

## 1. Analisis Struktur Folder
Proyek ini terdiri dari dua komponen utama: backend (API) dan frontend (Android App).

### Backend (Folder `api/`)
- Menggunakan bahasa pemrograman **Go (Golang)**.
- **Struktur Internal (`api/internal/`):**
    - `config/`: Pengaturan konfigurasi aplikasi.
    - `database/`: Koneksi database dan seeder data.
    - `handler/`: Layer untuk menangani request HTTP.
    - `repository/`: Layer untuk interaksi langsung dengan database (Domain & Report).
    - `service/`: Layer logika bisnis (Blocklist Service).
    - `middleware/`: Autentikasi dan pengamanan API.
    - `model/`: Definisi struktur data (Domain).

### Frontend (Folder `app/`)
- Aplikasi **Android** berbasis **Kotlin**.
- **Struktur Utama (`app/src/main/java/com/lawanpmo/autoblocklist/`):**
    - `data/`: Implementasi classifier URL (TFLite) dan repository data.
    - `domain/`: Abstraksi interface dan logika bisnis inti.
    - `presentation/`: Layer UI (Compose) dan ViewModel.
    - `service/`: `UrlBlockerAccessibilityService.kt` yang merupakan komponen kunci untuk memantau URL secara real-time.
- **Assets:** `url_classifier.tflite` digunakan untuk deteksi on-device.

## 2. Analisis Tujuan Project
Tujuan utama dari proyek ini adalah menyediakan sistem pemblokiran URL otomatis yang cerdas untuk membantu pengguna menghindari konten negatif (khususnya konten dewasa/PMO).
- **Deteksi Cerdas**: Tidak hanya bergantung pada daftar hitam statis, tetapi menggunakan model machine learning (CNN-1D) untuk menganalisis pola nama domain.
- **Privasi & Kecepatan**: Dengan menggunakan TFLite di sisi aplikasi, deteksi dapat dilakukan secara lokal tanpa harus mengirim semua riwayat browsing ke server.
- **Reporting & Sync**: Backend Go berfungsi untuk sinkronisasi daftar domain yang diketahui dan pelaporan temuan baru.

## 3. Analisis Fungsi dari Pengembangan
Pengembangan ini berfungsi sebagai alat bantu kesehatan digital dengan fitur-fitur berikut:
1. **Real-time Monitoring**: Memantau URL yang diakses melalui browser menggunakan Android Accessibility Service.
2. **On-Device Classification**: Mengklasifikasikan URL secara instan menggunakan model CNN-1D.
3. **Automated Blocking**: Mengalihkan atau memblokir akses jika URL terdeteksi sebagai konten negatif.
4. **Centralized Database**: Mengelola database domain yang terverifikasi melalui API terpusat.
5. **Scalability**: Memungkinkan pembaruan model dan daftar blokir secara dinamis melalui infrastruktur cloud/backend.
