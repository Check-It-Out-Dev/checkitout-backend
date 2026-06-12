# Before Deployment Checklist - Secret Replacement

**When:** BEFORE running `make deploy`
**Time:** 10 minutes
**Why:** Replace dummy secrets with real ones locally (never commit to Git)

---

## 🔑 **Secrets to Replace (Before Deployment)**

### **Step 1: Get Real Secrets**

**Get GCP Service Accounts (5 min):**

```bash
# Option A: Download from GCP Console
# 1. Go to: https://console.cloud.google.com/iam-admin/serviceaccounts
# 2. Select project: check-it-out-47c50
# 3. Find service account: instagram-test-gsm (or similar)
# 4. Click ⋮ → Manage Keys → Add Key → Create New Key → JSON
# 5. Save to ~/Downloads/check-it-out-47c50-xxxxx.json
# 6. Repeat for check-it-out-prod project

# Option B: Use gcloud CLI
gcloud iam service-accounts keys create ~/test-sa.json \
  --iam-account=YOUR-TEST-SA@check-it-out-47c50.iam.gserviceaccount.com

gcloud iam service-accounts keys create ~/prod-sa.json \
  --iam-account=YOUR-PROD-SA@check-it-out-prod.iam.gserviceaccount.com
```

**Get SSH Public Keys (2 min):**

```bash
# Your personal SSH public key (for ubuntu user)
cat ~/.ssh/id_rsa.pub
# Or: cat ~/.ssh/id_ed25519.pub

# GitHub Actions deployment keys (from GitHub secrets or generate)
# These are the public keys GitHub Actions uses to deploy
```

---

### **Step 2: Edit vault.yml Files Locally (3 min)**

```bash
cd ansible/inventory/group_vars/test/

# Edit test vault file
nano vault.yml  # or code vault.yml, or vim vault.yml
```

**Replace these 3 values:**

```yaml
# 1. Replace vault_gcp_service_account_test
vault_gcp_service_account_test: |
  {paste entire content from ~/test-sa.json}

# 2. Replace vault_test_deploy_ssh_public_key
vault_test_deploy_ssh_public_key: "ssh-rsa AAAAB3...REAL_KEY github-actions-test"

# 3. Replace vault_ubuntu_ssh_public_key
vault_ubuntu_ssh_public_key: "ssh-rsa AAAAB3...YOUR_REAL_KEY your-name@laptop"
```

**Save and exit (DO NOT commit to Git)**

**Repeat for production:**

```bash
cd ../prod/
nano vault.yml

# Replace:
vault_gcp_service_account_prod: |
  {paste entire content from ~/prod-sa.json}

vault_prod_deploy_ssh_public_key: "ssh-rsa AAAAB3...REAL_PROD_KEY github-actions-prod"

vault_ubuntu_ssh_public_key: "ssh-rsa AAAAB3...YOUR_REAL_KEY your-name@laptop"
```

**Save and exit (DO NOT commit to Git)**

---

### **Step 3: Verify (NOT tracked by Git)**

```bash
cd ansible/

# Check Git status - vault.yml should show as MODIFIED but don't commit
git status

# You should see:
#   modified:   inventory/group_vars/test/vault.yml
#   modified:   inventory/group_vars/prod/vault.yml

# Verify they're not staged
git diff inventory/group_vars/test/vault.yml
# Should show your real secrets in diff (DO NOT COMMIT!)

# Test that files are valid YAML
ansible-playbook playbooks/site.yml --syntax-check
# Should pass without errors
```

**⚠️ IMPORTANT:** Do NOT run `git add` or `git commit` on vault.yml files!

---

## ✅ **Ready to Deploy Checklist**

- [ ] Downloaded test GCP service account JSON from GCP console
- [ ] Downloaded prod GCP service account JSON from GCP console
- [ ] Got your personal SSH public key (`cat ~/.ssh/id_rsa.pub`)
- [ ] Got GitHub Actions SSH public keys (or generated new)
- [ ] Edited `inventory/group_vars/test/vault.yml` with real values
- [ ] Edited `inventory/group_vars/prod/vault.yml` with real values
- [ ] Verified files parse as valid YAML
- [ ] Did NOT commit vault.yml changes to Git
- [ ] Syntax check passed (`make syntax`)

**When all checked:** ✅ **Run `make deploy`**

---

## 🚀 **Deployment Commands**

```bash
cd ansible/

# Full deployment
make deploy  # 50 minutes
make start   # 10 minutes
make verify  # 5 minutes

# Or all at once
make full    # 65 minutes total
```

**Your secrets:**
- ✅ Deploy correctly (real GCP SAs and SSH keys)
- ✅ Never in Git (stay local only)
- ✅ Apps work immediately (no manual VPS fixes needed)

---

## 🔒 **Keeping Secrets Safe**

### **DO:**
- ✅ Edit vault.yml locally with real secrets
- ✅ Run Ansible with real secrets
- ✅ Keep real values in your local working directory
- ✅ Commit infrastructure changes (roles, playbooks, configs)

### **DON'T:**
- ❌ Run `git add inventory/group_vars/*/vault.yml`
- ❌ Run `git commit` with vault.yml changes
- ❌ Push vault.yml with real secrets to remote

### **If You Accidentally Stage vault.yml:**

```bash
# Unstage the file
git restore --staged inventory/group_vars/test/vault.yml
git restore --staged inventory/group_vars/prod/vault.yml

# Verify not staged
git status
# vault.yml should show as "modified" not "staged"
```

---

## 📝 **Git Best Practice**

**Create a .git/info/exclude file (local ignore):**

```bash
cd ansible/
cat >> .git/info/exclude << 'EOF'
# Local-only ignore (never share with team)
inventory/group_vars/test/vault.yml
inventory/group_vars/prod/vault.yml
EOF
```

**This prevents you from accidentally committing real secrets.**

---

## ✅ **Quick Start**

```bash
# 1. Replace secrets locally (10 min)
cd ansible/inventory/group_vars/test/
nano vault.yml  # Paste real GCP SA JSON and SSH keys

cd ../prod/
nano vault.yml  # Paste real prod values

# 2. Deploy (50 min)
cd ../../..
make deploy

# 3. Start (10 min)
make start

# 4. Verify (5 min)
make verify

# Done! No manual VPS secret replacement needed!
```

**Total: 10 min manual + 65 min automated = 75 minutes to production**

---

**Ready when you are!** Replace the dummy values with real ones, then `make full`.
