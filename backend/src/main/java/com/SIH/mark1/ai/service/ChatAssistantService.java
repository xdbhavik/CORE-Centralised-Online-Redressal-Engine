package com.SIH.mark1.ai.service;

import com.SIH.mark1.ai.chatbot.ChatContextBuilder;
import com.SIH.mark1.ai.chatbot.ChatIntent;
import com.SIH.mark1.ai.chatbot.ChatIntentService;
import com.SIH.mark1.ai.chatbot.ComplaintChatContext;
import com.SIH.mark1.ai.chatbot.ComplaintContextService;
import com.SIH.mark1.ai.chatbot.CurrentUserService;
import com.SIH.mark1.ai.client.OpenRouterClient;
import com.SIH.mark1.ai.dto.ChatRequest;
import com.SIH.mark1.ai.dto.ChatResponse;
import com.SIH.mark1.ai.prompt.ChatPromptBuilder;
import com.SIH.mark1.ai.rag.RAGService;
import com.SIH.mark1.model.User;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class ChatAssistantService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final TranslationService translationService;
    private final RAGService ragService;
    private final ChatPromptBuilder chatPromptBuilder;
    private final OpenRouterClient openRouterClient;
    private final ChatIntentService chatIntentService;
    private final CurrentUserService currentUserService;
    private final ComplaintContextService complaintContextService;
    private final ChatContextBuilder chatContextBuilder;

    public ChatAssistantService(TranslationService translationService,
                                RAGService ragService,
                                ChatPromptBuilder chatPromptBuilder,
                                OpenRouterClient openRouterClient,
                                ChatIntentService chatIntentService,
                                CurrentUserService currentUserService,
                                ComplaintContextService complaintContextService,
                                ChatContextBuilder chatContextBuilder) {
        this.translationService = translationService;
        this.ragService = ragService;
        this.chatPromptBuilder = chatPromptBuilder;
        this.openRouterClient = openRouterClient;
        this.chatIntentService = chatIntentService;
        this.currentUserService = currentUserService;
        this.complaintContextService = complaintContextService;
        this.chatContextBuilder = chatContextBuilder;
    }

    public ChatResponse chat(ChatRequest request) {
        String normalized = translationService.normalizeForAI(request.message(), null);
        ChatIntent intent = chatIntentService.detect(normalized);

        if (intent == ChatIntent.GENERAL_KNOWLEDGE) {
            return generalKnowledge(normalized);
        }
        if (intent == ChatIntent.CREATE_COMPLAINT) {
            return new ChatResponse("Main aapki complaint register karne me help kar sakta hoon. Kripya location aur short description batayein.");
        }

        Optional<User> currentUser = currentUserService.currentUser();
        if (currentUser.isEmpty()) {
            return new ChatResponse("Complaint ki jankari dekhne ke liye login required hai. JWT token ke saath request bhejein.");
        }

        User citizen = currentUser.get();
        if (complaintContextService.requestedComplaintBelongsElsewhere(citizen, normalized)) {
            return new ChatResponse("Yeh complaint aapke account me available nahi hai, isliye main iski jankari nahi dikha sakta.");
        }

        if (intent == ChatIntent.COMPLAINT_LIST) {
            return new ChatResponse(formatComplaintList(complaintContextService.listComplaints(citizen)));
        }

        Optional<ComplaintChatContext> complaint = complaintContextService.resolveComplaint(citizen, normalized);
        if (complaint.isEmpty()) {
            return new ChatResponse("System me aapki matching complaint information available nahi hai.");
        }

        ComplaintChatContext context = complaint.get();
        return switch (intent) {
            case COMPLAINT_STATUS -> new ChatResponse(formatStatus(context));
            case ASSIGNED_OFFICER -> new ChatResponse(formatOfficer(context));
            case DEPARTMENT -> new ChatResponse(formatDepartment(context));
            case COMPLAINT_HISTORY -> new ChatResponse(formatHistory(context));
            case HYBRID -> hybrid(normalized, context);
            default -> generalKnowledge(normalized);
        };
    }

    private ChatResponse generalKnowledge(String message) {
        RAGService.RagContext context = ragService.retrieveContext(message);
        String prompt = chatPromptBuilder.build(message, null, context.context());
        String fallback = context.context().isBlank()
                ? "Knowledge base me is sawal ke liye relevant information available nahi hai."
                : context.chunks().getFirst().content();
        return new ChatResponse(openRouterClient.complete(prompt).orElse(fallback));
    }

    private ChatResponse hybrid(String message, ComplaintChatContext complaint) {
        RAGService.RagContext ragContext = ragService.retrieveContext(message);
        String complaintContext = chatContextBuilder.complaintContext(complaint);
        String prompt = chatPromptBuilder.build(message, complaintContext, ragContext.context());
        String fallback = formatStatus(complaint);
        if (!ragContext.context().isBlank() && !ragContext.chunks().isEmpty()) {
            fallback = fallback + "\n\nGeneral SOP information: " + ragContext.chunks().getFirst().content();
        }
        return new ChatResponse(openRouterClient.complete(prompt).orElse(fallback));
    }

    private String formatStatus(ComplaintChatContext context) {
        String department = value(context.department());
        return "Aapki complaint " + value(context.complaintNumber()) + " abhi " + value(context.status())
                + " hai aur " + department + " ke paas hai.";
    }

    private String formatOfficer(ComplaintChatContext context) {
        if (context.hasOfficer()) {
            return "Aapki complaint " + value(context.complaintNumber()) + " " + context.officer() + " ko assigned hai.";
        }
        return "Aapki complaint " + value(context.complaintNumber()) + " abhi kisi specific officer ko assign nahi hui hai.";
    }

    private String formatDepartment(ComplaintChatContext context) {
        if (context.department() == null || context.department().isBlank()) {
            return "System me is complaint ka department available nahi hai.";
        }
        return "Aapki complaint " + value(context.complaintNumber()) + " " + context.department() + " department ke paas hai.";
    }

    private String formatHistory(ComplaintChatContext context) {
        if (context.timeline() == null || context.timeline().isEmpty()) {
            return "System me is complaint ki history available nahi hai. Current status: " + value(context.status()) + ".";
        }
        StringBuilder builder = new StringBuilder("Aapki complaint ki latest history:\n");
        for (ComplaintChatContext.TimelineItem item : context.timeline()) {
            builder.append(DATE.format(item.time()))
                    .append(" - ")
                    .append(item.event())
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private String formatComplaintList(List<ComplaintChatContext> complaints) {
        if (complaints == null || complaints.isEmpty()) {
            return "System me aapki koi active complaint available nahi hai.";
        }
        StringBuilder builder = new StringBuilder("Aapki complaints:\n");
        complaints.stream().limit(10).forEach(complaint -> builder
                .append(complaint.complaintNumber())
                .append(" - ")
                .append(value(complaint.title()))
                .append(" - ")
                .append(value(complaint.status()))
                .append("\n"));
        if (complaints.size() > 10) {
            builder.append("Aur ").append(complaints.size() - 10).append(" complaints available hain.");
        }
        return builder.toString().trim();
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "unavailable" : value;
    }
}
