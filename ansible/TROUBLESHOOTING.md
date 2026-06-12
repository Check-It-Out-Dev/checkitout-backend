# Troubleshooting Guide - Instagram Platform Ansible

## Common Deployment Issues

### 1. SSH Connection Failures

#### Problem: Cannot connect to VPS

```
fatal: [vps-example01]: UNREACHABLE! => {"msg": "Failed to connect to the host via ssh"}
```

**Solutions:**

```bash
# Test SSH manually
ssh -i ~/.ssh/id_rsa ubuntu@192.0.2.10

# Check SSH key permissions
chmod 600 ~/.ssh/id_rsa

# Verify inventory configuration
ansible-inventory -i inventory/production.yml --list

# Test with verbose SSH
ssh -vvv -i ~/.ssh/id_rsa ubuntu@192.0.2.10

# Update inventory with correct SSH key path
# Edit inventory/production.yml:
# ansible_ssh_private_key_file: /full/path/to/key
```

---

### 2. Vault Decryption Errors

#### Problem: Cannot decrypt vault files

```
ERROR! Attempting to decrypt but no vault secrets found
```

**Solutions:**

```bash
# Verify vault file is encrypted
head -1 inventory/group_vars/test/vault.yml
# Should start with: $ANSIBLE_VAULT;1.1;AES256

# Test vault password
ansible-vault view inventory/group_vars/test/vault.yml --vault-password-file ~/.ansible/vault-pass

# Re-encrypt if corrupted
ansible-vault decrypt inventory/group_vars/test/vault.yml
ansible-vault encrypt inventory/group_vars/test/vault.yml

# Check vault password file permissions
chmod 600 ~/.ansible/vault-pass
```

---

### 3. Package Installation Failures

#### Problem: APT packages fail to install

```
fatal: [vps-example01]: FAILED! => {"msg": "Failed to update apt cache"}
```

**Solutions:**

```bash
# SSH to VPS and fix APT
ssh ubuntu@192.0.2.10

# Update package lists
sudo apt update

# Fix broken packages
sudo apt --fix-broken install

# Clear cache
sudo apt clean
sudo apt update

# Check internet connectivity
ping -c 3 8.8.8.8

# Check DNS
nslookup ubuntu.com
```

---

### 4. Docker Network Creation Fails

#### Problem: Network subnet conflicts

```
Error response from daemon: Pool overlaps with other one on this address space
```

**Solutions:**

```bash
# List existing networks
docker network ls

# Inspect conflicting network
docker network inspect <network-name>

# Remove conflicting network (if safe)
docker network rm <network-name>

# Or choose different subnet in group_vars/all.yml
# Edit: docker_networks.test.subnet
```

---

### 5. Permission Denied Errors

#### Problem: Cannot write to directory

```
fatal: [vps-example01]: FAILED! => {"msg": "Permission denied"}
```

**Solutions:**

```bash
# Check directory ownership
ssh ubuntu@192.0.2.10 'ls -la /opt/instagram-platform/test/'

# Fix ownership
ssh ubuntu@192.0.2.10 'sudo chown -R instagram-test-deploy:instagram-test-deploy /opt/instagram-platform/test/'

# Fix permissions
ssh ubuntu@192.0.2.10 'sudo chmod 755 /opt/instagram-platform/test/'

# Verify user exists
ssh ubuntu@192.0.2.10 'id instagram-test-deploy'
```

---

### 6. Firewall Locks Out SSH

#### Problem: Lost SSH access after firewall rules

**Solution:**

```bash
# Use OVH console/VNC access
# Login via web console

# Check firewall rules
sudo iptables -L INPUT -n -v

# Add SSH rule if missing
sudo iptables -I INPUT 1 -p tcp --dport 22 -j ACCEPT

# Save rules
sudo iptables-save > /etc/iptables/rules.v4

# Test SSH from external
ssh ubuntu@192.0.2.10
```

**Prevention:** Always test in Molecule or cheap VPS first

