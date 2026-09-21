# DayBricks sync protocol v2

Both routes require `Authorization: Bearer <token>`. Identity comes from
authentication; the client never sends a user ID. Production uses HTTPS.
No calendar events or device-local preferences belong in this schema.

## State

```json
{
  "schemaVersion": 2,
  "templates": [
    {
      "id": "9d30e090-2a77-4ee6-bdb6-26077db1e428",
      "title": "Training",
      "icon": "🏋️",
      "description": "Alternate strength and mobility days.",
      "defaultDurationMinutes": 15,
      "reminderMinutes": null,
      "sortOrder": 100,
      "presets": [
        {
          "id": "725f92eb-279a-4f91-9969-c087a76ea347",
          "title": "Mobility",
          "icon": "🤸",
          "durationMinutes": 16,
          "description": "Start gently and avoid painful ranges."
        }
      ]
    }
  ],
  "preferences": {
    "dominantHand": "RIGHT"
  }
}
```

IDs are UUID strings. Title: nonblank, at most 200 UTF-16 code units;
icon: null or at most 32 units. Description: at most 2,000 units. Durations: integers 1–720. Preset duration
may be null (use template default). Up to 2,000 templates and 100 presets
per template, unique IDs in their respective collections. `reminderMinutes` is
optional, is measured in minutes before the event, and may be `null` for no
reminder. `sortOrder` is
a signed 64-bit integer. Lists are arrays, not null. Body limit: 2 MiB.

`dominantHand` is retained only for compatibility with existing server state;
new clients always send `RIGHT` and place controls by writing direction.
Android IDs, zone, date, draft, viewport,
zoom, layout override, server URL and credentials are excluded.

## GET /api/v1/state

Returns `200`, the state above, `Content-Type: application/json`,
`Cache-Control: no-store`, and a strong `ETag: "42"`.
An uninitialized principal returns an empty template list, default
preferences and `ETag: "0"`.

## PUT /api/v1/state

Send the entire state, `Content-Type: application/json` and
`If-Match: "42"`. A successful atomic replacement returns `204`, no body,
and the new `ETag: "43"`. Delete a template by omitting it from the array.
Existing calendar events are unrelated to this request.

Errors: `401` invalid/missing token; `400` invalid schema/JSON/ETag;
`405` unsupported method; `413` body too large; `415` wrong content type;
`428` missing `If-Match`; `412` stale revision; `500` unavailable/corrupt
storage. Error bodies contain no user state or credentials. Unknown fields
are rejected by the reference server. The v2 server and client still read v1
states; absent descriptions and per-activity icons use empty/null defaults.
Writes are upgraded to v2. Clients must not overwrite newer unknown schemas.

## Client reconciliation

1. Snapshot pending local operations with stable local sequence IDs.
2. GET current remote state and ETag.
3. Replay `UpsertTemplate` or `DeleteTemplate` onto it. Old queued
   dominant-hand operations are ignored. Preserve all remote objects not touched locally.
4. If there are operations, PUT with `If-Match`.
5. On 412, repeat GET/replay/PUT, at most three total CAS attempts.
6. In one local transaction, acknowledge only sent sequence IDs, replay
   any newer local operations and install the merged state.

With no pending operations, only GET is necessary. A corrupt response or
network failure must leave local state/outbox intact. Replaying upsert,
delete and preference assignments is idempotent, including after a lost
PUT response. The last successfully applied operation on an object/field
wins, regardless of client clocks.
