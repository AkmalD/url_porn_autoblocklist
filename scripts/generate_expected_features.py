"""Bangkitkan app/src/test/resources/expected_features.csv untuk unit test fitur RF.

Menghitung 7 fitur leksikal untuk 200 domain pertama Revisi/test.csv dengan fungsi
acuan dari notebook pelatihan dan daftar kata app/src/main/assets/rf/kata_sensitif_android.txt.

Jalankan dari root proyek:  python scripts/generate_expected_features.py
"""
import csv
import re

TEST_CSV = 'Revisi/test.csv'
WORDS_TXT = 'app/src/main/assets/rf/kata_sensitif_android.txt'
OUT_CSV = 'app/src/test/resources/expected_features.csv'
N_ROWS = 200

with open(WORDS_TXT, encoding='utf-8') as f:
    SUSPICIOUS_WORDS = {line.strip().lower() for line in f if line.strip()}


def normalize_domain(url):
    url = url.lower().strip()
    for prefix in ['https://', 'http://', 'www.']:
        if url.startswith(prefix):
            url = url[len(prefix):]
    return url.split('/')[0].split('?')[0].split('#')[0].split(':')[0]


def extract_features(domain):
    lower = domain.lower()
    digits = sum(c.isdigit() for c in lower)
    letters = sum(c.isalpha() for c in lower)
    grams = {lower[i:i+n] for n in range(2, 7) for i in range(len(lower) - n + 1)}
    return [
        len(domain),
        digits,
        lower.count('.'),
        sum(1 for c in lower if not c.isalnum() and c != '.'),
        len(grams & SUSPICIOUS_WORDS),
        digits / letters if letters > 0 else float(digits),
        max((len(s) for s in re.findall(r'\d+', lower)), default=0),
    ]


with open(TEST_CSV, encoding='utf-8', newline='') as f:
    domains = [row['domain'] for row in csv.DictReader(f)][:N_ROWS]

with open(OUT_CSV, 'w', encoding='utf-8', newline='') as f:
    w = csv.writer(f, lineterminator='\n')
    w.writerow(['domain', 'f1', 'f2', 'f3', 'f4', 'f5', 'f6', 'f7'])
    for d in domains:
        nd = normalize_domain(d)
        w.writerow([nd] + [repr(v) if isinstance(v, float) else v for v in extract_features(nd)])

print(f'{len(domains)} baris -> {OUT_CSV}')
print('akademik.polban.ac.id ->', extract_features(normalize_domain('akademik.polban.ac.id')))
