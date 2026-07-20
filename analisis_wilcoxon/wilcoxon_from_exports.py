"""
Wilcoxon signed-rank test langsung dari 2 file CSV hasil EXPORT aplikasi.

Aplikasi mengekspor satu CSV per model (kolom: url, model, label,
inference_time_ms, ...). Skrip ini menggabungkan CSV CNN + CSV RF berdasarkan
'url', membuang baris yang di-skip model (waktu ~0) + baris warm-up pertama,
lalu menjalankan uji Wilcoxon.

Cara pakai:
    pip install pandas scipy numpy
    python wilcoxon_from_exports.py eval_CNN1D.csv eval_RF.csv

Tanpa argumen, ia mencari 2 file .csv terbaru di folder ini secara otomatis.
"""

import sys
import glob
import os
import numpy as np
import pandas as pd
from scipy.stats import wilcoxon, norm

SKIP_THRESHOLD_MS = 0.05   # waktu < ini dianggap di-skip model (bukan inferensi nyata)
# Aplikasi sudah melakukan warm-up internal + median per URL, jadi baris pertama
# TIDAK perlu dibuang lagi. Set True hanya bila pakai data lama tanpa warm-up.
DROP_FIRST_ROW = False


def model_name_from_file(path):
    """Rapikan nama model dari nama file: eval_<model>_<timestamp>.csv -> <model>."""
    base = os.path.splitext(os.path.basename(path))[0]
    if base.startswith("eval_"):
        base = base[len("eval_"):]
    # buang sufiks timestamp _YYYYMMDD_HHMMSS bila ada
    parts = base.split("_")
    while parts and (parts[-1].isdigit()):
        parts.pop()
    return "_".join(parts) or os.path.basename(path)


def load_one(path):
    df = pd.read_csv(path)
    df["inference_time_ms"] = pd.to_numeric(df["inference_time_ms"], errors="coerce")
    model = str(df["model"].iloc[0]) if "model" in df.columns else model_name_from_file(path)
    if DROP_FIRST_ROW and len(df) > 1:
        print(f"  [{model}] buang baris warm-up: {df['url'].iloc[0]}")
        df = df.iloc[1:]
    return df[["url", "inference_time_ms"]].rename(
        columns={"inference_time_ms": model}
    ), model


def main(path_a, path_b):
    print("Memuat data...")
    a, model_a = load_one(path_a)
    b, model_b = load_one(path_b)

    merged = pd.merge(a, b, on="url", how="inner")
    n_before = len(merged)

    # buang baris yang di-skip salah satu model (waktu ~0)
    skipped = merged[(merged[model_a] < SKIP_THRESHOLD_MS) |
                     (merged[model_b] < SKIP_THRESHOLD_MS)]
    if len(skipped):
        print(f"\nDibuang {len(skipped)} URL yang di-skip model (waktu < {SKIP_THRESHOLD_MS} ms):")
        print(skipped[["url", model_a, model_b]].to_string(index=False))
    merged = merged[(merged[model_a] >= SKIP_THRESHOLD_MS) &
                    (merged[model_b] >= SKIP_THRESHOLD_MS)]

    x = merged[model_a].values
    y = merged[model_b].values

    mode = "exact" if len(x) < 25 else "approx"
    stat, p = wilcoxon(x, y, alternative="two-sided", mode=mode)
    z = norm.isf(p / 2)
    r = z / np.sqrt(len(x))
    size = ("diabaikan" if r < 0.1 else "kecil" if r < 0.3 else
            "sedang" if r < 0.5 else "besar")

    print("\n" + "=" * 56)
    print("UJI WILCOXON SIGNED-RANK — WAKTU INFERENSI")
    print("=" * 56)
    print(f"Model A               : {model_a}")
    print(f"Model B               : {model_b}")
    print(f"Pasangan dipakai      : {len(x)} (dari {n_before} URL cocok)")
    print("-" * 56)
    print(f"median {model_a:14.14s}: {np.median(x):.4f} ms")
    print(f"median {model_b:14.14s}: {np.median(y):.4f} ms")
    print(f"mean   {model_a:14.14s}: {np.mean(x):.4f} ms")
    print(f"mean   {model_b:14.14s}: {np.mean(y):.4f} ms")
    print("-" * 56)
    print(f"statistik W           : {stat:.4f}")
    print(f"Z                     : {z:.4f}")
    print(f"p-value               : {p:.6e}")
    print(f"effect size r         : {r:.4f} ({size})")
    print("-" * 56)
    if p < 0.05:
        cepat = model_a if np.median(x) < np.median(y) else model_b
        print(f"KESIMPULAN: p < 0.05 -> H0 DITOLAK. Perbedaan SIGNIFIKAN.")
        print(f"           {cepat} lebih cepat (median lebih rendah).")
    else:
        print("KESIMPULAN: p >= 0.05 -> H0 gagal ditolak. Tidak signifikan.")
    print("=" * 56)


if __name__ == "__main__":
    if len(sys.argv) >= 3:
        main(sys.argv[1], sys.argv[2])
    else:
        here = os.path.dirname(os.path.abspath(__file__))
        files = sorted(glob.glob(os.path.join(here, "eval_*.csv")),
                       key=os.path.getmtime, reverse=True)
        if len(files) < 2:
            print("Letakkan 2 file CSV hasil export (eval_*.csv) di folder ini,")
            print("atau jalankan: python wilcoxon_from_exports.py file_cnn.csv file_rf.csv")
            sys.exit(1)
        print(f"Auto-pakai 2 CSV terbaru:\n  {os.path.basename(files[0])}\n  {os.path.basename(files[1])}\n")
        main(files[1], files[0])
