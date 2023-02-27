package com.example.springbot.service;

import com.example.springbot.config.BotConfig;
import com.example.springbot.model.Ads;
import com.example.springbot.model.AdsRepository;
import com.example.springbot.model.User;
import com.example.springbot.model.UserRepository;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AdsRepository adsRepository;
    final BotConfig config;
    static final String HELP_TEXT = "Я создан тебе помогать....\nно пока что слишком слаб для этого...\nмой создатель скоро сделает меня умнее.";
    static final String YES_BUTTON = "YES_BUTTON";
    static final String NO_BUTTON = "NO_BUTTON";
    public TelegramBot(BotConfig config) {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "Начало работы, приветствие."));
        listOfCommands.add(new BotCommand("/mydata", "Получить данные."));
        listOfCommands.add(new BotCommand("/deletedata", "Удалить мои данные."));
        listOfCommands.add(new BotCommand("/help", "Помощь."));
        listOfCommands.add(new BotCommand("/settings", "Изменить твои настройки."));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        }catch (TelegramApiException e){
            log.error("Error setting bot's command list:" + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {

        if(update.hasMessage() && update.getMessage().hasText()){
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if(messageText.contains("/send") && config.getOwnerId() == chatId){
                String textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
                Iterable<User> users = userRepository.findAll();

                for(User user: users){
                    prepareAndSendMessage(user.getChatId(), textToSend);
                }
            }else {

                switch (messageText) {
                    case "/start":
                        registerUser(update.getMessage());
                        startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                        break;
                    case "/help":
                        prepareAndSendMessage(chatId, HELP_TEXT);
                        break;
                    case "/register":
                        register(chatId);
                        break;
                    default:
                        prepareAndSendMessage(chatId, "Извини, я пока не знаю что это за команда");
                }
            }
        } else if (update.hasCallbackQuery()) {
            String callBackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();


            if(callBackData.equals(YES_BUTTON)){
                String text = "Вы нажали кнопку Да";
                executeEditMessageText(text, chatId, messageId);
            } else if (callBackData.equals(NO_BUTTON)) {
                String text = "Вы нажали кнопку Нет";
                executeEditMessageText(text, chatId, messageId);
            }
        }


    }

    private void executeEditMessageText(String text, long chatId, long messageId){
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setText(text);
        message.setMessageId((int) messageId);

        try{
            execute(message);
        }catch (TelegramApiException e){
            log.error("Error occurred: " + e.getMessage());
        }
    }

    private void register(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Ты уверен что хочешь зарегистрироваться?");

        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        InlineKeyboardButton yesButton = new InlineKeyboardButton();

        yesButton.setText("Yes");
        yesButton.setCallbackData(YES_BUTTON);

        InlineKeyboardButton noButton = new InlineKeyboardButton();

        noButton.setText("No");
        noButton.setCallbackData(NO_BUTTON);

        rowInLine.add(yesButton);
        rowInLine.add(noButton);

        rowsInLine.add(rowInLine);

        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);

        executeMessage(message);
    }

    private void registerUser(Message msg) {
        if(userRepository.findById(msg.getChatId()).isEmpty()){
            Long chatId = msg.getChatId();
            Chat chat = msg.getChat();

            User user = new User();

            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

            userRepository.save(user);
            log.info("user saved: " + user);
        }
    }

    private void startCommandReceived(long chatId, String name) {
        String answer = EmojiParser.parseToUnicode("Привет, " + name + ", рад тебя видеть!" + " :blush:");
        log.info("Replied to user " + name);
        sendMessage(chatId, answer);
    }

    private void sendMessage(long chatId, String textToSent){
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSent);

        //вынеси в отдельный метод от сюда
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        row.add("Weather");
        row.add("Get random joke");

        keyboardRows.add(row);

        row = new KeyboardRow();

        row.add("Register");
        row.add("Chek my data");
        row.add("Delete my data");

        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);

        message.setReplyMarkup(keyboardMarkup);
        //до сюда))

        executeMessage(message);
    }

    private void prepareAndSendMessage(long chatId, String textToSent){
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSent);
        executeMessage(message);
    }
    private void executeMessage(SendMessage message){
        try{
            execute(message);
        }catch (TelegramApiException e){
            log.error("Error occurred: " + e.getMessage());
        }
    }

    @Scheduled(cron = "${cron.scheduler}")
    private void sendAds(){
        Iterable<Ads> ads = adsRepository.findAll();
        Iterable<User> users = userRepository.findAll();

        for(Ads ad: ads){
            for(User user: users){
                prepareAndSendMessage(user.getChatId(), ad.getAd());
            }
        }
    }
}
