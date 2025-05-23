// Copyright (c) Microsoft. All rights reserved.
package com.microsoft.semantickernel.aiservices.google.chatcompletion;

import com.azure.core.util.BinaryData;
import com.google.cloud.vertexai.api.FunctionDeclaration;
import com.google.cloud.vertexai.api.Schema;
import com.microsoft.semantickernel.implementation.chatcompletion.ChatPromptParseVisitor;
import com.microsoft.semantickernel.implementation.chatcompletion.ChatXMLPromptParser;
import com.microsoft.semantickernel.services.chatcompletion.AuthorRole;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;
import com.microsoft.semantickernel.services.chatcompletion.ChatMessageContent;
import com.microsoft.semantickernel.services.chatcompletion.message.ChatMessageTextContent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses an XML prompt for a Gemini chat.
 */
public class GeminiXMLPromptParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiXMLPromptParser.class);

    /**
     * Represents a parsed prompt for Gemini chat.
     */
    public static class GeminiParsedPrompt {

        private final ChatHistory chatHistory;
        private final List<FunctionDeclaration> functions;

        /**
         * Creates a new parsed prompt.
         * @param parsedChatHistory The chat history
         * @param parsedFunctions The functions declarations.
         */
        protected GeminiParsedPrompt(
            ChatHistory parsedChatHistory,
            @Nullable List<FunctionDeclaration> parsedFunctions) {
            this.chatHistory = parsedChatHistory;
            if (parsedFunctions == null) {
                parsedFunctions = new ArrayList<>();
            }
            this.functions = parsedFunctions;
        }

        /**
         * Gets the chat history.
         * @return A copy of the chat history.
         */
        public ChatHistory getChatHistory() {
            return new ChatHistory(chatHistory.getMessages());
        }

        /**
         * Gets the functions declarations.
         * @return A copy of the functions declarations.
         */
        public List<FunctionDeclaration> getFunctions() {
            return Collections.unmodifiableList(functions);
        }
    }

    private static AuthorRole getAuthorRole(String role) {
        switch (role) {
            case "user":
                return AuthorRole.USER;
            case "assistant":
                return AuthorRole.ASSISTANT;
            case "system":
                return AuthorRole.SYSTEM;
            case "tool":
                return AuthorRole.TOOL;
            default:
                LOGGER.error("Unknown role: " + role);
                return AuthorRole.USER;
        }
    }

    private static class GeminiChatPromptParseVisitor
        implements ChatPromptParseVisitor<GeminiParsedPrompt> {

        @Nullable
        private GeminiParsedPrompt parsedRaw = null;
        private final List<FunctionDeclaration> functionDefinitions = new ArrayList<>();
        private final ChatHistory chatHistory = new ChatHistory();

        @Override
        public ChatPromptParseVisitor<GeminiParsedPrompt> addMessage(
            String role,
            String content) {
            chatHistory.addMessage(
                ChatMessageTextContent.builder()
                    .withContent(content)
                    .withAuthorRole(getAuthorRole(role))
                    .build());
            return this;
        }

        @Override
        public ChatPromptParseVisitor<GeminiParsedPrompt> addFunction(
            String name,
            @Nullable String description,
            @Nullable BinaryData parameters) {

            // TODO: Build the parameters schema
            Schema.Builder parametersBuilder = Schema.newBuilder();

            FunctionDeclaration.Builder function = FunctionDeclaration.newBuilder()
                .setName(name)
                .setDescription(description)
                .setParameters(parametersBuilder.build());

            functionDefinitions.add(function.build());
            return this;
        }

        @Override
        public boolean areMessagesEmpty() {
            return chatHistory.getMessages().isEmpty();
        }

        @Override
        public ChatPromptParseVisitor<GeminiParsedPrompt> fromRawPrompt(
            String rawPrompt) {

            ChatMessageContent<?> message = ChatMessageTextContent.userMessage(rawPrompt);

            this.parsedRaw = new GeminiParsedPrompt(
                new ChatHistory(Collections.singletonList(message)), null);

            return this;
        }

        @Override
        public GeminiParsedPrompt get() {
            if (parsedRaw != null) {
                return parsedRaw;
            }

            return new GeminiParsedPrompt(chatHistory, functionDefinitions);
        }

        @Override
        public ChatPromptParseVisitor<GeminiParsedPrompt> reset() {
            return new GeminiChatPromptParseVisitor();
        }
    }

    /**
     * Create a GeminiParsedPrompt by parsing a raw prompt.
     * @param rawPrompt the raw prompt to parse.
     * @return The parsed prompt.
     */
    public static GeminiParsedPrompt parse(String rawPrompt) {
        ChatPromptParseVisitor<GeminiParsedPrompt> visitor = ChatXMLPromptParser.parse(
            rawPrompt,
            new GeminiChatPromptParseVisitor());

        return visitor.get();
    }
}
