# Chinese Grammar AI — Windows AI Server

FastAPI server that powers the Android app's **Advanced Mode**: Chinese
sentence analysis with Vietnamese explanations.

It is designed to **degrade honestly**. HanLP, LTP and Ollama/Qwen are
optional: without them the server still runs, using a built-in rule-based
fallback parser, and every fallback result is labelled
`source="rule_fallback"` with elevated `uncertainty`. `GET /capabilities`
reports which engines are *actually* available right now — it never claims
an engine that is not installed or reachable.

## Setup on Windows (Python 3.11)

```bat
cd server
py -3.11 -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

Optional — the real NLP engines (large downloads, do this only on the PC
that will serve Advanced Mode):

```bat
pip install -r requirements-advanced.txt
```

Optional — the LLM for fluent Vietnamese explanations:

```bat
ollama pull qwen3:8b
```

## Run

```bat
run_windows.bat
```

or on Linux/macOS: `./run.sh`

Environment variables: `HOST` (default `127.0.0.1`), `PORT` (default
`8730`), `OLLAMA_HOST` (default `http://127.0.0.1:11434`), `QWEN_MODEL`
(default `qwen3:8b`), `REQUIRE_AUTH` (default `1`), `PAIRING_CODE` (set
your own instead of a generated one), `DB_PATH`.

## Pairing a phone

1. Start the server. The console prints **once**:
   `PAIRING CODE (shown once): XXXXXXXX`
2. In the Android app (PC Pairing screen), enter the server address and
   that code. The app calls `POST /v1/pair` and stores the returned device
   token in Android Keystore-backed storage.
3. All later `POST /v1/analyze` calls send `Authorization: Bearer <token>`.
4. To unpair: call `POST /v1/revoke-device` with the token (the app's
   "revoke" button), or delete `data/devices.db` on the PC while the
   server is stopped.

Only SHA-256 hashes of the pairing code and device tokens are stored
(SQLite, `data/devices.db`); plaintext tokens exist only on the phone.

## Network / security notes

- The server binds to **loopback by default**. For phone access on your
  home LAN, set `HOST=0.0.0.0` **only** on a trusted network, or — better —
  reach the PC over **Tailscale** and keep the bind/firewall tight.
- **Never expose Ollama (port 11434) or this server to the public
  Internet.** Ollama has no authentication of its own.
- Request bodies are capped at 32 KB, `/v1/analyze` text at 2000 chars,
  and endpoints are rate-limited. Logs contain only a short SHA-256 prefix
  and the length of analyzed text — never the sentence itself.
- User sentences sent to Ollama are wrapped as a delimited data block and
  the system prompt forbids following instructions inside them; model
  output must match a strict JSON schema or it is discarded.

## API summary

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /health` | no | liveness + schema version |
| `GET /capabilities` | no | honest engine availability report |
| `POST /v1/pair` | pairing code | exchange code for a device token |
| `POST /v1/revoke-device` | Bearer | revoke a device token |
| `POST /v1/analyze` | Bearer (if `REQUIRE_AUTH=1`) | sentence analysis |

Errors are always `{"error": {"code": "...", "message": "..."}}`.

## Tests

```bash
python -m venv .venv && source .venv/bin/activate   # or Windows equivalent
pip install -r requirements.txt pytest
python -m pytest tests/ -q
```

The test suite runs **without** HanLP, LTP or Ollama and verifies, among
other things: offset-preserving normalization, fallback segmentation and
的/地/得 corrections, HanLP/LTP alignment disagreement detection,
capabilities honesty, pairing → authorized analysis, 401 without a token,
413/422 for oversized input, and that a prompt-injection sentence is
treated purely as data.

## Known limitations (fallback mode)

- Segmentation uses a ~90-word built-in lexicon plus maximum matching;
  unknown words fall back to single characters and are flagged in
  `warnings`.
- Roles and dependency arcs are heuristics (position + POS), not a
  statistical parse.
- `translation_vi` is a word-by-word gloss composition, not a fluent
  translation, until Ollama/Qwen is available.
