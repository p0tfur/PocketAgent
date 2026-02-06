# How It All Connects — Web UI to Phone Execution

Complete breakdown of what happens when you type "Send Mom I'll be late tonight" on a web page and it executes on an Android phone.

---

## You Have 3 Physical Things

```
📱 Phone 1 — your daily phone (in your pocket, anywhere)
💻 Laptop  — sitting at home, always on, plugged in
📱 Phone 2 — the agent phone (sitting next to laptop, on WiFi)
```

The laptop does EVERYTHING. It runs both the web app AND the kernel.

```
Phone 1 = the remote control  (just a web browser)
Laptop  = the brain           (runs the website + AI + sends ADB commands)
Phone 2 = the robot hands     (receives ADB commands, taps/types on screen)
```

Phone 1 never talks to Phone 2 directly. Everything goes through the laptop.

---

## What's Running on the Laptop

```
💻 Your Laptop (home desk, always on)
┌─────────────────────────────────────────┐
│                                         │
│   1. SvelteKit app (the website)        │
│      - Shows a text box + run button    │
│      - Listens on port 3000             │
│                                         │
│   2. Kernel (the AI brain)              │
│      - Gets called BY the SvelteKit app │
│      - Talks to Groq/OpenAI over internet│
│      - Sends ADB commands to Phone 2    │
│                                         │
│   3. ADB connection to Phone 2          │
│      - adb connect 192.168.1.42:5555    │
│      - Already paired, always connected │
│                                         │
│   4. Tailscale (just networking)        │
│      - Makes this laptop reachable      │
│        from anywhere as 100.64.0.2      │
│                                         │
└─────────────────────────────────────────┘
```

---

## What Happens Step by Step

### Step 0: Setup (one time)

```
You plug Phone 2 into laptop USB
You run: adb tcpip 5555
You unplug Phone 2, put it on charger next to laptop
Laptop runs: adb connect 192.168.1.42:5555 ← Phone 2's WiFi IP
Now laptop can control Phone 2 wirelessly
You start the SvelteKit app: bun run dev
Tailscale is running on laptop + Phone 1
```

### Step 1: You open the web app

```
📱 Phone 1 (you're at a coffee shop)
    │
    │  You open browser: http://100.64.0.2:3000
    │                     ^^^^^^^^^^^^^^^^
    │                     This is your laptop's Tailscale IP
    │
    │  Tailscale encrypts this and tunnels it to your laptop at home
    │
    ▼
💻 Laptop (at home)
    SvelteKit serves the web page back to your phone's browser
```

### Step 2: You type the goal and hit Run

```
📱 Phone 1 browser
    │
    │  You type: "Send Mom I'll be late tonight"
    │  You tap: [RUN]
    │
    │  Browser sends: POST http://100.64.0.2:3000/api/run
    │                 body: { goal: "Send Mom I'll be late tonight" }
    │
    ▼
💻 Laptop receives this HTTP request
    │
    │  SvelteKit API route catches it
    │  Calls: kernel.run("Send Mom I'll be late tonight")
    │
    │  NOW THE KERNEL LOOP STARTS (on the laptop):
    │
    ▼
```

### Step 3: Kernel loop (runs on laptop, controls Phone 2)

```
💻 Laptop                                          📱 Phone 2
    │                                                   │
    │  adb shell uiautomator dump ────────────────────>│
    │  "tell me what's on your screen"                  │
    │                                                   │ (sends XML back)
    │<──────────────────────────────────────────────────│
    │                                                   │
    │  Parses XML: "home screen, WhatsApp icon at 540,800"
    │                                                   │
    │  Sends to Groq API ──────────────> ☁️ Internet    │
    │  "screen shows home, goal is send msg to Mom"     │
    │                                                   │
    │  Groq replies: { action: "launch", package: "com.whatsapp" }
    │                                                   │
    │  adb shell monkey -p com.whatsapp ──────────────>│
    │  "open WhatsApp"                                  │ (WhatsApp opens)
    │                                                   │
    │  (waits 2 seconds)                                │
    │                                                   │
    │  adb shell uiautomator dump ────────────────────>│
    │  "what's on screen now?"                          │
    │                                                   │ (sends XML back)
    │<──────────────────────────────────────────────────│
    │                                                   │
    │  "WhatsApp is open, I see search icon"            │
    │                                                   │
    │  ... repeats 5 more times until message is sent   │
    │                                                   │
    │  Kernel returns: { success: true, steps: 7 }     │
    │                                                   │
```

### Step 4: Result comes back to your phone

```
💻 Laptop
    │
    │  kernel.run() finished
    │  SvelteKit sends HTTP response back
    │
    ▼
📱 Phone 1 (still at coffee shop)
    │
    │  Browser shows: "Done! Sent in 7 steps (12.4s)"
```

