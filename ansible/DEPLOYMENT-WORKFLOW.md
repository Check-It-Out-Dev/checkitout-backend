# Complete Deployment Workflow - With Manual Secret Replacement

**Deployment Model:** Dummy placeholders → Ansible deploy → Manual secret replacement → Start apps

**Total Time:** 50 min automated + 7 min manual = **~1 hour**

---

## 🚀 **Complete Workflow**

### **Phase 1: Ansible Deployment (50 minutes automated)**

```bash
cd ansible/

# Deploy complete infrastructure with dummy secrets
make deploy

# Or manually:
ansible-playbook -i inventory/production.yml playbooks/site.yml
```

**What happens:**
- ✅ Base system configured (packages, network, firewall, SSH)
- ✅ Users and groups created
- ✅ Directories created with correct permissions
- ✅ Docker configured (networks, volumes)
- ✅ **Dummy secrets deployed** (GCP SA, SSH keys - don't work yet)
- ✅ Scripts deployed
- ✅ Systemd services created
- ✅ Nginx configured
- ✅ Sudo permissions configured
- ✅ Docker Compose files deployed
- ✅ Automation configured
- ✅ Security hardening applied

**Result:** Complete infrastructure ready, except real secrets

---

### **Phase 2: Manual Secret Replacement (5-7 minutes)**

```bash
# SSH to VPS
ssh ubuntu@192.0.2.10

# === Replace Test GCP Service Account (2 min) ===
sudo su - instagram-test-deploy

cat > /etc/instagram-platform/test/secrets/service-account.json << 'EOF'
{paste actual test GCP service account JSON here}
EOF

chmod 640 /etc/instagram-platform/test/secrets/service-account.json
exit

# === Replace Prod GCP Service Account (2 min) ===
sudo su - instagram-prod-deploy

cat > /etc/instagram-platform/prod/secrets/service-account.json << 'EOF'
{paste actual prod GCP service account JSON here}
EOF

chmod 640 /etc/instagram-platform/prod/secrets/service-account.json
exit

# === Replace SSH Keys (1 min) ===
# Test deployment key
echo "ssh-rsa ACTUAL_TEST_KEY github-actions-test" | \
  sudo tee /home/instagram-test-deploy/.ssh/authorized_keys
sudo chmod 600 /home/instagram-test-deploy/.ssh/authorized_keys

# Prod deployment key
echo "ssh-rsa ACTUAL_PROD_KEY github-actions-prod" | \
  sudo tee /home/instagram-prod-deploy/.ssh/authorized_keys
sudo chmod 600 /home/instagram-prod-deploy/.ssh/authorized_keys

# === Verify (30 sec) ===
# Check files exist and have correct format
sudo jq .project_id /etc/instagram-platform/test/secrets/service-account.json
sudo jq .project_id /etc/instagram-platform/prod/secrets/service-account.json
# Should show correct project IDs

exit  # Exit VPS
```

---

### **Phase 3: Start Applications (10 minutes automated)**

```bash
# Back on local machine
cd ansible/

make start

# Or manually:
ansible-playbook -i inventory/production.yml playbooks/startup.yml
```

**What happens:**
1. Starts Loki monitoring stack
2. Starts test environment (5 containers)
   - google-secrets-init uses REAL GCP SA to fetch secrets from GCP
   - Writes DB password, JWT secret, etc. to /app/config/
   - postgres, redis, sentinel, app all start successfully
3. Starts production environment (4 containers)
   - google-secrets-init uses REAL GCP SA
   - Fetches prod secrets from GCP Secret Manager
   - redis, sentinel, app start successfully

**Result:** All apps running with real secrets from GCP!

---

### **Phase 4: Verify Deployment (5 minutes)**

```bash
cd ansible/

make verify

# Or manually:
ansible-playbook -i inventory/production.yml playbooks/verify.yml
```

**What's verified:**
- ✅ All services running
- ✅ All containers healthy
- ✅ Health endpoints return 200
- ✅ Network connectivity
- ✅ Docker configuration
- ✅ Security configuration

---

## 📊 **Timeline Breakdown**

| Phase | Time | Type | What Happens |
|-------|------|------|--------------|
| 1. Deploy | 40-50 min | Automated | Ansible configures everything |
| 2. Replace secrets | 5-7 min | **Manual** | Copy-paste 3 files on VPS |
| 3. Start apps | 5-10 min | Automated | Apps start with real secrets |
| 4. Verify | 2-5 min | Automated | Health checks |
| **TOTAL** | **52-72 min** | **93% automated** | **7% manual** |

**Manual work:** Just 5-7 minutes (3 file replacements)

---

## 🔐 **Why This is Secure**

### ✅ Secrets Never in Git

- vault.yml in Git = Dummy values only
- Real GCP service accounts = Never committed
- Runtime secrets (DB, JWT, API) = Never in Ansible at all

### ✅ Minimal Attack Surface

**If Git repository compromised:**
- Attacker gets: Dummy GCP SA (doesn't work)
- Attacker gets: Ansible infrastructure code
- Attacker CANNOT: Access real secrets (on VPS only)
- Attacker CANNOT: Access runtime secrets (in GCP only)

**If VPS compromised:**
- Attacker gets: GCP SA JSONs (can fetch secrets)
- Mitigation: Revoke service account in GCP console
- Mitigation: Rotate secrets in GCP Secret Manager
- No Git history exposure

### ✅ Easy Secret Rotation

**To rotate GCP service account:**
1. Create new key in GCP console
2. SSH to VPS
3. Replace service-account.json file (2 minutes)
4. Restart app: `systemctl restart instagram-platform-prod`
5. Revoke old key in GCP console

**No Ansible redeployment needed!**

---

## 🎯 **Alternative: Automate Secret Replacement**

If you want to reduce manual work:

### **Use Ansible Lookup from Local Files**

```yaml
# roles/05-secrets/tasks/test-secrets.yml
- name: Deploy test GCP service account from local file
  ansible.builtin.copy:
    content: "{{ lookup('file', lookup('env', 'HOME') + '/gcp-secrets/test-sa.json') }}"
    dest: "{{ test_secrets_path }}/service-account.json"
    mode: '0640'
  no_log: true
```

**Keep real secrets locally:**
```bash
# On your machine (NOT in Git)
~/gcp-secrets/
├── test-sa.json       # Real test GCP SA
├── prod-sa.json       # Real prod GCP SA
└── loki-sa.json       # Real loki GCP SA
```

**Ansible reads from local files, deploys to VPS**

**Benefits:**
- ✅ No manual VPS commands
- ✅ Ansible handles everything
- ✅ Secrets still not in Git
- ✅ Fully automated

**Want me to set this up instead?**

---

## 📝 **Quick Command Reference**

### Replace Test SA
```bash
ssh ubuntu@192.0.2.10
sudo su - instagram-test-deploy
cat > /etc/instagram-platform/test/secrets/service-account.json << 'EOF'
{actual JSON}
EOF
chmod 640 /etc/instagram-platform/test/secrets/service-account.json
```

### Replace Prod SA
```bash
sudo su - instagram-prod-deploy
cat > /etc/instagram-platform/prod/secrets/service-account.json << 'EOF'
{actual JSON}
EOF
chmod 640 /etc/instagram-platform/prod/secrets/service-account.json
```

### Replace SSH Keys
```bash
echo "ssh-rsa REAL_KEY comment" | sudo tee /home/instagram-test-deploy/.ssh/authorized_keys
sudo chmod 600 /home/instagram-test-deploy/.ssh/authorized_keys
```

---

## ✅ **When to Use This Approach**

**Good for:**
- ✅ Quick deployments (7 min manual acceptable)
- ✅ Maximum security (no secrets in Git ever)
- ✅ Flexibility (change secrets anytime without Ansible)
- ✅ Simple workflow (no vault encryption complexity)

**Not ideal for:**
- ❌ Frequent redeployments (manual work each time)
- ❌ Multiple environments (more manual copy-paste)
- ❌ CI/CD automation (requires manual step)

**For full automation, use the "lookup from local files" approach in Alternative section above.**

---

**Recommended:** Manual replacement for initial deployment, then automate if redeploying frequently.
