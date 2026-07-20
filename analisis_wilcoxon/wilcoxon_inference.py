"""
Uji signifikansi waktu inferensi CNN-1D vs Random Forest
menggunakan Wilcoxon signed-rank test (uji berpasangan, non-parametrik).

Cara pakai:
    pip install pandas scipy numpy openpyxl
    python wilcoxon_inference.py inference_times.xlsx

Format file input (Excel .xlsx atau .csv), satu baris per URL:
    url            | time_cnn_ms | time_rf_ms
    pornhub.com    | 0.842       | 0.231
    google.com     | 0.790       | 0.205
    ...

Catatan:
    - Beri label "WARMUP" pada kolom url untuk baris yang ingin dibuang
      (mis. URL pertama yang kena bias warm-up TFLite).
    - Pastikan semua domain valid (bukan search query yang di-skip aplikasi).
"""

import sys
import numpy as np
import pandas as pd
from scipy.stats import wilcoxon, norm


def load(path: str) -> pd.DataFrame:
    if path.lower().endswith((".xlsx", ".xls")):
        return pd.read_excel(path)
    return pd.read_csv(path)


def main(path: str, alpha: float = 0.05) -> None:
    df = load(path)

    # buang baris warm-up jika ditandai "WARMUP" di kolom url
    if "url" in df.columns:
        df = df[df["url"].astype(str).str.upper() != "WARMUP"].copy()

    cnn = df["time_cnn_ms"].astype(float).values
    rf = df["time_rf_ms"].astype(float).values
    diff = cnn - rf

    # mode 'exact' lebih akurat untuk n kecil; aproksimasi normal untuk n besar
    mode = "exact" if len(cnn) < 25 else "approx"
    stat, p = wilcoxon(cnn, rf, alternative="two-sided", mode=mode)

    # effect size r = Z / sqrt(N)
    z = norm.isf(p / 2)
    r = z / np.sqrt(len(cnn))
    if r < 0.1:
        size = "diabaikan"
    elif r < 0.3:
        size = "kecil"
    elif r < 0.5:
        size = "sedang"
    else:
        size = "besar"

    print("=" * 50)
    print("UJI WILCOXON SIGNED-RANK — WAKTU INFERENSI")
    print("=" * 50)
    print(f"n pasangan (URL)      : {len(cnn)}")
    print(f"median CNN-1D         : {np.median(cnn):.4f} ms")
    print(f"median Random Forest  : {np.median(rf):.4f} ms")
    print(f"median selisih (C-RF) : {np.median(diff):.4f} ms")
    print(f"rata-rata CNN-1D      : {np.mean(cnn):.4f} ms")
    print(f"rata-rata RF          : {np.mean(rf):.4f} ms")
    print("-" * 50)
    print(f"statistik W           : {stat:.4f}")
    print(f"p-value               : {p:.6e}")
    print(f"effect size r         : {r:.4f} ({size})")
    print("-" * 50)
    if p < alpha:
        lebih_cepat = "Random Forest" if np.median(cnn) > np.median(rf) else "CNN-1D"
        print(f"KESIMPULAN: p < {alpha} -> H0 DITOLAK.")
        print(f"Ada perbedaan SIGNIFIKAN. {lebih_cepat} lebih cepat (median lebih rendah).")
    else:
        print(f"KESIMPULAN: p >= {alpha} -> H0 GAGAL DITOLAK.")
        print("Tidak ada perbedaan waktu inferensi yang signifikan.")
    print("=" * 50)


if __name__ == "__main__":
    file_path = sys.argv[1] if len(sys.argv) > 1 else "inference_times.xlsx"
    main(file_path)
