#!/usr/bin/env python3
"""
Generates the demo dataset for the Tokopulse chargeback intelligence service.

Outputs:
  data/transactions.csv                       (600 rows over 90 days)
  data/chargebacks_processor_alpha.csv        (Visa-style codes, ISO dates)
  data/chargebacks_processor_beta.csv         (Mastercard codes + amount in cents, MM/dd/yyyy)
  data/chargebacks_processor_gamma.csv        (PG-* internal codes, epoch dates, SCREAMING headers)

Engineered patterns:
  * Overall rate ≈ 2.5% (above the 1.5% Visa/MC limit)
  * Processor hotspot: ProcessorBeta at ~5-6% (>2x portfolio average)
  * BIN cluster: BIN 453210 has 9 chargebacks
  * Geo concentration: NG accounts for >40% of chargebacks but <10% of transactions
  * Reason-code spike: FRAUD category > 60% of disputes
"""
import csv
import datetime as dt
import os
import random
from collections import Counter
from datetime import date, timedelta

random.seed(7)  # deterministic

OUT_DIR = "data"
os.makedirs(OUT_DIR, exist_ok=True)

TODAY = date(2026, 5, 1)
WINDOW_DAYS = 90
START = TODAY - timedelta(days=WINDOW_DAYS)

MERCHANTS = [
    ("MERCH-001", "Tokopulse Wallet Topup"),
    ("MERCH-002", "Tokopulse Marketplace"),
    ("MERCH-003", "Tokopulse Premium Subscription"),
    ("MERCH-004", "Tokopulse Gaming"),
]

# Country distribution: ID dominant (Tokopulse is Indonesian), low NG volume.
COUNTRIES_WEIGHTED = (
    ["ID"] * 60 +
    ["SG"] * 12 +
    ["US"] * 10 +
    ["GB"] * 7  +
    ["BR"] * 4  +
    ["NG"] * 7     # ~7% of transactions are from NG (still < 20%)
)

# 12 distinct BINs (challenge: 10–15)
BINS = [
    "411111", "453210", "424242", "545454",
    "510510", "549920", "601100", "650400",
    "411511", "401234", "552233", "470100",
]

MCC_DIST = (
    ["5816"] * 30 +    # digital goods
    ["5968"] * 10 +    # subscription
    ["5411"] * 12 +    # grocery
    ["5912"] * 8  +    # pharmacy
    ["5732"] * 6  +    # electronics
    ["5999"] * 14      # misc retail
)

# Processor share: Alpha 55%, Beta 20%, Gamma 25%
def pick_processor():
    r = random.random()
    if r < 0.55: return "ProcessorAlpha"
    if r < 0.75: return "ProcessorBeta"
    return "ProcessorGamma"

# ---------------------------------------------------------------------------
# Step 1: Transactions
# ---------------------------------------------------------------------------
N_TXNS = 600
transactions = []
for i in range(N_TXNS):
    tx_id = f"TXN-{i+1:05d}"
    merchant = random.choice(MERCHANTS)
    bin_ = random.choice(BINS)
    country = random.choice(COUNTRIES_WEIGHTED)
    mcc = random.choice(MCC_DIST)
    if country == "ID":
        currency = "IDR"
        amount = round(random.uniform(15000, 750000), 2)
    elif country == "GB":
        currency = "GBP"
        amount = round(random.uniform(5, 500), 2)
    elif country == "BR":
        currency = "BRL"
        amount = round(random.uniform(20, 800), 2)
    else:
        currency = "USD"
        amount = round(random.uniform(5, 500), 2)
    txn_date = START + timedelta(days=random.randint(0, WINDOW_DAYS))
    processor = pick_processor()
    status = "approved" if random.random() < 0.93 else "declined"
    transactions.append({
        "transaction_id":   tx_id,
        "merchant_id":      merchant[0],
        "merchant_name":    merchant[1],
        "mcc":              mcc,
        "amount":           f"{amount:.2f}",
        "currency":         currency,
        "transaction_date": txn_date.isoformat(),
        "card_bin":         bin_,
        "card_last_four":   f"{random.randint(0, 9999):04d}",
        "card_type":        random.choice(["credit", "debit", "prepaid"]),
        "issuer_country":   country,
        "processor_name":   processor,
        "status":           status,
    })

with open(os.path.join(OUT_DIR, "transactions.csv"), "w", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=list(transactions[0].keys()))
    writer.writeheader()
    writer.writerows(transactions)
print(f"Wrote {len(transactions)} transactions")

