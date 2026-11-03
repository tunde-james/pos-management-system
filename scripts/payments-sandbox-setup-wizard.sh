#!/usr/bin/env bash
#
# payments-sandbox-setup-wizard.sh
# Walks a human through sandbox onboarding for BOTH payment gateways.
# It never writes to .env and never echoes a secret — it validates shape
# and prints the .env block for you to paste yourself.
#
# Run from the repo root in git-bash:  bash scripts/payments-sandbox-setup-wizard.sh

set -euo pipefail

echo "=================================================================="
echo " PAYMENTS SANDBOX ONBOARDING"
echo " Two gateways, two dashboards, two open items to bring back."
echo "=================================================================="
echo

# ---------- 1. Paystack ----------
echo "STEP 1 — PAYSTACK (sandbox / test mode)"
echo "  1. Open https://dashboard.paystack.com and sign in."
echo "  2. Make sure you are in TEST MODE (the toggle is top-right)."
echo "  3. Settings -> API Keys -> copy the TEST secret key (starts sk_test_)."
echo "  4. While you are here, note the test card:"
echo "       4084 0840 8408 4081   (any future expiry, any CVV)"
read -r -p "  Paste the Paystack test secret key: " PAYSTACK_KEY
case "$PAYSTACK_KEY" in
  sk_test_*) echo "  -> shape OK." ;;
  *) echo "  -> that does not start with sk_test_ — double-check you copied the TEST key."; exit 1 ;;
esac
echo

# ---------- 2. Monnify (Moniepoint) ----------
echo "STEP 2 — MONNIFY / MONIEPOINT (sandbox)"
echo "  1. Open https://app.monnify.com and sign in to your sandbox account."
echo "  2. Settings -> API Keys & Webhooks."
echo "  3. Copy the API key (starts MK_TEST_) and the secret key."
read -r -p "  Paste the Monnify API key (MK_TEST_...): " MONNIFY_API
case "$MONNIFY_API" in
  MK_TEST_*) echo "  -> shape OK." ;;
  *) echo "  -> that does not start with MK_TEST_ — is this a sandbox key?"; exit 1 ;;
esac
read -r -s -p "  Paste the Monnify secret key: " MONNIFY_SECRET
echo
if [ "${#MONNIFY_SECRET}" -lt 20 ]; then
  echo "  -> suspiciously short — double-check the secret."
  exit 1
fi
echo "  -> shape OK."
echo

# ---------- 3. The two open items (the important part) ----------
echo "STEP 3 — CLOSE THE TWO OPEN ITEMS at the Monnify dashboard/API docs"
echo
echo "  OPEN ITEM A — transaction lookup:"
echo "  In the Monnify API docs (the sandbox dashboard links to them), find the"
echo "  endpoint that lists/verifies transactions arriving into a reserved"
echo "  account. Write down: the exact path, the HTTP method, and which query"
echo "  filters exist (account number? status? amount? date range?). We cannot"
echo "  write the real Monnify client until you bring this back."
read -r -p "  Write down what you found (paste freely): " MONNIFY_LOOKUP
if [ -z "$MONNIFY_LOOKUP" ]; then echo "  -> left empty — this item stays OPEN."; fi
echo
echo "  OPEN ITEM B — how the sandbox simulates an INCOMING customer transfer:"
echo "  In the sandbox dashboard, find the test tool that simulates a customer"
echo "  transferring INTO your reserved account. Write down the exact steps"
echo "  (menu names, fields). If no such tool exists, say so — that changes"
echo "  Monnify's v1 confirmation design and we will re-decide with evidence."
read -r -p "  Write down what you found (paste freely): " MONNIFY_SIM
if [ -z "$MONNIFY_SIM" ]; then echo "  -> left empty — this item stays OPEN."; fi
echo

# ---------- 4. The .env block ----------
echo "=================================================================="
echo " DONE. Paste this block into .env at the repo root (values from the"
echo " keys you just used — re-type them there, not from this screen):"
echo "=================================================================="
cat <<'EOF'

# ── PAYMENTS (sandbox) ───────────────────────────────────────────
# Paystack test-mode secret key (dashboard.paystack.com -> test mode)
PAYSTACK_SECRET_KEY=
# Monnify sandbox API key + secret (app.monnify.com -> Settings)
MONNIFY_API_KEY=
MONNIFY_SECRET_KEY=
EOF
echo
echo "Bring the two open-item answers back to the agent BEFORE the real"
echo "Monnify client is written. Paystack can be coded against the"
echo "reference-repo endpoints in the meantime."