---

## Where Tailscale Fits

Tailscale is just a wire. It connects Phone 1 to the laptop when they're on different networks. If they're on the same WiFi, you don't even need Tailscale.

```
Without Tailscale:  Phone 1 ──WiFi──> Laptop ──WiFi──> Phone 2
                    (must be same WiFi)

With Tailscale:     Phone 1 ──Tailscale tunnel──> Laptop ──WiFi──> Phone 2
                    (works from anywhere)
```

Tailscale is invisible to the kernel. The kernel doesn't know or care about Tailscale. It just talks to ADB like normal. Tailscale just makes the network path between Phone 1's browser and the laptop work across the internet.

```
WHAT GETS INSTALLED WHERE:

  Phone 1:  Tailscale app (from Play Store)
  Laptop:   Tailscale daemon (curl install)
  Phone 2:  NOTHING. Just USB debugging ON.
```

---

---

# Technical Deep Dive

Detailed diagrams for implementation reference.

---

## The 3 Pieces (Technical View)

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   PHONE 1       │     │   LAPTOP/SERVER  │     │   PHONE 2       │
│   (your daily)  │     │   (Raspberry Pi, │     │   (agent phone)  │
│                 │     │    VPS, laptop)  │     │                 │
│   Browser with  │     │   SvelteKit app  │     │   Android phone │
│   SvelteKit UI  │────>│   + Kernel       │────>│   with USB      │
│                 │     │                  │     │   debugging ON  │
│   "Send Mom     │     │   Runs the AI    │     │                 │
│    I'll be late │     │   loop + ADB     │     │   WhatsApp,     │
│    tonight"     │     │   commands       │     │   Settings, etc │
└─────────────────┘     └─────────────────┘     └─────────────────┘
     YOU                    THE BRAIN               THE HANDS
```

---

## Without Tailscale (Same WiFi Network)

When all 3 devices are on the same home/office WiFi:

```
┌──────────────────────── Home WiFi (192.168.1.x) ────────────────────────┐
│                                                                          │
│   Phone 1                    Laptop                     Phone 2          │
│   192.168.1.10               192.168.1.100              192.168.1.42     │
│                                                                          │
│   Browser ──HTTP──> SvelteKit (:3000)                                   │
│                        │                                                 │
│                        │ kernel.run("Send Mom...")                       │
│                        │                                                 │
│                        ├──ADB WiFi──> adb connect 192.168.1.42:5555     │
│                        │              adb shell uiautomator dump        │
│                        │              adb shell input tap 540 1200      │
│                        │              adb shell input text "I'll be..." │
│                        │                                                 │
│                        ├──HTTPS──> Groq/OpenAI API (LLM decision)       │
│                        │                                                 │
│                        │ result: { success: true, steps: 7 }            │
│                        │                                                 │
│   Browser <──HTTP──    │                                                 │
│   "Done! Sent in                                                        │
│    7 steps"                                                              │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## With Tailscale (From Anywhere)

Tailscale creates a private encrypted network across all your devices, no matter where they are. Each device gets a stable IP (100.x.x.x) that works from anywhere.

```
┌─── Phone 1 (coffee shop wifi) ───┐
│   Tailscale IP: 100.64.0.1       │
│   Browser                         │
│      │                            │
└──────│────────────────────────────┘
       │
       │  HTTPS (encrypted, via Tailscale tunnel)
       │  https://100.64.0.2:3000
       │
  ─────│──────── Internet ────────────────
       │
       │
┌──────│──────────────────────────────────────────┐
│      │                                           │
│   ┌──▼──────────────────┐    ┌────────────────┐ │
│   │ Laptop               │    │ Phone 2        │ │
│   │ Tailscale: 100.64.0.2│   │ 192.168.1.42   │ │  ← Same local
│   │                      │    │                │ │     network
│   │ SvelteKit + Kernel   │────│ ADB WiFi :5555 │ │
│   │                      │    │                │ │
│   └──────────────────────┘    └────────────────┘ │
│                                                   │
│              Home Network                         │
└───────────────────────────────────────────────────┘
```

---

## The Full Sequence — With Timestamps

Here's exactly what happens when you type "Send Mom I'll be late tonight" and hit enter:

