# Potato Analysis

Analysis of [Potato](https://github.com/talvo/potato) (source read at `~/Potato/potato.vfs`), a Tcl/Tk desktop MUD/MUSH client, as a reference for this Android client. Potato is open source and freely licensed; this is a read-only study of its design, not a copy of its code.

All file:line references below are to files under `potato.vfs/lib/` unless noted.

---

## 1. Project Overview

**Language/framework:** Tcl/Tk. The whole client is one Tcl package (`app-potato`), distributed either as a "starpack" (self-contained executable) or run from source via `main.tcl`.

**Architecture:** Monolithic, callback/global-array style — not MVC, not componentized. Everything lives in a single namespace, `::potato`, with **316 top-level procs** in one 12,759-line file (`potato.tcl`). There's no model/view separation: UI-building procs read and write the exact same global arrays that connection/protocol logic reads and writes. State lives in a few giant associative arrays keyed by synthetic composite keys:

- `world($worldId,$optionName)` — all per-world config (e.g. `world(3,host)`, `world(3,events,7,pattern)`)
- `conn($connId,$field)` — all per-connection runtime state (e.g. `conn(2,telnet,state)`, `conn(2,ansi,fg)`)
- `misc(*)` — global app preferences
- `potato(*)` — global app runtime state

The whole thing runs on Tk's single-threaded event loop (`fileevent`, `after`, `vwait`). This is idiomatic for a 20-year-old Tcl/Tk desktop app, but it is *not* a pattern to replicate on Android — it's the reason a straight port isn't attempted here; this doc exists so the Android app can imitate Potato's **behavior** while using Android-native architecture (Services, ViewModels, Room, StateFlow) instead.

**Entry points / top-level modules:**

`main.tcl` (9 lines) just adds `lib/` to the Tcl path and does `package require app-potato`, which resolves to `source`-ing `lib/potato.tcl`. That file's `::potato::main` does the real bootstrapping: app metadata, path setup (see §5), i18n, then module loading.

Notably, the `lib/potato-*.tcl` files are **not** separate packages — they're `source`d directly by a hardcoded filename list in `basic_reqs` (`potato.tcl:12639`: `set files [list telnet proxy wikihelp spell encoding config events slash]`). This is a manual code-splitting convention, not a real module boundary — everything ends up in the same namespace at runtime.

| File | ~Lines | Purpose |
|---|---|---|
| `potato.tcl` | 12,759 | Everything not listed below: connection lifecycle, ANSI parsing, main window/menu/binding construction, input handling, slash-command dispatcher, spawn windows, logging, mail window, address book, macro window, history. |
| `potato-telnet.tcl` | 520 | Telnet IAC state machine, option negotiation, subnegotiation parsing. |
| `potato-events.tcl` | 831 | The trigger-matching engine. |
| `potato-slash.tcl` | 1,069 | Declares the ~50 built-in `/commands`. |
| `potato-config.tcl` | 2,218 | Default preferences, global config load/save, and all world-settings dialog UI. |
| `potato-skin.tcl` | 1,767 | The default UI skin — actual main-window widget layout (see §3/§4). |
| `potato-encoding.tcl` | 268 | A static Unicode→Latin-1 fallback table (not general charset conversion — see §2). |
| `potato-proxy.tcl` + `potato-proxy-SOCKS4.tcl` | 9 + 99 | Proxy support — SOCKS4/4a only. |
| `potato-spell.tcl` | 347 | Spellcheck by shelling out to an external `aspell` process. |
| `potato-font.tcl` | 267 | Custom font picker, only used pre-Tk-8.6. |
| `potato-wikihelp.tcl` | 728 | Renders bundled `lib/help/*.md` as in-app help. |
| `potato-version.tcl` | 1 | `return "2.0.0b18"`. |

The closest thing to a real extension point is a `.tm` (Tcl Module) loader (`loadPotatoModules`, `potato.tcl:6866`) that `package require`s anything dropped in `~/.potato/lib` — unsandboxed arbitrary-code execution, not a plugin API in any modern sense.

---

## 2. Connection / Protocol Handling

This is the section with the most transferable behavior and the most ways to get it subtly wrong.

### Opening and running the connection

- Raw socket open is `socket -async $host $port` (`potato.tcl:2846`), wrapped in `ioOpen`/`ioClose`/`ioRead`/`ioWrite` indirection procs specifically so a `.tm` module *could* override transport behavior — nothing ships that does.
- `connect` (`potato.tcl:2889`) sets a `writable` fileevent, then calls `vwait` to block the *calling* proc until that fileevent fires — a classic Tcl trick for synchronous-looking code on top of async I/O, but it means `connect` can recursively re-enter the Tk event loop while "waiting." Not a pattern to imitate; a coroutine or suspend function is the natural Android analogue.
- **A world can have two host/port fallbacks** (`host`/`port` then `host2`/`port2`, each with its own SSL flag), tried in order.
- **TLS certificate validation is deliberately disabled** (`::tls::import ... -request 0`, `potato.tcl:3034`) — the comment explicitly says most MUSHes use self-signed certs. Worth knowing before assuming Android's stricter default TLS behavior is a bug to "fix" — it may need to be a deliberate, informed choice (e.g. trust-on-first-use) rather than a hard requirement, if talking to real-world MUSH servers.
- Once connected, the socket is set **fully binary** (`-translation binary -encoding binary`, `potato.tcl:3204`) so an `0xFF` (telnet IAC) byte is never confused with a multi-byte encoded character. Character decoding happens *after* telnet unescaping, per line, via `encoding convertfrom` (`potato.tcl:3669`) — decode-after-unescape, not before. This app already gets this right (`TelnetClient` reads bytes before charset interpretation).
- The read callback (`get_mushage`, `potato.tcl:3640`) reads in tiny 10-byte chunks per event, splits on the world's line ending, and feeds each complete line through ANSI/trigger/display processing.

### Telnet/IAC negotiation

A genuinely well-built hand-rolled byte-stream-safe state machine (`potato-telnet.tcl`), not a library. It correctly buffers partial/split telnet sequences across TCP packet boundaries (state kept in `conn($c,telnet,state/subState/buffer)`) and handles doubled-IAC escaping. Per-option negotiation is driven by per-world settings (CHARSET, NAWS, TTYPE each gated by a `world(...)` flag).

One MUD-specific idiom worth calling out: **GA/EOR-as-prompt handling.** When the server sends `IAC GA` or `IAC EOR`, Potato treats everything typed since the last newline as a "prompt" (`potato-telnet.tcl:219-235`) and there's an `ignoreNewline` option that strips a leading newline immediately after a displayed prompt. This is the kind of thing that looks like a bug in a naive client (an extra blank line around prompts) until you know the convention exists.

### Protocol support inventory

| Protocol | Status in Potato |
|---|---|
| Telnet core (IAC/DO/DONT/WILL/WONT/SB) | Fully implemented. |
| NAWS (window size) | Implemented, but height is **hardcoded to 24** and never updated on resize — only width (from the wrap column) is real. |
| TTYPE (terminal type) | Implemented. |
| CHARSET (RFC 2066) | Implemented, switches live via Tcl's `encoding` subsystem. |
| MSSP | Parsed but **read-only** — shown in a manual "View → MSSP Info" popup, never consumed to auto-configure anything. |
| MXP | **Stub only** — always answered `WONT`, no parser exists. |
| MSDP | **Not implemented at all.** |
| GMCP | **Not implemented at all.** |
| MCCP (compression) | **Not implemented at all.** |
| STARTTLS (in-band) | Present in the option table but dead code (hardcoded `0 && ...`) — TLS only happens via a pre-configured SSL port, never negotiated in-band. |

Potato is, in short, a "telnet + ANSI + NAWS/TTYPE/CHARSET" client with no modern out-of-band data channel (GMCP/MSDP) and no compression. If this app ever wants those, it's new design work with no Potato precedent to lean on — worth flagging since GMCP in particular is common on modern MU* codebases (and is already anticipated in this app's `TelnetEvent.Subnegotiation` hook, per `TelnetClient.kt`'s own doc comment).

### Reconnect / timeout / disconnect

- `disconnect` tears down the connection but does **not** itself schedule a reconnect.
- Reconnect scheduling is a separate proc, `boot_reconnect` (`potato.tcl:4369`), reading `world($w,autoreconnect)` / `,autoreconnect,time` (default **330 seconds**) and doing a flat `after` callback — **no exponential backoff, no jitter, no retry cap.** This app's 30s fixed delay is the same shape of design, just a shorter default.
- **A genuinely easy-to-miss asymmetry:** `boot_reconnect` is called from connect-failure paths and from the socket **read-error** path, but explicitly **not** from the clean-EOF path (`potato.tcl:3648`: on EOF it just disconnects, no reconnect call). In other words, real Potato does *not* auto-reconnect when the server closes the connection gracefully — only when the network itself errors out. This app's current behavior (reconnect on any non-user-initiated `DISCONNECTED`, EOF included) is a deliberate simplification, not an oversight — worth knowing it diverges from Potato on purpose, in case that ever needs revisiting.
- A separate 45-second keepalive loop sends either a telnet NOP or a raw NUL byte, per a per-world toggle, to stop idle disconnects.
- **Timers** are a distinct feature from reconnect and from Events: a world can define `timer,$id,{enabled,continuous,count,delay,every,cmds}` entries that periodically send text to the connection, independent of triggers — effectively a per-world cron. (This is exactly the "Timers" milestone already on this app's roadmap.)

### ANSI/SGR parsing

- Tokenizes per-character (not per-run), which is what lets a trigger recolor an arbitrary substring mid-line later.
- **Malformed ANSI truncates the line**: an `ESC[` with no closing `m` aborts display of the rest of that line entirely, not just the broken escape.
- **Color/bold/underline state persists across lines** by default (stored per-connection, not reset each line) unless a per-world "force normal at end of line" option is set — this app's `AnsiParser` already does this correctly and calls it out in its own doc comment.
- Handles xterm-256 (`38;5;N` / `48;5;N`) but **only the indexed form** — a truecolor `38;2;R;G;B` sequence fails a strict check and silently drops the rest of that escape's codes. **No 24-bit color support at all.** This app already supports truecolor correctly (`AnsiParser.consumeExtendedColor`), which is a genuine improvement over Potato, not parity.
- Bell character handling assumes PennMUSH's convention (`\a`), with a code comment acknowledging there's no way to reconfigure it for other MU* codebases.

---

## 3. UI / UX

Potato's actual window chrome (toolbar, world tabs, status bar, spawn bar) is **not** in the core file — it's implemented by a "skin" (`potato-skin.tcl`), a ~40-procedure contract (`init`, `show`, `status`, `worldBar`, `spawnBar`, `inputWindows`, etc.) that only one skin ("potato", the default) actually implements. Skins are a full alternate-UI mechanism, not a stylesheet/color-theme system — worth knowing if this app's own theming ambitions are ever compared to Potato's "skins," since they're not the same scope of thing.

**Per-connection widgets:** one output `text` widget, and **two** input `text` widgets created up front for every connection regardless of whether "two input windows" is enabled (the second is just hidden/shown). Both are real multi-line Tk text widgets — multi-line input is native, not a special mode.

**Spawn windows** — secondary output panes (e.g. to split off channel chatter from the main view) — are created dynamically, capped at a hardcoded 20 per connection, and routed to via Events or a `/addspawn` prefix list. This app's Trigger feature already covers the *conceptual* equivalent of "recolor/omit/route matched text," minus the separate-pane routing (spawn windows have no clean mobile analogue and were deliberately scoped out of this app's Triggers milestone for that reason).

**Output display / scrollback — a notable Potato bug worth deliberately avoiding:**

- Word-wrap is always on, and the wrap width doubles as both the visual wrap column *and* the value reported to the server via NAWS — the same setting drives two different things.
- **The main output window's scrollback limit is configured in the UI (`outputLimit`, default 500 lines) but is never actually enforced anywhere in the code** — grep shows it's read only by the settings dialog itself. The main conversation buffer grows completely unbounded for the life of the connection. **Spawn windows, by contrast, do enforce their own separate limit.** This is very likely an unintentional bug in Potato, not a design choice — and it's a much bigger problem on mobile (constrained memory) than on a 2010s desktop. This app should enforce a real cap on `SessionUiState.lines`, which Potato itself fails to do for its primary window.

**Input handling:**

- History is capped (default 250 entries), trimmed on add, and — a nice detail — Up/Down navigation swaps your in-progress unsent draft into a backup slot so scrolling back through history and then back down returns you to what you were actually typing, not a blank line. This app's `SessionScreen` already implements exactly this pattern (`draftBeforeHistory`).
- There's also a separate, fully searchable **History window** distinct from simple up/down recall — a browsable/filterable treeview over history, not just recall.
- **No tab-completion of any kind exists anywhere in Potato** — confirmed by full-text search. Worth knowing this isn't an oversight in this app's own scope; Potato never had it either.
- Keyboard shortcuts are a live-editable, persisted array, not a static config file.

---

## 4. Features

**Triggers (Events):** Potato's Events are more elaborate than this app's Triggers in a few specific ways worth knowing about even though they're out of scope:
  - Case-sensitivity, "continue matching," and "match all instances in the line" (vs. just the first) are all independent toggles.
  - A "Run When" condition (Always / Not Up / No Focus / Inactive) gates whether an event fires based on window state — not something with a mobile-app analogue.
  - Events can *replace* matched text in place (not just recolor or omit), and can spawn matched output to a secondary window.
  - **Global events (world id 0) always run in addition to a connection's own world's events** — global isn't a fallback used only when a world has none, it's strictly additive. This app's Trigger/SlashCommand engines already use exactly this "world-then-global" precedence.

**Slash commands:** Potato ships ~50 built-in `/commands` (a real scripting surface — `/eval` runs arbitrary Tcl) *plus* the per-world custom-command mechanism this app's Slash Commands feature is modeled on. One thing Potato's dispatcher does that this app's doesn't: **unique-prefix abbreviation** (typing enough of a command name to be unambiguous invokes it) and **nested `[/command ...]` sub-command evaluation** inside other commands' arguments — a small recursive mini-language, not flat string matching. Worth knowing as a possible future enhancement, not a gap to treat as a bug.

**Macros:** stored as simple named multi-line command sequences, invoked via `/runmacro`, with the same world-then-global fallback as everything else. Not yet built in this app.

**Timers:** a per-world scheduled-command feature, independent of Events — send commands on a repeating interval. Already identified as a future milestone for this app.

**Logging:** supports plain text and a genuinely clever **HTML export** that walks the Tk text widget's own tag dump to reconstruct styled `<span>` output with inline CSS matching the world's live-configured ANSI palette — an HTML log looks like a screenshot of the session. The log filename itself is run through `clock format`, so a filename like `%Y-%m-%d.log` auto-date-stamps. No rotation or size limiting exists — a log just grows until closed.

**Mail window:** a compose UI that formats an in-game `@mail`-style command from To/CC/BCC/Subject/Body fields via per-world templates for different MU* dialects (MUSH/MUX/MUSE/etc.) — pure text templating, not a real mail protocol.

**Spellcheck:** shells out to an external `aspell -a` process (a desktop-only dependency) — not portable as-is; would need a from-scratch Android implementation (e.g. the platform's native spell-check APIs) if ever wanted.

**Proxy support:** SOCKS4/4a only. No SOCKS5, no HTTP CONNECT.

**Extension system:** the `.tm` module loader mentioned in §1 — unsandboxed, full-trust Tcl execution. Not something to imitate literally; if this app ever wants a scripting/plugin surface, it needs its own sandboxing story, not a port of Potato's (which has none).

---

## 5. Data / Config

- **Worlds:** one file per world, `~/.potato/worlds/*.wld`, in a format that is literally executable Tcl source (`set newWorld(key) value` lines, `source`d directly to load). Filenames are a sanitized name plus a 3-digit index. **Saving is a full rewrite**: every `.wld` file is deleted and all worlds rewritten from memory on every save — no incremental writes.
- **Passwords are only trivially "obfusticated,"** not encrypted — a symmetric single-character substitution cipher using two hardcoded lookup tables, explicitly named (not even euphemistically) `obfusticate` in the source. It is honestly documented as reversible-by-anyone-who-reads-the-code, not real security. This app already does the right thing by comparison (Room-backed local storage, no pretense of encryption, protected by normal Android app sandboxing) — but if this app ever wants to go further (e.g. Keystore-backed encryption for saved passwords), that would be a real improvement over Potato's approach, not parity with it.
- **Global config:** `~/.potato/config`, same "Tcl-source-a-flat-file" approach, holding global preferences and keyboard shortcuts.
- **Events, Macros, Timers, and custom Slash Commands are all stored inline inside the owning world's `.wld` file** as `world($id,events,...)` / `,macro,...` / `,timer,...` / `,slashcmd,...` fields — never separate files. This app's equivalent (a `Trigger`/`SlashCommand` Room table with a nullable `worldId` foreign key, `null` = Global) is a cleaner, relational version of the same "belongs to a world, or global" shape Potato expresses via string-keyed arrays in flat files.
- **Versioning:** Potato went through two schemes — a legacy bitwise-flags integer embedded in a trailing line of the file, explicitly marked frozen in the source ("DO NOT ADD ANYTHING ELSE HERE, use `world($w,version)` instead"), superseded by a simple incrementing integer field with a dedicated migration proc per version bump. That's the same shape this app's Room `Migration` objects already follow (`MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`) — worth noting only because it's independent validation that "one incrementing version int + one migration function per step" is what a mature version of this exact problem converged on, rather than something fancier.

---

## 6. Carried-Forward Feature Candidates

A working list of Potato UI features Rick specifically wants kept in mind for this app — not "obsolete" in the sense §§1-5 use that word (that was reserved for things like the `aspell` dependency, SOCKS4-only proxy, or the unenforced-scrollback bug), just not yet built here. Mapped against what Potato actually does and what already exists in this app:

| Feature | What Potato does | Status in this app |
|---|---|---|
| **Address Book** | `manageWorlds` (`potato.tcl:4920`) — the world list/manager itself; worlds *are* the address-book entries, with drag-and-drop grouping and soft-delete (a `temp` flag moves a world to a synthetic "Temp/Deleted" group instead of removing the file). | Built — the Worlds screen. No grouping or soft-delete equivalent yet. |
| **"Connect to" a site** | Quick/manual connect (`/connect`, `/quick`) bypassing a saved world, per `lib/help/QuickConnect.md`. | Built — "Connect without saving." |
| **Manage Auto Connects** | Per-world `world($w,autoconnect)` flag — a world dials itself automatically when the app starts, rather than waiting for the user to pick it. Managed alongside the other per-world connection settings, not a separate screen. | **Not built.** No world currently auto-connects on launch; every connection today is user-initiated (picking a world/character, or the one exception — auto-login on character pick — sends the login line but still requires the explicit tap that got you there). |
| **Log button** | `doLog`/log menu (`potato.tcl:1552-1899`) — starts/stops writing the scrollback to a file, either plain text or HTML. The HTML export walks the output text widget's own tag dump to reconstruct styled `<span>`s with inline CSS matching the world's *live* configured ANSI palette, so the saved log visually matches what was on screen. The log filename itself is run through `clock format` first, so a filename like `%Y-%m-%d.log` auto-date-stamps. No rotation or size limiting — a log just grows until stopped. Multiple simultaneous log files per connection are supported. | **Not built.** |
| **Text editor + "Send to" world** | Potato's Upload window (part of `potato.tcl`'s core responsibilities, documented in `lib/help/UploadFile.md`) — a scratch space to compose or paste a block of text (typically code) and send it to the connection, presumably line-by-line given telnet's line-oriented nature. | **Not built.** Notably close in spirit to the `FugueEdit`-style external-editor trigger already discussed for this app (capture text, edit it, send it back) — worth designing these together rather than as unrelated features, since they solve the same underlying problem (composing something longer than fits comfortably in the single-line input bar) from two different directions. |
| **Mail button, multiple formats** | Mail window (`potato.tcl:778-977`) — a To/CC/BCC/Subject/Body composer that formats an in-game `@mail`-style command via per-world templates (`mailFormat`/`mailFormat,custom`) with presets for different MU* dialects (MUSH/MUX/MUSE/etc., `potato-config.tcl:237-241`). An option remaps literal newlines in the body to a placeholder token, since most in-game mail commands are single-line. Pure text templating — no real mail protocol involved. | **Not built.** |
| **Help button per section** | `potato-wikihelp.tcl` renders the bundled `lib/help/*.md` files as a contextual in-app help browser, reachable per-dialog. | **Not built.** Lowest priority of this group — this app's Settings/editor screens are simpler than Potato's dialogs and likely need less hand-holding, but worth a lightweight equivalent (e.g. a help link per settings screen) if the surface area grows. |

None of these require reversing anything flagged as obsolete in §§1-5 — they layer cleanly on top of the existing World/Trigger/SlashCommand data model (per-world or Global, same as everything else built so far).

---

## Summary: non-obvious things worth remembering

1. Reconnect fires on read *errors* but deliberately not on clean server EOF in real Potato — this app currently reconnects on both, a conscious divergence.
2. Reconnect delay is flat/fixed with no backoff or retry cap, in both clients.
3. TLS cert validation is deliberately disabled in Potato because most MUSHes self-sign — a real-world compatibility tradeoff, not an oversight to "fix" reflexively.
4. No MXP, MSDP, GMCP, or MCCP anywhere in Potato — any of those in this app would be new ground, not a port.
5. Potato has no truecolor ANSI support; this app already does — a genuine improvement, not a gap.
6. ANSI state (color/bold/underline) persists across lines by default in both clients — get this wrong and colors look randomly "leaky" or "reset."
7. Potato's main output scrollback limit is configured but silently never enforced — an unbounded-growth bug worth deliberately avoiding here, especially on mobile.
8. "World-then-global, both apply" is the consistent precedence rule across Events/Macros/Timers/Slash-Commands in Potato, and already matches how this app's Triggers/Slash Commands work.
9. Potato's passwords are only obfuscated, not encrypted — an honest weak point in the reference client, not a bar this app needs to clear, but also not one to imitate if better is easy.
10. Potato has zero tab-completion; that's not a gap versus Potato, since Potato never had it either.
11. Potato's "skins" are a full pluggable-UI-chrome contract (~40 procedures), a much bigger concept than a color theme — worth keeping in mind if this app's theming is ever compared to Potato's and found "simpler," since they're not aiming at the same scope.
