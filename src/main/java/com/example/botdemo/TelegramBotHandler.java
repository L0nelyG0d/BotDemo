package com.example.botdemo;

import com.example.botdemo.repository.UserRepository;

import com.example.botdemo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
public class TelegramBotHandler extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

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
                sendMessage(chatId, "Welcome to the bot! Please choose an option:\n"
                        + "/register <username> <password>\n"
                        + "/login <username> <password>");
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
            } else {
                sendMessage(chatId, "Unknown command. /register, or /login.");
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
        user.setPassword(password);
        userRepository.save(user);

        sendMessage(chatId, "Registration successful. You can now log in with /login.");
    }

    private void handleLogin(String chatId, String username, String password) {
        User user = userRepository.findByUsername(username);
        if (user != null && user.getPassword().equals(password)) {
            sendMessage(chatId, "Login successful. Welcome, " + username + "!");
        } else {
            sendMessage(chatId, "Invalid username or password.");
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