---

### 7. Nginx Configuration Invalid

#### Problem: Nginx won't start

```
nginx: [emerg] invalid parameter "something" in /etc/nginx/nginx.conf
```

**Solutions:**

```bash
# Test configuration
ssh ubuntu@192.0.2.10 'sudo nginx -t'

# View error log
ssh ubuntu@192.0.2.10 'sudo tail -f /var/log/nginx/error.log'

# Restore backup
ssh ubuntu@192.0.2.10 'sudo cp /etc/nginx/backups/nginx.conf.backup-<date> /etc/nginx/nginx.conf'

# Fix and reload
ssh ubuntu@192.0.2.10 'sudo nginx -t && sudo systemctl reload nginx'
```

---

### 8. Sudoers File Invalid

#### Problem: Sudoers validation fails

```
>>> /etc/sudoers.d/instagram-test-deploy: syntax error near line 42 <<<
```

**Solutions:**

```bash
# NEVER deploy invalid sudoers file!

# Validate locally first
sudo visudo -c -f ansible/roles/09-sudoers/files/instagram-test-deploy

# Fix syntax errors

# Validate again
sudo visudo -c -f ansible/roles/09-sudoers/files/instagram-test-deploy
# Must show: parsed OK

# Then re-run deployment
```

**Critical:** Invalid sudoers can lock you out of sudo!

---

### 9. Docker Compose Fails to Start

#### Problem: Containers won't start

```
Error response from daemon: driver failed programming external connectivity
```

**Solutions:**

```bash
# Check logs
ssh ubuntu@192.0.2.10 'sudo journalctl -u instagram-platform-test -n 100'

# Check Docker logs
ssh ubuntu@192.0.2.10 'sudo docker logs instagram-platform-test-app-1'

# Validate compose file
ssh ubuntu@192.0.2.10 'cd /opt/instagram-platform/test/deployment && docker compose config'

# Check network exists
ssh ubuntu@192.0.2.10 'docker network inspect instagram-platform-test_network'

# Restart Docker
ssh ubuntu@192.0.2.10 'sudo systemctl restart docker'
```

---

### 10. Secrets Not Found

#### Problem: Application can't access secrets

```
Error: GOOGLE_APPLICATION_CREDENTIALS file not found
```

**Solutions:**

```bash
# Check GCP service account exists
ssh ubuntu@192.0.2.10 'ls -la /etc/instagram-platform/test/secrets/service-account.json'

# Check permissions
ssh ubuntu@192.0.2.10 'stat /etc/instagram-platform/test/secrets/service-account.json'
# Should be: 640 instagram-test-deploy:docker-secrets-test

# Check .env file
ssh ubuntu@192.0.2.10 'ls -la /opt/instagram-platform/test/deployment/.env'

# Redeploy secrets
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags secrets --vault-password-file ~/.ansible/vault-pass
```

---

### 11. Health Checks Fail

#### Problem: Applications not healthy

```
FAILED - RETRYING: Wait for test application health endpoint (60 retries left)
```

**Solutions:**

```bash
# Check if containers are running
ssh ubuntu@192.0.2.10 'docker ps -a --filter name=instagram-platform-test'

# Check application logs
ssh ubuntu@192.0.2.10 'docker logs instagram-platform-test-app-1 --tail 100'

# Check database connectivity (test)
ssh ubuntu@192.0.2.10 'docker exec instagram-platform-test-app-1 ping -c 2 postgres'

# Test health endpoint manually
ssh ubuntu@192.0.2.10 'curl http://localhost:8082/actuator/health'

# Check secrets loaded
ssh ubuntu@192.0.2.10 'docker exec instagram-platform-test-app-1 ls -la /app/config/'
```

---

### 12. Database Connection Fails

#### Problem: Application can't connect to database

**Test Environment:**

