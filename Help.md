# YAM Help

A walkthrough of how to use YAM. See [FEATURES.md](FEATURES.md) for the full feature list.

## Getting connected

**Save a world.** From the Address Book, tap **New World** and give it a name, host, and
port. This just creates an entry — it doesn't connect anything by itself.

**Add a character (optional).** Select a saved world and tap **Connect To World** (or
double-tap the world) to see its character list, then **New Character** to save a name and
password. Tapping a saved character connects to that world *and* logs that character in
automatically — you'll never see the login line itself in the scrollback, so the password
never shows on screen.

Don't want to log in as anyone in particular? Tap **Connect without character** on the
character list, or **Connect without saving** on the Address Book for a one-off address you
don't want saved at all.

**Groups.** Organize your saved worlds into groups using the sidebar — **New Group** to
create one, select a world and **Edit World** to move it in or out. Deleting a group never
deletes the worlds inside it; they just become Ungrouped. **Copy World** duplicates a world's
name/host/port (not its characters) — handy for a second entry to the same server. Deleting a
world isn't final either — it moves to **Temp/Deleted**, where you can **Restore** it or
delete it permanently; every delete (world, character, group, trigger, slash command) asks
"Are you sure?" first.

**Characters list.** Settings → Characters shows every saved character across every world in
one list, if you'd rather jump straight to a character without opening its world first.

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
- The **Worlds** button, to the left of the tab row, always takes you back to the Address
  Book — regardless of how many tabs you have open or closed. Getting back to your terminal
  afterward — via the system Back button, the Address Book's own "< Back" (shown whenever
  there's somewhere to return to), or by reopening the same world — always lands you on the
  exact same tabs and scrollback, never a duplicate connection.
- The **⋮** in the top corner reaches Settings directly from the terminal — no need to leave
  your session just to check a character's password or grab a different world's host:port to
  send someone.

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
| `/timestamps` | Turn `[HH:MM:SS]` timestamps on new lines on or off for this tab (`/timestamps on`, `/timestamps off`) |

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

Reach Settings via the ⋮ menu — it's in the same top corner on every screen, including a live
terminal, so you're never more than one tap away from it.

- **Theme** — System default, Light, or Dark.
- **Auto-reconnect** — automatically redial after a dropped connection. Doesn't apply when
  you disconnect on purpose.
- **Worlds & Characters**, **Characters**, **Triggers**, **Slash Commands**, **Help** —
  manage everything described above, plus this page.

## Troubleshooting

- **A world isn't responding as expected** — try `/debug on` to see the raw telnet
  negotiation, which can reveal a server offering an option YAM doesn't yet act on.
- **A connection keeps dropping** — check Auto-reconnect is on in Settings; YAM will keep
  retrying until you disconnect that tab manually.
- **Lost track of a tab** — the tab stays open even after a disconnect; check the tab row
  before assuming a connection is gone.
