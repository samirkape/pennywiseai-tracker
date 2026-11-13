# ChatGPT / Claude Connector — Spendly

Status: **implemented** (branch `feature/chatgpt-claude-connector`)

The connector lets a user analyse their own spending with ChatGPT (OpenAI) or Claude (Anthropic)
for any window — last 7/30 days, last 3/6/12 months, or a custom number of days, months or years.

---

## 1. What the user gets

1. **Settings -> AI Connector -> Enable AI Connector** — the runtime config toggle.
2. **Provider & API Key** — pick a provider, choose a model, paste your own API key.
3. **Analyse Spending** — pick the period (or type your own question) and get a written
   analysis with the patterns in your spending and concrete suggestions.

Nothing is sent anywhere while the toggle is off.

### Providers

| Provider | Protocol | Free tier | Get a key |
|---|---|---|---|
| **Gemini (Google AI Studio)** | OpenAI-compatible `chat/completions` | **Yes** — free tier, no credit card | https://aistudio.google.com/apikey |
| **Groq** | OpenAI-compatible `chat/completions` | **Yes** — free plan, rate limited | https://console.groq.com/keys |
| **OpenRouter** | OpenAI-compatible `chat/completions` | **Yes** — `:free` model variants (20 req/min, 50/day) | https://openrouter.ai/keys |
| ChatGPT (OpenAI) | OpenAI Responses API | No — paid account | https://platform.openai.com/api-keys |
| Claude (Anthropic) | Anthropic Messages API | No — paid account | https://console.anthropic.com/settings/keys |

OpenAI and Anthropic do not hand out free API keys, so the free-tier providers are listed
first and the settings screen links straight to each provider's key page. Every provider is
a bring-your-own-key integration — Spendly never bundles or proxies a key.

## 2. Two levels of gating

| Level | Switch | Effect |
|---|---|---|
| Ship time | `Constants.Features.AI_CONNECTOR_ENABLED` | When `false` the `ConnectorSettings` / `ConnectorAnalysis` routes are not registered in `SpendlyNavHost` and no UI may navigate to them. |
| Run time | `connector_enabled` (DataStore) | The master toggle in Settings. When off, the repository refuses to make any network call (`ConnectorError.Disabled`). |

This mirrors the existing `AI_CHAT_ENABLED` pattern.

## 3. Module layout

The connector lives inside the `app` module (all other AI code — LiteRT-LM chat — already does),
in a self-contained package tree:

```
app/src/main/java/com/spendly/tracker/connector/
├── domain/
│   ├── AiProvider.kt          # ChatGPT (OpenAI) / Claude (Anthropic) metadata
│   ├── AiRequest.kt           # neutral request + response contract
│   ├── AiStreamEvent.kt       # TextDelta / Usage / Done
│   ├── ConnectorError.kt      # normalised errors + ConnectorException
│   ├── AnalysisWindow.kt      # "last X days / months / years"
│   └── SpendAnalysis.kt       # aggregate models + prompt rendering
├── data/
│   ├── remote/
│   │   ├── ProviderClient.kt  # adapter interface
│   │   ├── OpenAiProviderClient.kt    # OpenAI Responses API
│   │   ├── AnthropicProviderClient.kt # Anthropic Messages API
│   │   ├── ChatCompletionsProviderClient.kt # Gemini / Groq / OpenRouter
│   │   ├── ProviderSpec.kt    # provider -> protocol + base url
│   │   └── SseReader.kt       # text/event-stream framing
│   ├── analysis/
│   │   ├── SpendAnalysisRepository.kt # DB access
│   │   └── SpendAnalysisBuilder.kt   # pure aggregation (unit tested)
│   ├── preferences/ConnectorPreferencesRepository.kt
│   ├── security/SecureKeyStore.kt     # Keystore-encrypted API key
│   └── repository/ConnectorRepository.kt # orchestration + fallback
└── di/ConnectorModule.kt
```

UI: `ui/screens/connector/ConnectorSettingsScreen.kt`, `ConnectorAnalysisScreen.kt`
and their ViewModels. Shared markdown renderer: `ui/components/MarkdownText.kt`.

## 4. Anti-corruption layer

The app never sees a vendor payload. Each adapter translates the neutral contract:

* **OpenAI** — `POST https://api.openai.com/v1/responses` with `model`, `instructions`, `input`,
  `max_output_tokens`, `stream`. Streams `response.output_text.delta` events; the non-streaming
  response is read from `output[] -> content[] -> output_text`.
* **Anthropic** — `POST https://api.anthropic.com/v1/messages` with `model`, `max_tokens`,
  `system`, `messages[]`, `stream`. Streams `content_block_delta` events.
* **Gemini / Groq / OpenRouter** — one shared adapter
  (`ChatCompletionsProviderClient`) for the OpenAI-compatible
  `POST {base}/chat/completions` shape: `model`, `messages[]` (system + user), `max_tokens`,
  `stream`. Streams `choices[0].delta.content`. `temperature` is deliberately not sent —
  several free models reject it and the system prompt already shapes the answer.

Both protocols fall back to a single non-streaming request if the provider refuses to
stream, so the user always gets an answer. Errors are normalised into `ConnectorError`
(missing/invalid key, rate limited, network, server, bad request) so the UI never branches
on provider.

## 5. Privacy model

The only thing that ever leaves the device is the **aggregate brief** rendered by
`SpendAnalysis.renderPromptPayload()`:

* period, currency, total spend/income, transaction counts, average per day, savings rate
* spend per category (share % and count), top merchants, monthly trend
* recurring merchants, largest single spendings, spend per weekday, spend per tag

Deliberately **never** included: descriptions, SMS bodies, account numbers, bank names,
balances, reference ids. The builder only reads amount, merchant, category, type, date and tags.

The API key is encrypted with an AES-GCM key held in the **Android Keystore**
(`SecureKeyStore`) and stored as ciphertext in private SharedPreferences. It is sent only to the
provider the user selected, and it is never part of the APK.

## 6. Analysis window

`AnalysisWindow(unit, count)` resolves to an inclusive date range and to the equally sized
previous period (used for the "vs previous period" percentage). Presets: 7 days, 30 days,
3 months, 6 months, 12 months; the UI also allows a custom count for days, months or years.
Counts are clamped (max 366 days / 36 months / 10 years).

## 7. Tests

`app/src/test/java/com/spendly/tracker/connector/` covers the window maths and the aggregation
(totals, category shares, recurring detection, weekday grouping, period-over-period change, tag
splitting, prompt payload contents).

## 8. Not implemented (deliberately)

* **Backend proxy / BFF** — the design below recommended routing production traffic through our
  own backend so provider keys never reach the device. This implementation is direct-to-provider
  with a **user-supplied** key (the same trade-off every "bring your own key" app makes). If we
  ever ship a hosted version, the proxy belongs behind `ProviderClient`.
* **Multi-provider fallback chain** (`primary -> fallback -> emergency local model`) — the
  registry (`ProviderRegistry`) is the seam where it would go.
* **Chat-style conversation over history** — the connector is a single-shot analysis per request.