```
TIME    PHONE 1 (browser)          LAPTOP (SvelteKit + Kernel)         PHONE 2 (agent)
─────   ─────────────────          ───────────────────────────         ────────────────

0.0s    User types goal
        "Send Mom I'll be
        late tonight"
        Hits ENTER
           │
           │  POST /api/run
           │  { goal: "Send Mom..." }
           │
0.1s       │──────────────────────>│
           │                       │  kernel.run(goal) starts
           │                       │
           │                       │  ┌─── STEP 1 ───────────────────────────────────┐
           │                       │  │                                               │
0.2s       │                       │──│── adb shell uiautomator dump ──────────────>│
           │                       │  │                                     dumps UI │
0.5s       │                       │<─│── XML file pulled back ────────────────────│
           │                       │  │                                               │
           │                       │  │  sanitizer.ts parses XML                     │
           │                       │  │  → 47 elements found                          │
           │                       │  │  → filtered to top 40                         │
           │                       │  │  → foreground: launcher                       │
           │                       │  │                                               │
0.6s       │                       │  │  Builds message for LLM:                     │
           │                       │  │  [system prompt + goal + screen state]        │
           │                       │  │                                               │
           │                       │──│── POST https://api.groq.com/chat ──> Internet
           │                       │  │   "Here's the screen, goal is..."             │
           │                       │  │                                               │
1.4s       │                       │<─│── LLM responds:                               │
           │                       │  │   {                                           │
           │  SSE: step 1          │  │     "think": "I'm on home screen,            │
           │  "Launching WhatsApp" │  │              need to open WhatsApp",          │
1.5s       │<─────────────────────│  │     "action": "launch",                      │
           │  (shows on UI)        │  │     "package": "com.whatsapp"                │
           │                       │  │   }                                           │
           │                       │  │                                               │
           │                       │──│── adb shell monkey -p com.whatsapp ────────>│
           │                       │  │                                    opens app │
1.8s       │                       │  │  sleep(2s) — wait for UI to settle           │
           │                       │  └───────────────────────────────────────────────┘
           │                       │
           │                       │  ┌─── STEP 2 ───────────────────────────────────┐
3.8s       │                       │──│── adb shell uiautomator dump ──────────────>│
           │                       │<─│── XML (WhatsApp home screen) ───────────────│
           │                       │  │                                               │
           │                       │  │  Elements: search icon, chats list, tabs...  │
           │                       │  │                                               │
           │                       │──│── POST to LLM ──────────────────> Internet   │
           │                       │<─│── { "action": "tap",                         │
           │  SSE: step 2          │  │     "coordinates": [978, 142],               │
           │  "Tapping search"     │  │     "think": "Tap search to find Mom" }      │
4.8s       │<─────────────────────│  │                                               │
           │                       │──│── adb shell input tap 978 142 ─────────────>│
           │                       │  │                                    taps icon │
           │                       │  └───────────────────────────────────────────────┘
           │                       │
           │                       │  ┌─── STEP 3 ───────────────────────────────────┐
           │                       │  │  (same pattern: dump → LLM → execute)        │
           │  SSE: step 3          │  │                                               │
           │  "Typing 'Mom'"       │──│── adb shell input text "Mom" ──────────────>│
           │                       │  └───────────────────────────────────────────────┘
           │                       │
           │                       │  ┌─── STEP 4 ───────────────────────────────────┐
           │  SSE: step 4          │  │                                               │
           │  "Tapping Mom's chat" │──│── adb shell input tap 540 380 ─────────────>│
           │                       │  └───────────────────────────────────────────────┘
           │                       │
           │                       │  ┌─── STEP 5 ───────────────────────────────────┐
           │  SSE: step 5          │  │                                               │
           │  "Typing message"     │──│── adb shell input text                      │
           │                       │  │   "I'll%sbe%slate%stonight" ───────────────>│
           │                       │  └───────────────────────────────────────────────┘
           │                       │
           │                       │  ┌─── STEP 6 ───────────────────────────────────┐
           │  SSE: step 6          │  │                                               │
           │  "Tapping send"       │──│── adb shell input tap 1005 2280 ───────────>│
           │                       │  └───────────────────────────────────────────────┘
           │                       │
           │                       │  ┌─── STEP 7 ───────────────────────────────────┐
           │  SSE: step 7          │  │  LLM: { "action": "done",                   │
           │  "Done! ✓"            │  │         "reason": "Message sent to Mom" }    │
12.4s      │<─────────────────────│  └───────────────────────────────────────────────┘
           │                       │
           │  Shows result:        │  Session log saved:
           │  "Completed in        │  logs/1706234567890.json
           │   7 steps (12.4s)"    │
```

---

