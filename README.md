# Scheduling Agent

A local-first LLM scheduling assistant: ask it natural language questions about your schedule, and it answers by calling a live Google Calendar tool — running entirely on-device via Ollama, with no cloud LLM in the loop.

## Features

- **Local LLM inference via Ollama** — running Gemma entirely on-device, so no cloud LLM ever sees your calendar data or requests
- **Tool-calling via LangChain4j** — the agent doesn't hallucinate schedule details; a system prompt instructs it to call getSchedule before answering, so responses are grounded in real calendar data.
- **Multi-calendar aggregation** — pulls upcoming events from multiple Google Calendar IDs in one tool call and combines them into a single response, including a Canvas-synced calendar so class deadlines show up alongside personal events.
- **Timezone-aware formatting** — event times are converted from epoch milliseconds to the local system timezone before being handed to the model, and all-day items (like assignment due dates) are handled separately from timed events.
- **Secure OAuth2 authentication** — read-only calendar scope (`CALENDAR_READONLY`), with tokens cached locally in `tokens/` rather than re-authenticating every run.
- **Conversational memory** — a sliding window of the last 10 messages is kept, so follow-up questions ("what about tomorrow?") work without repeating context.
- **Interactive CLI** — a simple REPL loop for asking natural language questions about your schedule.

## Tech stack

- Java
- LangChain4j (LLM orchestration + tool-calling)
- Ollama, running Gemma (local model inference)
- Google Calendar API (OAuth2, read-only scope)
- Maven (wrapper included)

## Running it

```bash
# 1. Install and start Ollama, then pull the model
ollama pull gemma4:e4b

# 2. Add your Google OAuth2 credentials.json to src/main/resources
#    and list your calendar IDs in ScheduleTool.getSchedule()

# 3. Run the app
mvn compile exec:java -Dexec.mainClass="com.gemmaagent.Main"
```

On first run, you'll be prompted to authenticate via Google's OAuth2 consent flow (served locally on port 8888); the resulting token is cached in `tokens/` for subsequent runs.

## Project layout

```
src/main/java/com/gemmaagent/
  Main.java                      entry point: model setup, ScheduleTool, PlannerAgent interface, CLI loop
src/main/resources/
  credentials.json                Google OAuth2 client credentials (gitignored)
tokens/                            cached OAuth2 tokens after first auth (gitignored)
```

## Roadmap

The current agent is read-only: it can answer questions about your schedule, but it can't yet act on it. That's the direction I'm building toward:

- **Write access** — event creation and rescheduling as real LangChain4j tools, not just read access
- **Conflict resolution** — detecting overlaps against existing events and proposing alternatives before committing a new one
- **An interactive avatar interface** — a persona-driven front end rather than a CLI prompt, so interacting with the agent feels like talking to an assistant rather than issuing commands
- **Life-area integration beyond the calendar** — extending the same tool-calling architecture into other daily-life domains (tasks, habits, routines), not just scheduling
- **Gamification & a training loop** — turning consistent use into a rewarding feedback loop, with progress tracking that reinforces good habits over time
- **Multi-account synchronization** — managing multiple calendars/accounts through a single agent session

This roadmap is directional, not committed — priorities may shift as the core experience develops.

## Notes

This project currently demonstrates local LLM tool-use grounded in live external data (LangChain4j + Ollama + Google Calendar), not autonomous calendar management — the agent reads and reasons about your schedule, but it does not yet create events, modify events, or resolve conflicts.