# ---------------------------------------------------------------------------
# Step 2: Chargebacks — engineered to trigger every alert rule
# ---------------------------------------------------------------------------
chargebacks = []
used_ids = set()
approved = [t for t in transactions if t["status"] == "approved"]

# --- 2.a BIN cluster: 9 chargebacks all from BIN 453210 ---
cluster_bin = "453210"
cluster_pool = [t for t in approved if t["card_bin"] == cluster_bin]
random.shuffle(cluster_pool)
fraud_codes_per_proc = {
    "ProcessorAlpha": ["10.1", "10.4", "10.5"],          # Visa fraud
    "ProcessorBeta":  ["4837", "4870", "PB-FRAUD-01"],   # Mastercard / Beta internal
    "ProcessorGamma": ["PG-UNAUTH-01", "PG-UNAUTH-02"],  # Gamma internal
}
for txn in cluster_pool[:9]:
    chargebacks.append({
        "txn": txn,
        "reason_code": random.choice(fraud_codes_per_proc[txn["processor_name"]]),
    })
    used_ids.add(txn["transaction_id"])

# --- 2.b NG geo-concentration: 10 chargebacks from NG (small txn pool) ---
ng_pool = [t for t in approved if t["issuer_country"] == "NG" and t["transaction_id"] not in used_ids]
random.shuffle(ng_pool)
for txn in ng_pool[:14]:
    code = (random.choice(["10.4", "10.5"])           if txn["processor_name"] == "ProcessorAlpha"
            else random.choice(["4837", "4870", "PB-FRAUD-02"]) if txn["processor_name"] == "ProcessorBeta"
            else "PG-UNAUTH-01")
    chargebacks.append({"txn": txn, "reason_code": code})
    used_ids.add(txn["transaction_id"])

# --- 2.c Processor hotspot: pile fraud disputes onto ProcessorBeta ---
beta_approved = [t for t in approved if t["processor_name"] == "ProcessorBeta"
                 and t["transaction_id"] not in used_ids]
random.shuffle(beta_approved)
beta_target = max(0, int(len(beta_approved + [t for t in chargebacks
                              if t["txn"]["processor_name"] == "ProcessorBeta"]) * 0.06)
                  - sum(1 for c in chargebacks if c["txn"]["processor_name"] == "ProcessorBeta"))
beta_codes = ["4837", "4870", "PB-FRAUD-01", "PB-FRAUD-03"]
for txn in beta_approved:
    if sum(1 for c in chargebacks if c["txn"]["processor_name"] == "ProcessorBeta") >= 18:
        break
    chargebacks.append({"txn": txn, "reason_code": random.choice(beta_codes)})
    used_ids.add(txn["transaction_id"])

# --- 2.d A few Gamma chargebacks (so all 3 processors are exercised) ---
gamma_pool = [t for t in approved if t["processor_name"] == "ProcessorGamma"
              and t["transaction_id"] not in used_ids]
random.shuffle(gamma_pool)
for txn in gamma_pool[:3]:
    code = random.choice(["PG-UNAUTH-01", "PG-NOGOODS-04", "PG-RECURR-02"])
    chargebacks.append({"txn": txn, "reason_code": code})
    used_ids.add(txn["transaction_id"])

# --- 2.e A few non-fraud disputes for realism (kept under 40% of total) ---
non_fraud_codes = {
    "ProcessorAlpha": ["13.1", "13.2", "12.5"],
    "ProcessorBeta":  ["4853", "4831", "PB-DUPCHG-01"],
    "ProcessorGamma": ["PG-NOGOODS-04", "PG-RECURR-02"],
}
random.shuffle(approved)
non_fraud_added = 0
for txn in approved:
    if non_fraud_added >= 2:
        break
    if txn["transaction_id"] in used_ids:
        continue
    code = random.choice(non_fraud_codes[txn["processor_name"]])
    chargebacks.append({"txn": txn, "reason_code": code})
    used_ids.add(txn["transaction_id"])
    non_fraud_added += 1

print(f"Generated {len(chargebacks)} chargebacks "
      f"(rate = {len(chargebacks) / len(transactions) * 100:.2f}%)")

