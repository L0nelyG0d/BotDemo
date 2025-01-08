package com.example.botdemo;
import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import com.example.botdemo.repository.UserRepository;
import com.example.botdemo.User;
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
import org.jsoup.Connection;
import org.jsoup.Jsoup;

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

    private Map<String, String> sessionCookies;

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    public static void disableSSLVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        disableSSLVerification();
        if (update.hasMessage() && update.getMessage().hasText()) {
            String chatId = update.getMessage().getChatId().toString();
            String messageText = update.getMessage().getText();

            if (messageText.equalsIgnoreCase("/start")) {
                sendMessage(chatId, "Welcome to the bot! Please choose:\n/register <username> <password>\n/login <username> <password>\n/attendance to view your attendance.");
            } else if (messageText.startsWith("/register")) {
                String[] parts = messageText.split(" ");
                if (parts.length == 3) {
                    String username = parts[1];
                    String password = parts[2];
                    handleRegistration(chatId, username, password);
                } else {
                    sendMessage(chatId, "Usage: /register <username> <password>");
                }
            } else if (messageText.startsWith("/login")) {
                String[] parts = messageText.split(" ");
                if (parts.length == 3) {
                    String username = parts[1];
                    String password = parts[2];
                    handleLogin(chatId, username, password);
                } else {
                    sendMessage(chatId, "Usage: /login <username> <password>");
                }
            } else if (messageText.equalsIgnoreCase("/attendance")) {
                if (sessionCookies != null && !sessionCookies.isEmpty()) {
                    getAttendance(chatId); // Fetch attendance
                } else {
                    sendMessage(chatId, "Please log in first using /login <username> <password>.");
                }
            } else if (messageText.equalsIgnoreCase("yes")) {
                loginToPortal(chatId, "your_sdu_username", "your_sdu_password");
            } else if (messageText.equalsIgnoreCase("no")) {
                sendMessage(chatId, "Alright! Let me know if you need anything else.");
            } else {
                sendMessage(chatId, "Unknown command. Use /register, /login, or /attendance.");
            }
        }
    }

    private void handleRegistration(String chatId, String username, String password) {
        if (userRepository.findByUsername(username) != null) {
            sendMessage(chatId, "Username already exists. Please choose a different one.");
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(password); // Ideally, hash the password before saving.
        userRepository.save(user);

        sendMessage(chatId, "Registration successful. You can now log in with /login.");
    }

    private void handleLogin(String chatId, String username, String password) {
        User user = userRepository.findByUsername(username);
        if (user != null && user.getPassword().equals(password)) {
            sendMessage(chatId, "Login successful. Welcome, " + username + "!");
            sendMessage(chatId, "Would you like to log in to your SDU account? Reply 'yes' or 'no'.");
        } else {
            sendMessage(chatId, "Invalid username or password.");
        }
    }

    public void getAttendance(String chatId) {
        try {
            String attendanceUrl = "https://my.sdu.edu.kz/index.php?mod=ejurnal";

            Connection.Response response = Jsoup.connect(attendanceUrl)
                    .cookies(sessionCookies)
                    .method(Connection.Method.GET)
                    .execute();

            String responseBody = response.body();
            String attendanceInfo = parseAttendanceHtml(responseBody);

            sendMessage(chatId, "Your attendance:\n" + attendanceInfo);
        } catch (IOException e) {
            e.printStackTrace();
            sendMessage(chatId, "Failed to retrieve attendance information. Please try again later.");
        }

    }

    private String parseAttendanceHtml(String responseBody) {
        Document doc = Jsoup.parse(responseBody);

        Elements rows = doc.select("table tr");

        StringBuilder attendanceInfo = new StringBuilder();

        for(Element row : rows) {
            Elements cell = row.select("td");
            if(!cell.isEmpty()){
                String code = cell.get(0).text();
                String className = cell.get(1).text();
                String attendance = cell.get(cell.size() - 1).text();
                attendanceInfo.append("Code: ").append(code)
                        .append(", Course: ").append(className)
                        .append(", Absence: ").append(attendance).append("\n");
            }
        }
        return attendanceInfo.toString();
    }
    private void loginToPortal(String chatId, String portalUsername, String portalPassword) {
        disableSSLVerification();
        try {
            Connection.Response loginResponse = Jsoup.connect("https://my.sdu.edu.kz/index.php")
                    .data("username", portalUsername)
                    .data("password", portalPassword)
                    .method(Connection.Method.POST)
                    .execute();
            sessionCookies = loginResponse.cookies();
            sendMessage(chatId, "Successfully logged in to your SDU portal!");
        } catch (IOException e) {
            e.printStackTrace();
            sendMessage(chatId, "Failed to log in to your SDU portal. Please check your credentials or try again later.");
        }
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