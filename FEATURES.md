# YAM Features

A detailed look at what YAM can do today. This reflects the actual current codebase (v0.1) —
nothing aspirational.

## Connections

- **Multiple simultaneous connections ("tabs")** — connect to several worlds/characters at
  once, each with its own independent scrollback, input history, and reconnect state. Switch
  between them with a tab row; close one without disturbing the others.
- **Reorderable tabs** — press and hold a tab, then drag it sideways to a new position; the
  tab lifts and follows your finger and drops where its middle ends up. Session-only: the order
  isn't remembered after the app closes.
- **Tab cues** — the tab you're in is steady cyan; a background tab that receives new server
  text blinks orange (all flashing tabs blink together) until you switch to it. A tab that
  logged in as a saved Character shows that Character's name under the world name.
- **Address Book** — name, host, and port saved per world, organized into user-created
  Groups (a flat sidebar of folders, plus the built-in All Worlds/Ungrouped views). Select a
  world, then act on it: New/Copy/Edit/Connect/Delete.
- **Saved Characters per World** — multiple characters (name + password) per world; picking
  one dials the world and logs that character in automatically. The login line itself is
  never shown in the scrollback or saved into input history — not even redacted — so a
  password never lingers on screen. The Address Book shows each world's most-recently-used
  character at a glance.
- **Characters list** — every saved character across every world in one flat list, for
  jumping straight to a character without going through its world first.
- **Soft-deleted worlds** — deleting a world moves it to Temp/Deleted instead of erasing it
  immediately; restore it or delete it permanently from there. Deleting a Group never deletes
  the worlds inside it — they just become Ungrouped.
- **Connect without saving** — a blank host/port form for a one-off connection that isn't
  saved as a World.
- **Auto-reconnect** — on an unexpected disconnect, retries after a fixed delay; toggle in
  Settings. Distinguishes a dropped connection from a deliberate Disconnect (which never
  auto-reconnects).
- **Manual reconnect control** — `/reconnect` redials immediately (cancelling any pending
  auto-reconnect wait); `/disconnect` and `/connect <world-or-host:port>` give direct control
  from the input line.
- **Foreground service** — the connection(s) stay alive in the background while you switch
  apps, with a persistent notification showing connection status (and, with multiple tabs
  open, a per-tab status list).

## Navigation

- **Settings ("⋮") is reachable from every screen** — including a live terminal — not just
  the Address Book. Look up a character's password or copy a different world's host:port to
  send someone, then get back.
- **Getting back to a live session never loses or duplicates it.** Whether you return via the
  system Back button, the Address Book's own "< Back" (shown whenever it was reached from
  somewhere, hidden on the app's true start screen), or the tab row's "Worlds" button, you land
  on the exact same tabs, scrollback, and connection state — opening Settings or browsing
  Worlds along the way never silently opens a second connection to the same world.

## Protocol handling

- Raw telnet over TCP, byte-transparent (ISO-8859-1) so nothing is lost decoding arbitrary
  server output.
- Telnet IAC option negotiation: responds WONT/DONT to every offered option — a valid,
  spec-compliant stance that keeps plain line-mode text working everywhere without
  pretending to support options (MXP, GMCP, MSSP, etc.) that aren't actually implemented yet.
- Subnegotiation blocks are parsed and surfaced (for future protocol work) but not yet
  interpreted.
- `/debug` toggles a live view of the raw negotiation traffic, for diagnosing a fussy server.

## Saving a log

- **Save Log** (next to Find) writes the active tab's whole scrollback to a plain-text file
  you choose — Android's own "Save as" picker opens with a name like
  `YAM-LocalRhost-20261017-113228.txt`, so no storage permission is involved.
- The file starts with a short header (world, host:port, Character, when it was saved, line
  count) followed by the scrollback exactly as it reads on screen — timestamps included if
  they were on, ANSI color codes not. It holds what was showing when you tapped Save.
- Disabled while the scrollback is empty.

## Find

- Tap **Find** under the connection bar to search the active tab's scrollback. The search is
  live (updates as you type), plain text rather than a pattern, and case-insensitive; the
  **Aa** chip makes it case-sensitive.
- Every match is highlighted and the current one stands out. Previous/Next step through them
  and wrap around at either end; the counter reads `3 / 13`. A new search starts at the most
  recent match, since the text you want in a MUD is usually the newest.
- Highlights are an overlay — the text and its colors are untouched. While a search is active
  the view stays put instead of jumping to the bottom as new text arrives. The system Back
  button (or the X) closes the bar and clears the highlights.

## Timestamps