# ---------------------------------------------------------------------------
# Step 3: Distribute into per-processor CSVs in their native formats
# ---------------------------------------------------------------------------
alpha_rows, beta_rows, gamma_rows = [], [], []
for idx, cb in enumerate(chargebacks):
    txn = cb["txn"]
    code = cb["reason_code"]
    cb_date = date.fromisoformat(txn["transaction_date"]) + timedelta(days=random.randint(30, 80))
    if cb_date > TODAY:
        cb_date = TODAY - timedelta(days=random.randint(0, 5))
    cb_id = f"CB-{idx+1:05d}"
    amount = float(txn["amount"])
    proc = txn["processor_name"]

    if proc == "ProcessorAlpha":
        alpha_rows.append({
            "dispute_reference_number": cb_id,
            "original_txn_id":          txn["transaction_id"],
            "dispute_amount":           f"{amount:.2f}",
            "dispute_currency":         txn["currency"],
            "visa_reason_code":         code,
            "dispute_date":             cb_date.isoformat(),
            "card_bin":                 txn["card_bin"],
            "card_issuer_country":      txn["issuer_country"],
        })
    elif proc == "ProcessorBeta":
        beta_rows.append({
            "cbId":           cb_id,
            "txnReference":   txn["transaction_id"],
            "amountCents":    str(int(round(amount * 100))),
            "currencyCode":   txn["currency"],
            "reasonCode":     code,
            "chargebackDate": cb_date.strftime("%m/%d/%Y"),
            "binNumber":      txn["card_bin"],
            "issuingCountry": txn["issuer_country"],
        })
    else:
        epoch = int(dt.datetime.combine(cb_date, dt.time(12, 0, 0)).timestamp())
        gamma_rows.append({
            "DISPUTE_ID":     cb_id,
            "TXN_ID":         txn["transaction_id"],
            "DISPUTE_AMOUNT": f"{amount:.2f}",
            "CURRENCY":       txn["currency"],
            "DISPUTE_CODE":   code,
            "DISPUTE_EPOCH":  str(epoch),
            "BIN":            txn["card_bin"],
            "COUNTRY":        txn["issuer_country"],
        })

def write_csv(path, rows, headers):
    with open(path, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=headers)
        w.writeheader()
        w.writerows(rows)
    print(f"Wrote {len(rows):>3} rows → {path}")

if alpha_rows: write_csv(os.path.join(OUT_DIR, "chargebacks_processor_alpha.csv"), alpha_rows, list(alpha_rows[0].keys()))
if beta_rows:  write_csv(os.path.join(OUT_DIR, "chargebacks_processor_beta.csv"),  beta_rows,  list(beta_rows[0].keys()))
if gamma_rows: write_csv(os.path.join(OUT_DIR, "chargebacks_processor_gamma.csv"), gamma_rows, list(gamma_rows[0].keys()))

# ---------------------------------------------------------------------------
# Sanity report
# ---------------------------------------------------------------------------
print("\n=== Sanity check ===")
print(f"Total transactions: {len(transactions)}")
print(f"Total chargebacks:  {len(chargebacks)}")
print(f"Overall CB rate:    {len(chargebacks) / len(transactions) * 100:.2f}%")

proc_txn = Counter(t["processor_name"] for t in transactions)
proc_cb  = Counter(c["txn"]["processor_name"] for c in chargebacks)
print("\nPer-processor:")
for p in sorted(proc_txn):
    rate = (proc_cb.get(p, 0) / proc_txn[p]) * 100
    print(f"  {p:18s} txns={proc_txn[p]:>4d}  cbs={proc_cb.get(p, 0):>3d}  rate={rate:.2f}%")

bin_counter = Counter(c["txn"]["card_bin"] for c in chargebacks)
print(f"\nBIN cluster on {cluster_bin}: {bin_counter[cluster_bin]} chargebacks")

country_cb = Counter(c["txn"]["issuer_country"] for c in chargebacks)
country_txn = Counter(t["issuer_country"] for t in transactions)
print("\nCountry distribution:")
for country in sorted(country_cb, key=lambda c: -country_cb[c]):
    cb_share = country_cb[country] / len(chargebacks) * 100
    tx_share = country_txn[country] / len(transactions) * 100
    print(f"  {country}  cbs={country_cb[country]:>2d} ({cb_share:5.1f}%)   txns={country_txn[country]:>3d} ({tx_share:5.1f}%)")

fraud_codes = {"10.1", "10.4", "10.5", "4837", "4870", "FR2",
               "PB-FRAUD-01", "PB-FRAUD-02", "PB-FRAUD-03",
               "PG-UNAUTH-01", "PG-UNAUTH-02"}
fraud_count = sum(1 for c in chargebacks if c["reason_code"] in fraud_codes)
print(f"\nFraud share: {fraud_count}/{len(chargebacks)} = {fraud_count / len(chargebacks) * 100:.1f}%")
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              