# PocketAgent

> An AI agent that controls your Android phone. Give it a goal in plain English — it figures out what to tap, type, and swipe.

**[Download Android APK (v0.3.1)](https://github.com/unitedbyai/pocketagent/releases/download/v0.3.1/app-debug.apk)** | **[Discord](https://discord.gg/nRHKQ29j)**

*Read this in other languages: [Polski](README.pl.md)*

PocketAgent breathes new life into your old Android devices by turning them into autonomous AI agents. By interpreting the Android accessibility tree, it operates your device intelligently—no APIs required.

**💡 100% Free & Open Source**  
This version of PocketAgent is fully self-hosted and **100% free forever**. All previous premium restrictions, payment walls, and license key requirements have been completely removed. You have full access to all features, including unlimited API keys.

---

## ⚡ How It Works

PocketAgent uses a simple **Perception → Reasoning → Action** loop:
1. **Perceive:** Reads the screen using Android's Accessibility Tree to find interactive elements.
2. **Reason:** Sends the screen state and your goal to an LLM (like Groq, OpenAI, or local Ollama) to decide the next move.
3. **Act:** Executes the tap, swipe, or typing action via ADB, then repeats until the goal is achieved.

It includes built-in safeguards like stuck loop detection, repetition tracking, and vision fallback (taking screenshots when the accessibility tree is empty) to ensure reliable execution.

---

## 🚀 Quick Setup

### 1. Database Configuration
PocketAgent uses PostgreSQL. We recommend a free [Neon](https://neon.tech) database.
1. Create a Neon project and copy the connection string.
2. Add it to your `.env` and `web/.env` files: `DATABASE_URL=postgres://...`

### 2. LLM Provider
Edit your `.env` to configure an LLM. For a free start, we recommend Groq:
```bash
LLM_PROVIDER=groq
GROQ_API_KEY=gsk_your_key_here
```
*(You can also use `ollama` for fully local execution, `openai`, `openrouter`, or `bedrock`).*

### 3. Server Startup
Start the backend server and web dashboard:
```bash
bun install
bun run dev
```
Visit `http://localhost:5173` to access your local dashboard and generate a free API key.

### 4. Android App Installation
You need the Companion App on your phone to allow the agent to control it.

**Option A: Automated GitHub Build**
1. Go to the "Actions" tab in your GitHub repository.
2. Click the latest "Android Build" workflow run.
3. Download the `app-debug` zip from the "Artifacts" section.
4. Extract the APK, transfer it to your phone, and install. *(Note: If Play Protect blocks it, click "More details" -> "Install anyway").*

**Option B: Build from Source**
```bash
cd android
./gradlew installDebug
```

Once installed, launch the app, grant the necessary Accessibility and Screen Capture permissions, and enter your local Server URL and API Key.

---

## 🎮 Usage Modes

PocketAgent supports different execution modes depending on your needs:

1. **Interactive Mode:** Type goals on the fly.
   ```bash
   bun run src/kernel.ts
   ```
2. **Workflows (JSON/AI):** Chain complex goals across multiple apps using the LLM.
   ```bash
   bun run src/kernel.ts --workflow examples/workflows/research/weather-to-whatsapp.json
   ```
3. **Flows (YAML/No AI):** Execute fast, deterministic macros without LLM calls.
   ```bash
   bun run src/kernel.ts --flow examples/flows/send-whatsapp.yaml
   ```

## 🛠️ Community & Support
This is a community-driven fork aimed at making PocketAgent accessible to everyone. If you run into issues, check your `DATABASE_URL` and `adb devices` connection, or drop by the Discord!
