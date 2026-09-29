package com.gemmaagent;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class Main {

    static class ScheduleTool {
        private static final String APPLICATION_NAME = "Gemma Local Planner";
        private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
        private static final java.io.File TOKENS_DIRECTORY = new java.io.File("tokens");

        private Calendar getCalendarService() throws Exception {
            InputStream in = Main.class.getResourceAsStream("/credentials.json");
            if (in == null) {
                throw new RuntimeException("Resource not found: /credentials.json in src/main/resources/");
            }

            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JSON_FACTORY,
                    clientSecrets,
                    Collections.singletonList(CalendarScopes.CALENDAR_READONLY))
                    .setDataStoreFactory(new com.google.api.client.util.store.FileDataStoreFactory(TOKENS_DIRECTORY))
                    .setAccessType("offline")
                    .build();

            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
            Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

            return new Calendar.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        }

        @Tool("Retrieves the user's live schedule, classes, and Canvas assignments from Google Calendar")
        public String getSchedule() {
            try {
                Calendar service = getCalendarService();
                DateTime now = new DateTime(System.currentTimeMillis());

                // 1. Enter all your Calendar IDs here in this array:
                String[] myCalendars = {
                        "YOUR CALENDAR ID",
                        "YOUR CALENDAR ID"
                };

                StringBuilder schedule = new StringBuilder("Upcoming Schedule & Deadlines:\n");
                boolean foundAny = false;
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a");

                // 2. Loop through every calendar ID and fetch upcoming items
                for (String calendarId : myCalendars) {
                    try {
                        Events events = service.events().list(calendarId)
                                .setMaxResults(10)
                                .setTimeMin(now)
                                .setOrderBy("startTime")
                                .setSingleEvents(true)
                                .execute();

                        List<Event> items = events.getItems();
                        for (Event event : items) {
                            foundAny = true;

                            if (event.getStart().getDateTime() == null) {
                                // Handles all-day assignment deadlines
                                schedule.append(event.getStart().getDate()).append(" (All Day) - ").append(event.getSummary()).append("\n");
                            } else {
                                // Handles timed events converted to your local time zone
                                long epochMilli = event.getStart().getDateTime().getValue();
                                java.time.ZonedDateTime localTime = java.time.Instant.ofEpochMilli(epochMilli)
                                        .atZone(java.time.ZoneId.systemDefault());

                                schedule.append(localTime.format(formatter)).append(" - ").append(event.getSummary()).append("\n");
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Could not fetch events for calendar ID: " + calendarId);
                    }
                }

                if (!foundAny) {
                    return "No upcoming events found.";
                }

                return schedule.toString();

            } catch (Exception e) {
                System.err.println("\n--- GOOGLE CALENDAR API ERROR ---");
                e.printStackTrace();
                System.err.println("----------------------------------\n");
                return "Failed to retrieve calendar data: " + e.getMessage();
            }
        }
    }

    interface PlannerAgent {
        @SystemMessage("You are a scheduling assistant. You MUST use the getSchedule tool to retrieve the user's schedule before answering.")
        String chat(String userMessage);
    }

    public static void main(String[] args) {
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("gemma4:e4b")
                .temperature(0.0)
                .build();

        PlannerAgent agent = AiServices.builder(PlannerAgent.class)
                .chatLanguageModel(model)
                .tools(new Main.ScheduleTool())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        Scanner scanner = new Scanner(System.in);
        System.out.println("=== Live Gemma Planner Agent Initialized ===");
        System.out.println("Hi, I'm Gemma! I am now connected to your Google Calendar.");
        System.out.println("Type 'exit' or 'quit' at any time to end the session.\n");

        while (true) {
            System.out.print("Type your question: ");
            String userInput = scanner.nextLine();

            if (userInput.equalsIgnoreCase("exit") || userInput.equalsIgnoreCase("quit")) {
                System.out.println("Exiting... Goodbye!");
                break;
            }
            if (userInput.trim().isEmpty()) continue;

            System.out.println("Processing input...\n");
            String response = agent.chat(userInput);
            System.out.println("Gemma:\n" + response + "\n");
        }
        scanner.close();
    }
}
