# Deploy-script immutability chain

The pipeline treats **its own deployment scripts as untrusted artifacts**. Every file
that lands on the server is made kernel-immutable (`chattr +i`) within a second of
upload, validated read-only, unlocked only for the instant it executes, and deleted
afterwards. The design started from a named attack-surface analysis and closed each
window explicitly.

## The four attack windows it closes

| # | Window | Closed by |
|---|---|---|
| 1 | Scripts sitting on disk between upload and execution (~10 min) | `chattr +i` immediately after `scp` |
| 2 | Deployment files in the admin home before copy | Home-dir copies locked too |
| 3 | Race during the copy to the deployment dir | Atomic unlock → copy → relock of source, lock of destination |
| 4 | Shared dependency (`secure-logger.sh`) swapped underneath the scripts | Dependencies locked like first-class artifacts |

```
GitHub Runner ──upload + chattr +i──▶ Admin home (IMMUTABLE)
                                          │ unlock → cp → relock both
                                          ▼
                                   Deployment dir (IMMUTABLE)
                                          │ validate (read-only)
                                          ▼
                              unlock → execute → self-delete
```

## Mechanics

- **Upload** (`secure-script-upload.sh`, runner side): each `scp` is followed by
  `sudo chattr +i` on the remote path — scripts and their `.metadata` files both.
- **Validation & execution** (`validate-and-exec-ci-script.sh`, server side): refuses
  to run anything whose metadata does not validate; lifts immutability around the
  execution window and restores it for everything that stays.
- **Copy** (`deploy-and-secure.sh`): `chattr -i src → cp → chattr +i src → chattr +i dst`
  — no moment exists where either side is writable.
- **Configuration phase**: YAML fix-ups and permission changes run inside an explicit
  unlock/relock bracket.
- **Sudoers**: the deploy user gets `NOPASSWD: /usr/bin/chattr` and nothing broader —
  the lock primitive itself is the only privileged operation the chain needs.

## The paranoid sibling: rollback

`execute-systemd-rollback.sh` applies the same philosophy to disaster recovery: it
**refuses to run if its own file is older than 10 minutes**, killing replay of stale
rollback payloads. Automatic rollback triggers on validation failure in the deploy
workflow, restoring the pre-deployment backup captured before every release.

## Guarantees

1. No modification window — files are protected within ~1 s of landing.
2. No race conditions — lock/unlock/copy brackets are atomic per file.
3. No dependency attacks — shared libraries are locked like entry points.
4. Audit trail — metadata records every lock/unlock transition.
5. No residue — executed scripts self-delete; the attack surface ends with the run.

The live scripts: [`.github/scripts/remote/`](../../.github/scripts/remote/) ·
deployment workflow under [`.github/workflows/`](../../.github/workflows/).
