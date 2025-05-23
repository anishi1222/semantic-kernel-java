// Copyright (c) Microsoft. All rights reserved.
package com.microsoft.semantickernel.samples.documentationexamples;

import java.util.Arrays;
import java.util.List;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.KeyCredential;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
import com.microsoft.semantickernel.orchestration.FunctionResult;
import com.microsoft.semantickernel.orchestration.ToolCallBehavior;
import com.microsoft.semantickernel.plugin.KernelPlugin;
import com.microsoft.semantickernel.plugin.KernelPluginFactory;
import com.microsoft.semantickernel.samples.plugins.ConversationSummaryPlugin;
import com.microsoft.semantickernel.semanticfunctions.KernelFunction;
import com.microsoft.semantickernel.semanticfunctions.KernelArguments;
import com.microsoft.semantickernel.services.chatcompletion.AuthorRole;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;

public class FunctionsWithinPrompts {

    // CLIENT_KEY is for an OpenAI client
    private static final String CLIENT_KEY = System.getenv("CLIENT_KEY");

    // AZURE_CLIENT_KEY and CLIENT_ENDPOINT are for an Azure client
    // CLIENT_ENDPOINT required if AZURE_CLIENT_KEY is set
    private static final String AZURE_CLIENT_KEY = System.getenv("AZURE_CLIENT_KEY");
    private static final String CLIENT_ENDPOINT = System.getenv("CLIENT_ENDPOINT");

    private static final String MODEL_ID = System.getenv()
        .getOrDefault("MODEL_ID", "gpt-3.5-turbo");

    public static void main(String[] args) {

        System.out.println("======== Functions within Prompts ========");

        OpenAIAsyncClient client;

        if (AZURE_CLIENT_KEY != null && CLIENT_ENDPOINT != null) {
            client = new OpenAIClientBuilder()
                .credential(new AzureKeyCredential(AZURE_CLIENT_KEY))
                .endpoint(CLIENT_ENDPOINT)
                .buildAsyncClient();
        } else if (CLIENT_KEY != null) {
            client = new OpenAIClientBuilder()
                .credential(new KeyCredential(CLIENT_KEY))
                .buildAsyncClient();
        } else {
            System.out.println("No client key found");
            return;
        }

        // <CreateChatCompletionService>
        ChatCompletionService chatCompletionService = OpenAIChatCompletion.builder()
            .withModelId(MODEL_ID)
            .withOpenAIAsyncClient(client)
            .build();
        // </CreateChatCompletionService>

        // <CreatePluginFromObject>
        KernelPlugin plugin = KernelPluginFactory.createFromObject(
            new ConversationSummaryPlugin(), "ConversationSummaryPlugin");
        // </CreatePluginFromObject>

        // <CreateKernel>
        Kernel kernel = Kernel.builder()
            .withAIService(ChatCompletionService.class, chatCompletionService)
            .withPlugin(plugin)
            .build();
        // </CreateKernel>

        List<String> choices = Arrays.asList("ContinueConversation", "EndConversation");

        // Create few-shot examples
        List<ChatHistory> fewShotExamples = Arrays.asList(

            new ChatHistory() {
                {
                    addMessage(AuthorRole.USER,
                        "Can you send a very quick approval to the marketing team?");
                    addMessage(AuthorRole.SYSTEM, "Intent:");
                    addMessage(AuthorRole.ASSISTANT, "ContinueConversation");
                }
            },
            new ChatHistory() {
                {
                    addMessage(AuthorRole.USER,
                        "Can you send the full update to the marketing team?");
                    addMessage(AuthorRole.SYSTEM, "Intent:");
                    addMessage(AuthorRole.ASSISTANT, "EndConversation");
                }
            });

        // Create handlebars template for intent
        // <IntentFunction>
        KernelFunction<String> getIntent = KernelFunction.<String>createFromPrompt(
            """
                <message role="system">Instructions: What is the intent of this request?
                Do not explain the reasoning, just reply back with the intent. If you are unsure, reply with {{choices.[0]}}.
                Choices: {{choices}}.</message>

                {{#each fewShotExamples}}
                    {{#each this}}
                        <message role="{{role}}">{{content}}</message>
                    {{/each}}
                {{/each}}

                {{ConversationSummaryPlugin-SummarizeConversation history}}

                <message role="user">{{request}}</message>
                <message role="system">Intent:</message>
                """)
            .withTemplateFormat("handlebars")
            .build();
        // </IntentFunction>

        // Create a Semantic Kernel template for chat
        // <CreateFunctionFromPrompt>
        KernelFunction<String> chat = KernelFunction.<String>createFromPrompt(
            """
                {{ConversationSummaryPlugin.SummarizeConversation $history}}
                User: {{$request}}
                Assistant:
                """)
            .build();
        // </CreateFunctionFromPrompt>

        // Create chat history
        ChatHistory history = new ChatHistory();

        // Start the chat loop
        // <ChatLoop>
        while (true) {
            // Get user input
            System.console().printf("User > ");
            String request = System.console().readLine();

            KernelArguments arguments = KernelArguments.builder()
                .withVariable("request", request)
                .withVariable("choices", choices)
                .withVariable("history", history)
                .withVariable("fewShotExamples", fewShotExamples)
                .build();

            // Invoke handlebars prompt
            FunctionResult<String> intent = kernel.invokeAsync(getIntent)
                .withArguments(arguments)
                .withToolCallBehavior(
                    ToolCallBehavior.allowOnlyKernelFunctions(true,
                        plugin.get("SummarizeConversation")))
                .block();

            // End the chat if the intent is "Stop"
            if ("EndConversation".equals(intent.getResult())) {
                break;
            }

            // Get chat response
            FunctionResult<String> chatResult = chat.invokeAsync(kernel)
                .withArguments(
                    KernelArguments.builder()
                        .withVariable("request", request)
                        .withVariable("history", history)
                        .build())
                .block();

            String message = chatResult.getResult();
            System.console().printf("Assistant > %s\n", message);

            // Append to history
            history.addUserMessage(request);
            history.addAssistantMessage(message);
        }
        // </ChatLoop>
    }

}
