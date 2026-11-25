package com.budgetbee.web;

import com.budgetbee.service.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping
    public ChatService.Reply send(@RequestBody Map<String, String> body) {
        return chat.chat(body.get("message"));
    }
}
