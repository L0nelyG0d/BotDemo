package com.example.botdemo;

import com.example.botdemo.repository.UserRepository;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class TelegramBotHandler extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    private Map<String, String> sessionCookies = new HashMap<>();

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String chatId = update.getMessage().getChatId().toString();
            String messageText = update.getMessage().getText();

            if (messageText.equalsIgnoreCase("/start")) {
                sendMessage(chatId, "Welcome to the bot! Use /login <username> <password> to log in and /schedule to view your schedule.");
            } else if (messageText.startsWith("/login")) {
                String[] parts = messageText.split(" ");
                if (parts.length == 3) {
                    String username = parts[1];
                    String password = parts[2];
                    login(chatId, username, password);
                } else {
                    sendMessage(chatId, "Usage: /login <username> <password>");
                }
            } else if (messageText.equalsIgnoreCase("/schedule")) {
                if (sessionCookies != null && !sessionCookies.isEmpty()) {
                    fetchSchedule(chatId);
                } else {
                    sendMessage(chatId, "Please log in first using /login <username> <password>.");
                }
            } else {
                sendMessage(chatId, "Unknown command. Use /login or /schedule.");
            }
        }
    }

    private void login(String chatId, String username, String password) {
        try {
            String loginUrl = "https://my.sdu.edu.kz/loginAuth.php";

            Connection.Response loginResponse = Jsoup.connect(loginUrl)
                    .data("username", username)
                    .data("password", password)
                    .data("modstring", "")
                    .data("LogIn", "Log in")
                    .method(Connection.Method.POST)
                    .execute();

            sessionCookies = loginResponse.cookies();
            System.out.println("Login successful. Cookies: " + sessionCookies);

            if (!sessionCookies.isEmpty()) {
                sendMessage(chatId, "Successfully logged in!");
            } else {
                sendMessage(chatId, "Login failed. Please check your credentials.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            sendMessage(chatId, "Login failed due to an error. Please try again.");
        }
    }

    private void fetchSchedule(String chatId) {
        try {
            String scheduleUrl = "https://my.sdu.edu.kz/index.php?mod=course_reg"; // URL for schedule
            Document document = Jsoup.connect(scheduleUrl)
                    .cookies(sessionCookies)
                    .get();

            String scheduleInfo = parseScheduleHtml(document);
            if (!scheduleInfo.isEmpty()) {
                sendMessage(chatId, "Your schedule:\n" + scheduleInfo);
            } else {
                sendMessage(chatId, "No schedule information found.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            sendMessage(chatId, "Failed to retrieve schedule. Please try again.");
        }
    }

    private String parseScheduleHtml(Document document) {
        // Map to hold schedule data organized by day
        Map<String, StringBuilder> scheduleByDay = new HashMap<>();
        String[] days = {"Mo", "Tu", "We", "Th", "Fr", "Sa"};

        // Initialize the map with empty builders for each day
        for (String day : days) {
            scheduleByDay.put(day, new StringBuilder(day + ":\n"));
        }

        // Find the schedule rows
        Elements timeRows = document.select("tr[align=center]");

        for (Element row : timeRows) {
            // Extract the time slot (e.g., "08:30\n09:20")
            Element timeCell = row.selectFirst("td[style]");
            String timeSlot = timeCell != null ? timeCell.text().replace("\n", " - ") : "";

            // Extract cells for each day
            Elements dayCells = row.select("td.clsTd");

            for (int i = 1; i < dayCells.size(); i++) { // Start from index 1 to skip the time slot column
                String day = days[i - 1];
                Element cell = dayCells.get(i);
                Element courseDiv = cell.selectFirst("div.inbasket");

                if (courseDiv != null) {
                    String courseDetails = courseDiv.text().trim();
                    scheduleByDay.get(day).append("  ").append(timeSlot).append(": ").append(courseDetails).append("\n");
                }
            }
        }

        // Combine all schedules by day into a single string
        StringBuilder sortedSchedule = new StringBuilder();
        for (String day : days) {
            sortedSchedule.append(scheduleByDay.get(day).toString()).append("\n");
        }

        return sortedSchedule.toString();
    }

    private void sendMessage(String chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}