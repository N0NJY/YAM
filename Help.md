# YAM Help

A walkthrough of how to use YAM. See [FEATURES.md](FEATURES.md) for the full feature list.

## Getting connected

**Save a world.** From the Worlds screen, tap **New World** and give it a name, host, and
port. This is just an address book entry — it doesn't connect anything by itself.

**Add a character (optional).** Tap a saved world to see its character list, then **New
Character** to save a name and password. Tapping a saved character connects to that world
*and* logs that character in automatically — you'll never see the login line itself in the
scrollback, so the password never shows on screen.

Don't want to log in as anyone in particular? Tap **Connect without character** on the
character list, or **Connect without saving** on the Worlds screen for a one-off address you
don't want saved at all.

**Players list.** Settings → Players shows every saved character across every world in one
list, if you'd rather jump straight to a character without opening its world first.

## Working with tabs

Every connection you open gets its own tab, shown in the row across the top of the session
screen. Tabs are fully independent — separate scrollback, separate input history, separate
connection state.

- Tap a tab to switch to it.
- Tap the **×** on a tab to close it. Closing is the *only* thing that removes a tab — if a
  connection drops or you tap Disconnect, the tab stays put showing its disconnected state,
  so you don't lose your place.
- Tap **+** to open a blank form for a new connection (a manual host/port, same as "Connect
  without saving").
- The **Worlds** button, to the left of the tab row, always takes you back to the Worlds
  list — regardless of how many tabs you have open or closed.

While any tab is connected (or reconnecting), a notification stays up so the connection
survives you switching away to another app. With more than one tab open, the notification
lists each one's status.

## Sending commands

Type into the box at the bottom and tap **Send** (or use your keyboard's Send/Enter key).
The up/down arrows to the left of the input box recall previously sent commands, like shell
history — each tab keeps its own.

### Slash commands

Typing `/something` at the start of a line is checked against a small set of built-in
commands first, then any custom ones you've defined, before being sent to the server as-is.

| Type this | To do this |
|---|---|
| `/cls` | Clear the screen |
| `/print hello` | Show a local note (not sent to the server) |
| `/time` | Show the current date/time |
| `/time HH:mm:ss` | Show the time in a custom format |
| `/web example.com` | Open a link in your browser |
| `/speedwalk 3n2e` | Send north, north, north, east, east |
| `/reconnect` | Reconnect this tab right now |
| `/disconnect` | Disconnect this tab |
| `/connect Cave Mush` | Redial this tab to a saved world by name |
| `/connect example.com:4201` | Redial this tab to a raw host:port |
| `/debug on` | Show raw telnet negotiation traffic (for troubleshooting a server) |

Typed a real command that happens to start with `/` and don't want it intercepted? Start it
with `//` instead — `//foo` always sends `/foo` to the server untouched.

**Custom slash commands** (Settings → Slash Commands) let you define your own shortcuts —
say, `/grab` expanding to a longer command your MU* understands, with `%0`–`%9` standing in
for whatever you typed after the command name. Set one up as *Global* to have it work on
every world, or scope it to just one.

## Triggers

Settings → Triggers lets you react automatically to text the server sends you. Each trigger
has:

- A **pattern** to match against incoming lines (wildcard, regex, or plain substring).
- One or more **actions**: recolor the matching line, hide it entirely, silently send a
  reply, or drop text into your input box for you to send yourself.

Like slash commands, a trigger can be scoped to one world or made *Global* (applies
everywhere). Triggers are checked in order, world triggers before Global ones; by default the
first match wins, but a trigger can be marked to let later ones also check the same line.

## Settings

Reach Settings from the Worlds screen (the ⋮ menu, top right).

- **Theme** — System default, Light, or Dark.
- **Auto-reconnect** — automatically redial after a dropped connection. Doesn't apply when
  you disconnect on purpose.
- **Worlds & Characters**, **Players**, **Triggers**, **Slash Commands** — manage everything
  described above.

## Troubleshooting

- **A world isn't responding as expected** — try `/debug on` to see the raw telnet
  negotiation, which can reveal a server offering an option YAM doesn't yet act on.
- **A connection keeps dropping** — check Auto-reconnect is on in Settings; YAM will keep
  retrying until you disconnect that tab manually.
- **Lost track of a tab** — the tab stays open even after a disconnect; check the tab row
  before assuming a connection is gone.