```bash
# Check PostgreSQL container running
ssh ubuntu@192.0.2.10 'docker ps --filter name=postgres'

# Check database logs
ssh ubuntu@192.0.2.10 'docker logs instagram-platform-test-postgres-1 --tail 50'

# Test connection from app container
ssh ubuntu@192.0.2.10 'docker exec instagram-platform-test-app-1 psql -h postgres -U instagram_test_user -d instagram_platform_test -c "\l"'
```

**Production Environment:**

```bash
# Test external OVH connection
ssh ubuntu@192.0.2.10 'psql -h your-postgres-host.example.com -p 20184 -U checkitout_prod_user -d checkitout_app_prod_database'

# Check if VPS IP is whitelisted in OVH
# Go to OVH control panel → Database → Authorized IPs
# Add: 192.0.2.10
```

---

### 13. Molecule Tests Fail

#### Problem: Molecule can't create container

```
CRITICAL Failed to create pod: Permission denied
```

**Solutions:**

```bash
# Check Docker running locally
docker ps

# Pull test image
docker pull geerlingguy/docker-ubuntu2404-ansible:latest

# Check Docker permissions
sudo usermod -aG docker $USER
newgrp docker

# Run with debug
molecule --debug create

# Try different driver/image
# Edit molecule/default/molecule.yml
```

---

### 14. Idempotency Test Fails

#### Problem: Second run shows changes

```
PLAY RECAP: changed=15 ...
```

**Solutions:**

Identify which tasks are not idempotent:

```bash
# Run with diff to see what changes
ansible-playbook -i inventory/production.yml playbooks/site.yml --diff

# Fix non-idempotent tasks:
# - Add creates: /path to shell commands
# - Use changed_when: false for check commands
# - Use state: present instead of shell commands
```

---

### 15. Variables Not Found

#### Problem: Undefined variable error

```
fatal: [vps-example01]: FAILED! => {"msg": "The task includes an option with an undefined variable. The error was: 'vault_test_secret' is undefined"}
```

**Solutions:**

```bash
# Check variable defined in vault
ansible-vault view inventory/group_vars/test/vault.yml | grep vault_test_secret

# Check variable defined in group_vars
grep -r "vault_test_secret" inventory/group_vars/

# Add to appropriate file
ansible-vault edit inventory/group_vars/test/vault.yml

# Or make optional in template
# Use: {{ vault_test_secret | default('') }}
```

---

## Emergency Recovery

### Complete Deployment Failure

If deployment completely fails and VPS is unusable:

1. **Restore from snapshot** (OVH control panel)
2. **Or start with fresh VPS:**
   - Create new Ubuntu 24.04 VPS
   - Update IP in inventory/production.yml
   - Re-run deployment

### Partial Failure - Continue from Checkpoint

```bash
# If you know which role failed, start from there
ansible-playbook -i inventory/production.yml playbooks/site.yml --start-at-task "Task name"

# Or skip completed roles
ansible-playbook -i inventory/production.yml playbooks/site.yml --skip-tags phase3,phase4
```

---

## Getting Help

### Enable Debug Logging

```bash
# In ansible.cfg
[defaults]
log_path = ./ansible.log
verbosity = 3

# Or per-run
ansible-playbook -i inventory/production.yml playbooks/site.yml -vvv
```

### Collect Diagnostic Information

```bash
# Ansible version
ansible --version

# Python version
python3 --version

# Installed collections
ansible-galaxy collection list

# Check connectivity
ansible -i inventory/production.yml all -m ping

# Gather facts
ansible -i inventory/production.yml all -m setup
```

### Review Logs

```bash
# Local Ansible log
tail -f ansible.log

# VPS systemd journal
ssh ubuntu@192.0.2.10 'sudo journalctl -f'

# Specific service
ssh ubuntu@192.0.2.10 'sudo journalctl -u instagram-platform-test -f'

# Docker logs
ssh ubuntu@192.0.2.10 'docker logs instagram-platform-test-app-1 --tail 100 -f'
```

---

## Contact

For issues specific to this deployment, refer to:
- **Ansible Docs:** `ansible/README.md`
