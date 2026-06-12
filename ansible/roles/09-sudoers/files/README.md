# Sudoers Files

## Instructions

Place sudoers files in this directory before running the playbook:

1. **instagram-test-deploy** (301 lines)
2. **instagram-prod-deploy** (162 lines)
3. **instagram-scripts-admin** (389 lines)

## How to Obtain Files

### From Existing VPS

```bash
# From your local machine
scp ubuntu@OLD_VPS_IP:/etc/sudoers.d/instagram-test-deploy files/
scp ubuntu@OLD_VPS_IP:/etc/sudoers.d/instagram-prod-deploy files/
scp ubuntu@OLD_VPS_IP:/etc/sudoers.d/instagram-scripts-admin files/
```

### Verify Files Locally

```bash
# Validate syntax before deployment
sudo visudo -c -f files/instagram-test-deploy
sudo visudo -c -f files/instagram-prod-deploy
sudo visudo -c -f files/instagram-scripts-admin
```

## Security

- These files contain fine-grained sudo permissions
- Each file explicitly lists allowed commands (no wildcards)
- Files are validated automatically during deployment
- Invalid syntax will abort deployment

## Total Lines

- instagram-test-deploy: 301 lines, 123 commands
- instagram-prod-deploy: 162 lines, 68 commands
- instagram-scripts-admin: 389 lines, 116+ commands
- **Total: 849 lines** of security configuration
