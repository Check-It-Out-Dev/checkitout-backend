# Role: 09-sudoers

## Description

Configures fine-grained sudo permissions for Instagram Platform deployment users (849 lines total):
- instagram-test-deploy (301 lines, 123 commands)
- instagram-prod-deploy (162 lines, 68 commands)
- instagram-scripts-admin (389 lines, 116+ commands)

## CRITICAL Security

- All files MUST be validated with `visudo -c` before deployment
- File permissions MUST be exactly 440 (r--r-----)
- Files MUST be owned by root:root
- Invalid sudoers syntax can lock you out of sudo!

## Requirements

- Sudoers files available in files/ directory
- Root access

## Dependencies

None (can run independently)

## Example Playbook

```yaml
- hosts: instagram_platform
  become: true
  roles:
    - 09-sudoers
```

## Safety

Role includes automatic validation with visudo before deployment. If validation fails, deployment is aborted.

## Tags

- `sudoers` - All tasks
- `test-sudoers` - Test user only
- `prod-sudoers` - Prod user only
- `admin-sudoers` - Admin user only