## The 4 Communication Layers

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                     │
│  LAYER 4: User Interface                                           │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │ SvelteKit frontend (runs in Phone 1's browser)                │ │
│  │ - Text input for goal                                         │ │
│  │ - Real-time step updates via SSE (Server-Sent Events)        │ │
│  │ - Shows think/plan/progress from LLM                         │ │
│  │ - Displays screenshots if vision mode is on                  │ │
│  └───────────────────────────────────────────────────────────────┘ │
│           │ HTTP POST /api/run              ▲ SSE /api/run/stream  │
│           ▼                                 │                      │
│  LAYER 3: Web Server                                               │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │ SvelteKit backend (server-side, runs on the Laptop)           │ │
│  │ - API route: POST /api/run { goal }                           │ │
│  │ - Starts kernel.run() as async task                           │ │
│  │ - Streams step updates back to browser via SSE                │ │
│  │ - Stores session history in DB/files                          │ │
│  └───────────────────────────────────────────────────────────────┘ │
│           │ function call                                          │
│           ▼                                                        │
│  LAYER 2: Kernel (the brain)                                       │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │ kernel.ts — the agent loop                                    │ │
│  │                                                               │ │
│  │ for each step:                                                │ │
│  │   1. Call ADB to dump screen  ──────> (Layer 1)               │ │
│  │   2. Parse + filter elements                                  │ │
│  │   3. Send to LLM ──────────────────> Groq/OpenAI/etc (cloud) │ │
│  │   4. Parse LLM response                                      │ │
│  │   5. Execute action via ADB ──────> (Layer 1)                 │ │
│  │   6. Emit step event ─────────────> (Layer 3, for SSE)        │ │
│  │   7. Log to file                                              │ │
│  └───────────────────────────────────────────────────────────────┘ │
│           │ Bun.spawnSync()                                        │
│           ▼                                                        │
│  LAYER 1: ADB (the hands)                                          │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │ ADB over WiFi (TCP connection to Phone 2)                     │ │
│  │                                                               │ │
│  │ Laptop ──TCP:5555──> Phone 2                                  │ │
│  │                                                               │ │
│  │ Commands:                                                     │ │
│  │   adb shell uiautomator dump    (read screen)                │ │
│  │   adb shell input tap x y       (tap)                        │ │
│  │   adb shell input text "..."    (type)                       │ │
│  │   adb shell input swipe ...     (scroll)                     │ │
│  │   adb shell am start ...        (launch app)                 │ │
│  │   adb shell screencap           (screenshot)                 │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## SvelteKit App Structure

```
web/
├── src/
│   ├── routes/
│   │   ├── +page.svelte              ← The UI (goal input, step viewer)
│   │   ├── api/
│   │   │   ├── run/
│   │   │   │   └── +server.ts        ← POST /api/run — starts kernel
│   │   │   ├── stream/
│   │   │   │   └── +server.ts        ← GET /api/stream — SSE step updates
│   │   │   └── status/
│   │   │       └── +server.ts        ← GET /api/status — device connected?
│   ├── lib/
│   │   ├── kernel-bridge.ts           ← Imports kernel, wraps as async API
│   │   └── stores.ts                  ← Svelte stores for UI state
├── package.json
└── svelte.config.js

kernel (existing, no changes needed except kernel.ts):
├── src/
│   ├── kernel.ts                      ← Modified: export run() function
│   ├── actions.ts                     ← No changes
│   ├── llm-providers.ts               ← No changes
│   ├── sanitizer.ts                   ← No changes
│   ├── config.ts                      ← No changes
│   ├── constants.ts                   ← No changes
│   └── logger.ts                      ← No changes
```

---

## Data Flow Summary

```
YOU type "Send Mom I'll be late tonight"
    │
    ▼
Phone 1 browser ──HTTP POST──> Laptop (SvelteKit API route)
    │                               │
    │                               ▼
    │                          kernel.run(goal)
    │                               │
    │                               │ ┌──────── LOOP (7 times) ────────┐
    │                               │ │                                 │
    │                               │ │  1. adb shell uiautomator dump │──> Phone 2
    │                               │ │     (what's on screen?)        │<── XML
    │                               │ │                                 │
    │                               │ │  2. Parse XML → 40 elements    │
    │                               │ │                                 │
    │  SSE: live step updates       │ │  3. Send to LLM ──────────────>│──> Groq API
    │<──────────────────────────────│ │     (what should I do?)        │<── JSON
    │  "Step 3: Typing Mom"         │ │                                 │
    │                               │ │  4. Execute action             │
    │                               │ │     adb shell input tap x y   │──> Phone 2
    │                               │ │                                 │
    │                               │ │  5. Wait 2s for UI to settle  │
    │                               │ │                                 │
    │                               │ └─────────────────────────────────┘
    │                               │
    │  HTTP response: done          │
    │<──────────────────────────────│
    │                               │
    ▼                               ▼
"Done! 7 steps, 12.4s"        logs/session.json saved
```

---

## One-Line Summary

```
Browser (Phone 1) ──HTTP──> SvelteKit (Laptop) ──ADB WiFi──> Android (Phone 2)
                                  │
                                  ├──HTTPS──> LLM API (cloud) for decisions
                                  │
                            Tailscale makes this reachable from anywhere
```
