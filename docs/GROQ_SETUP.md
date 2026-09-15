# Groq API key setup

Groq is optional. Without a key, Repo Pilot uses deterministic lexical ranking. Groq is used only to rank already-indexed candidate symbols; deterministic PR mapping and graph construction do not depend on it.

## Get a key

Create a key in the Groq Console. Never paste the key into source code, `pom.xml`, a Notion page, screenshots, or Git. Do not commit `.env` files.

## Windows PowerShell

For the current terminal:
```powershell
$env:GROQ_API_KEY="gsk_your_key_here"
$env:GROQ_MODEL="llama-3.3-70b-versatile"   # optional
```
Persist for future terminals:
```powershell
setx GROQ_API_KEY "gsk_your_key_here"
setx GROQ_MODEL "llama-3.3-70b-versatile"
```
Open a new terminal after `setx`.

## macOS/Linux

Current terminal:
```bash
export GROQ_API_KEY='gsk_your_key_here'
export GROQ_MODEL='llama-3.3-70b-versatile'  # optional
```
Persist by adding the exports to `~/.zshrc` or `~/.bashrc`, then restart the terminal.

## Verify without exposing the secret

PowerShell: `if ($env:GROQ_API_KEY) { "configured" } else { "missing" }`

macOS/Linux: `test -n "$GROQ_API_KEY" && echo configured || echo missing`

Then run:
```bash
bin/repo-pilot diff "Refactor token validation" -r /path/to/project --threshold 0.80 --explain
```
The output says `Target resolution: groq` when Groq succeeded, or `deterministic` when no key is present or the service is unavailable. A low-confidence result exits with code 3 and lists alternatives.