- Per-tab, off by default, never remembered. Toggle with the **Timestamps** chip under the
  connection bar or with `/timestamps [on|off]`.
- While on, every new line (server text, your own echoed commands, and local notices) gets a
  dimmed `[HH:MM:SS]` prefix. Lines already on screen are never rewritten.
- Switching on or off prints one full date-and-time marker line (e.g.
  `--- Timestamps ON: Sat, Oct 17 2026 10:42:07 ---`), so a copied log still says what day it was.

## Rendering

- Full ANSI SGR support: standard 16-color, 256-color (`38;5;N`), and 24-bit truecolor
  (`38;2;R;G;B`) foreground/background.
- Bold, italic, underline, and reverse-video, including bold-brightens-the-8-base-colors
  terminal convention.
- Color/style state persists across lines the way a real terminal does — a server that sets
  a color once and expects it to carry forward keeps working correctly.
- Non-color CSI sequences (cursor movement, line-clear, etc.) are recognized and silently
  dropped rather than leaking raw escape bytes into the scrollback.

## Triggers

Pattern-match incoming lines and react — per world, or Global (applies to every world in
addition to that world's own triggers), evaluated World-then-Global.

- **Matching**: wildcard (`*`, `?`), regular expression, or plain substring, case-sensitive
  or not.
- **Actions** (any combination, on a single trigger):
  - Recolor the matched line's foreground.
  - Omit the line from the scrollback entirely.
  - Auto-send one or more lines back to the server, with `%0`–`%9` substituted from the
    match's captures — sent silently, never shown as if you'd typed it.
  - Fill the input box with text (captures substituted) for you to review and send yourself,
    rather than firing automatically.
  - Continue-matching flag: let later triggers in the list also evaluate against the same
    line, instead of stopping at the first match.

## Slash commands

Typed into the input bar as `/name args`. A leading `/` is only special when it names
something recognized — anything else is sent to the server exactly as typed, and `//foo`
always sends `/foo` literally.

**Built in:**

| Command | Effect |
|---|---|
| `/cls` | Clear the scrollback |
| `/print <text>` | Show a local-only note in the scrollback (never sent to the server) |
| `/time [format]` | Show the current date/time; optional custom format pattern |
| `/web <url>` | Open a URL in the browser |
| `/speedwalk <path>` | Expand shorthand like `3n2e` into `north, north, north, east, east` |
| `/reconnect` | Reconnect now |
| `/disconnect` | Disconnect |
| `/connect <world-name-or-host:port>` | Redial this tab to a different address |
| `/debug [on\|off]` | Toggle (or set) a live view of raw telnet negotiation traffic |
| `/timestamps [on\|off]` | Toggle (or set) a `[HH:MM:SS]` prefix on new scrollback lines for this tab |

**Custom, user-defined:** per world or Global, `/name` plus a pattern (wildcard/regex/
contains) matched against the typed arguments, expanding into one or more lines sent to the
server with `%0`–`%9` substitution. What you actually typed is what's echoed and saved to
history — recalling it later re-expands rather than replaying a stale result.

## Input

- Up/down arrow recall of previously sent commands, independent per tab.
- **Pasting several lines** asks first: a preview shows "Send N lines?" with Send / Cancel.
  On Send, each line goes to the world in order, exactly as if typed and sent (so `/commands`
  work and each line is echoed and kept in history). Cancel sends nothing and leaves the box as
  it was. Blank lines are skipped, and a single trailing line break doesn't count as a line.
  A paste that is really just one line (e.g. `look` plus a trailing newline) simply lands in
  the box as one line, no prompt.
- A manually typed `connect <name> <password>` (or `co`/`create`/`cr`) is automatically
  redacted to `connect name ********` in the scrollback and history, even though it wasn't
  sent through the character auto-login path.

## Settings

- Theme: System default, Light, or Dark.
- Auto-reconnect on/off.
- Manage Worlds & Characters, Characters, Triggers, and Slash Commands — each its own list/
  editor screen.
- Fonts: the terminal (scrollback) and the input line each get their own font family
  (Monospace, Sans Serif, Serif) and size (8-32 sp), with a live sample. Monospace is the
  terminal default so banners and tables stay aligned. Changes apply to every open tab at once
  and are remembered.
- A confirmation dialog before every destructive action (deleting a World, Character, Group,
  Trigger, or Slash Command).
- In-app Help — reads this project's own Help.md, browsable by section.

## Platform

- Android 10 (API 29) and up, Kotlin + Jetpack Compose, Room for local storage.
- Requires Internet and (for the persistent connection notification) POST_NOTIFICATIONS
  permission; no other permissions or third-party services involved. Everything runs and is
  stored entirely on-device.
