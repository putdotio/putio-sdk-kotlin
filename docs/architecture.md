# Architecture

[`PutioClient`](../src/main/kotlin/io/putdotio/sdk/PutioClient.kt) composes one
namespace per product domain over a shared OkHttp transport. Each namespace's
`*Api.kt` lists its operations.

## Design Rules

- Keep the public surface close to `putio-sdk-typescript`.
- Use coroutine-first suspend APIs for network operations.
- Parse response JSON at the boundary with `kotlinx.serialization`.
- Preserve unknown backend string values in public value types instead of failing whole payloads.
- Keep exceptions typed, wrap API failures in `domain.operation` context, and derive user-facing guidance separately through `PutioErrorLocalizer`.
- Keep namespaces small until app needs prove expansion.
- Prefer transport helpers and typed models over generic JSON bags.
- Keep Android UI and app lifecycle types out of the SDK.
- `files.copy` wraps `/files/copy-to-disk`, which put.io keeps for an older client; new callers use `sharing.cloneSharedFiles`.

## Device-Code Contract

`DeviceCodeAuth.link` is a cold `Flow<DeviceCodeAuthState>` that runs one linking
attempt: `Requesting` → `AwaitingLink(code, qrCodeUrl, budget)` → `Validating` →
`Linked(accessToken, account)`. The consumer shows the code and `put.io/link`, and
stores the token from `Linked`; it never polls. `DeviceCodeAuthOptions` sets the poll
interval and budget.

A 404 from the match endpoint or a rejected validation ends the attempt in
`Expired(CODE_REJECTED)`; an elapsed budget ends it in `Expired(BUDGET_ELAPSED)`.
Either way, "Get new code" is a fresh collection. Transport failures while polling
are retried inside the budget; any other SDK error ends the attempt in
`Failed(error)` for `PutioErrorLocalizer`.
Cancelling the collector stops polling. `Linked.toString()` omits the token.

## Playback Contract

`FilesApi.resolvePlayback` composes strict file details, HLS/MP4/original selection,
conversion state, resume position, and sidecar subtitles into one typed result.
The consumer must supply its app-scoped HLS/MP4 preference and account-wide resume
setting. It may also declare `originalVideoPlayable` after platform proof; the SDK
does not read or own `/config` playback keys.

Direct media URLs use the account `download_token`, decoded as an
`AccountDownloadToken` and supplied as a `PlaybackMediaCredential`; the public
`build*Url` builders take the `AccountDownloadToken` directly. The API accepts it on
media endpoints (download, stream, MP4, HLS, subtitles, XSPF) and rejects it on API
calls such as account info or file listing, so a leaked media URL cannot drive the account.
The account token, playback credential, and resolved URL redact their debug representations.
Consumers may reveal the URL only at the player boundary and must not log, persist, cache, share, or attach it to analytics, notifications, or errors.

The account-wide resume setting is `use_start_from` (`AccountSettings.useStartFrom`);
the app passes it as `PlaybackRequest.useStartFrom`, while the per-file offset remains
`start_from` (`PlaybackSource.startFromSeconds`). Optional sidecar subtitle failures do
not block an otherwise ready source: consumers receive `PlaybackSubtitles.Unavailable`
with a typed API, transport, or invalid-response reason and may show a non-blocking
warning. Authentication failures still surface normally. Next-file lookup stays on
`FilesApi.findNextFile` so an autoplay lookup failure cannot block the current playback
source.

HLS masters follow the account's `hide_subtitles` setting: when it is on, the API leaves
every subtitle rendition out unless the URL sets `max_subtitle_count`.
`PlaybackRequest.maxSubtitleCount` and `buildHlsStreamUrl(maxSubtitleCount = ...)` send it;
`HLS_ALL_SUBTITLES` (`-1`) asks for every rendition, so a player that starts subtitles off
can still offer them.

When a video still needs conversion, resolution reads the canonical
`GET /files/{id}/mp4` status endpoint. The backend may use that read to recover an
existing stalled conversion; the resolver never starts conversion with `POST`.

Consumers handle `PlaybackConversionState` as follows:

- `Queued` and `Converting` render the interstitial and poll `resolvePlayback` with
  bounded delay and lifecycle cancellation.
- `Completed` triggers one immediate resolution refresh so strict file details can
  produce `Ready`; if it remains completed, stop and offer retry or Back.
- `Failed` stops polling and offers an explicit retry action that calls
  `startMp4Conversion`.
- `NotAvailable` is not terminal: the file needs conversion but none has been
  requested. When the viewer opened the file to play it, call `startMp4Conversion`
  once, as put.io's web, iOS and TV clients do; it moves the file to `Queued` or
  `Converting`. Back and download stay available.
- `Unknown` preserves the backend value, stops automatic polling, and offers retry
  or Back.

Never call `startMp4Conversion` from polling.

## Error Contract

- Domain namespaces wrap SDK failures with `domain.operation` context.
- `PutioApiException.errorMessage` is put.io's own explanation (`error_message`, else `message`), redacted. The exception message prefers it over the HTTP status text.
- A response that carries a non-OK `status` on HTTP 2xx is a serialization failure.
- `PutioErrorLocalizer` layers operation-specific recovery guidance on the typed API or transport error.
- Transport exceptions expose a stable failure kind and keep sanitized timeout, DNS, connection, TLS, protocol, and I/O cause types.
- Trash continuations may omit totals; keep the aggregates from the first page.

## Credential-Bearing URLs

- API requests send the token in the `Authorization` header where the endpoint supports it.
- Media URL builders put the download token in `oauth_token`. Treat the returned URLs as credentials.
- SDK exceptions replace sensitive query values with `REDACTED` and keep the method, path, query names, and other values. [PutioException.kt](../src/main/kotlin/io/putdotio/sdk/errors/PutioException.kt) owns the parameter list.
