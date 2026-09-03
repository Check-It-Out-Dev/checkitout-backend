# Firebase Storage bucket CORS

The upload flow (avatar, campaign photos, content) is **direct-to-bucket**:
the backend mints a V4 signed `PUT` URL (`SignedUrlService`), and the browser
uploads the bytes straight to `storage.googleapis.com`. The backend never
proxies the file — it only records ownership in `file_uploads` and, on
confirm, verifies the blob exists.

Because that `PUT` is a **cross-origin** request from the app's origin to
`storage.googleapis.com`, the bucket must publish a CORS policy that allows
it. Without one the browser blocks the `PUT` with `TypeError: Failed to
fetch` **before it ever leaves the tab** — and the failure is invisible to
the automated tests, because Playwright's request API (and the BE Cucumber
suite) are not subject to browser CORS. A real browser is the only thing
that exercises this path, which is how it was found (Chrome journey,
2026-06-14).

## Apply

Requires `gcloud`/`gsutil` authenticated to the project with Storage Admin.

```bash
# bucket = the Firebase default bucket for check-it-out-47c50
gsutil cors set deployment/gcs/storage-cors.json \
  gs://check-it-out-47c50.firebasestorage.app

# verify
gsutil cors get gs://check-it-out-47c50.firebasestorage.app
```

`storage-cors.json` MIRRORS THE LIVE BUCKET POLICY (state 2026-06-16). The
bucket turned out to already carry a legacy-era policy (checkitout.app +
bs-local/BrowserStack origins, POST/DELETE methods, extended response
headers) — only the greenfield `:4201` origins were missing, which is why
legacy `:4200` browser uploads worked while greenfield ones died on the
CORS preflight. The 4201 variants were ADDED additively on 2026-06-16 (via
the storage JSON API with the BE service account); nothing was removed, so
whatever still relies on the legacy entries keeps working. Keep this file
in lockstep with the live policy — `gsutil cors set` REPLACES the whole
policy, so applying a trimmed file would silently drop origins.

The two plain-HTTP `:4300` origins (same additive apply, 2026-06-16) serve
the browser-QA mirror: automated Chrome extensions cannot pass the
self-signed interstitial on `https://localhost:4201`, so manual/agent QA
runs the greenfield app via `ng serve --port 4300 --ssl false`. Only http
variants exist — the mirror never terminates TLS.

This is an **owner/infra action** — it changes bucket configuration, not the
repo — so it is not run by any pipeline here. Re-run it whenever the set of
app origins changes (e.g. a new custom domain).
