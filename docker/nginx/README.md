# nginx (local TLS)

The PRD requires TLS termination at nginx with HTTP→HTTPS redirect and HSTS. For development, use a self-signed certificate.

## Generate certs (one-time)

From the repository root:

```bash
mkdir -p docker/nginx/certs
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout docker/nginx/certs/dev.key \
  -out docker/nginx/certs/dev.crt \
  -subj "/CN=localhost"
```

## Trust (optional)

- **macOS:** open `dev.crt` and add to Keychain as trusted, or use `-k` with `curl`.
- **curl:** `curl -vk https://localhost/actuator/health`

Browser warnings for self-signed certs are expected in dev. Replace with a CA-issued certificate in production.
