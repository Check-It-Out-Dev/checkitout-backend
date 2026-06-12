# Role: 05-secrets

## Description

Deploys sensitive secrets for Instagram Platform:
- GCP service account JSON files (from Ansible Vault)
- .env files for test and production environments
- Configures exact permissions (640) and ownership
- Validates secret file presence

## Critical Security

- All secrets must be encrypted in Ansible Vault before use
- File permissions MUST be exactly 640 (owner read/write, group read only)
- Ownership must match deployment users and secrets groups

## Requirements

- 02-users-groups completed (users and security groups must exist)
- 03-directories completed (secrets directories must exist)
- Vault password file configured

## Dependencies

- 02-users-groups
- 03-directories

## Example Playbook

```yaml
- hosts: instagram_platform
  become: true
  roles:
    - 05-secrets
```

## Usage

Before running, ensure vault files are encrypted:
```bash
ansible-vault encrypt inventory/group_vars/test/vault.yml
ansible-vault encrypt inventory/group_vars/prod/vault.yml
```

Run with vault password:
```bash
ansible-playbook -i inventory/production.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass
```

## Tags

- `secrets` - All tasks
- `gcp-secrets` - GCP service accounts only
- `env-files` - .env files only
