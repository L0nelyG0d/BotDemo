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
                }
                else {
                    sendMessage(chatId, "Please log in first using /login <username> <password>.");
                }
            }
            else if (messageText.equalsIgnoreCase("/attendance")) {
                if (sessionCookies != null && !sessionCookies.isEmpty()) {
                    fetchAttendance(chatId);
                } else {
                    sendMessage(chatId, "Please log in first using /login <username> <password>.");
                }
            }
            else {
                sendMessage(chatId, "Unknown command. Use /login or /schedule or /attendance.");
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

    private String parseAttendanceHtml(Document document) {
        StringBuilder attendanceInfo = new StringBuilder();

        Elements rows = document.select("tr[bgcolor=white], tr[bgcolor=#f9f9f9]");

        for (Element row : rows) {
            try {
                Elements cells = row.select("td");

                if (cells.size() > 7) {
                    String courseCode = cells.get(1).text().trim();

                    Element courseNameLabel = cells.get(2).selectFirst("label");
                    String courseName = courseNameLabel != null ? courseNameLabel.text().trim() : "Unknown";

                    Element attendanceDiv = cells.get(9).selectFirst("div[title]");
                    String attendance = attendanceDiv != null ? attendanceDiv.attr("title").trim() : "N/A";

                    attendanceInfo.append("Course Code: ").append(courseCode)
                            .append(", Name: ").append(courseName)
                            .append(", Attendance: ").append(attendance).append("\n");
                }
            } catch (Exception e) {
                System.err.println("Error parsing row: " + row.html());
                e.printStackTrace();
            }
        }

        return attendanceInfo.toString();
    }

    private void fetchAttendance(String chatId) {
        try {
            String attendanceUrl = "https://my.sdu.edu.kz/index.php?mod=ejurnal";

            Document document = Jsoup.connect(attendanceUrl)
                    .cookies(sessionCookies)
                    .get();

            String attendanceInfo = parseAttendanceHtml(document);

            if (!attendanceInfo.isEmpty()) {
                sendMessage(chatId, "Your attendance:\n" + attendanceInfo);
            } else {
                sendMessage(chatId, "No attendance information found.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            sendMessage(chatId, "Failed to retrieve attendance. Please try again.");
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
        Map<String, StringBuilder> scheduleByDay = new HashMap<>();
        String[] days = {"Mo", "Tu", "We", "Th", "Fr", "Sa"};

        for (String day : days) {
            scheduleByDay.put(day, new StringBuilder(day + ":\n"));
        }

        Elements timeRows = document.select("tr[align=center]");

        for (Element row : timeRows) {
            Element timeCell = row.selectFirst("td[style]");
            String timeSlot = timeCell != null ? timeCell.text().replace("\n", " - ") : "";

            Elements dayCells = row.select("td.clsTd");

            for (int i = 1; i < dayCells.size(); i++) {
                String day = days[i - 1];
                Element cell = dayCells.get(i);
                Element courseDiv = cell.selectFirst("div.inbasket");

                if (courseDiv != null) {
                    String courseDetails = courseDiv.text().trim();
                    scheduleByDay.get(day).append("  ").append(timeSlot).append(": ").append(courseDetails).append("\n");
                }
            }
        }

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