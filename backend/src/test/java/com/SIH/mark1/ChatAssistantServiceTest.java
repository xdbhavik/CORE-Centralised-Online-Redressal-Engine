package com.SIH.mark1;

import com.SIH.mark1.ai.chatbot.ChatContextBuilder;
import com.SIH.mark1.ai.chatbot.ChatIntentService;
import com.SIH.mark1.ai.chatbot.ComplaintChatContext;
import com.SIH.mark1.ai.chatbot.ComplaintContextService;
import com.SIH.mark1.ai.chatbot.CurrentUserService;
import com.SIH.mark1.ai.client.OpenRouterClient;
import com.SIH.mark1.ai.dto.ChatRequest;
import com.SIH.mark1.ai.prompt.ChatPromptBuilder;
import com.SIH.mark1.ai.rag.RAGService;
import com.SIH.mark1.ai.service.ChatAssistantService;
import com.SIH.mark1.ai.service.TranslationService;
import com.SIH.mark1.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAssistantServiceTest {

    @Mock
    private TranslationService translationService;
    @Mock
    private RAGService ragService;
    @Mock
    private OpenRouterClient openRouterClient;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ComplaintContextService complaintContextService;

    private ChatAssistantService service;
    private User citizen;

    @BeforeEach
    void setUp() {
        service = new ChatAssistantService(
                translationService,
                ragService,
                new ChatPromptBuilder(),
                openRouterClient,
                new ChatIntentService(),
                currentUserService,
                complaintContextService,
                new ChatContextBuilder()
        );
        citizen = User.builder().name("Citizen").mobile("9876543210").build();
        citizen.setUserId(1L);
    }

    @Test
    void answersStatusFromComplaintContext() {
        String message = "Meri complaint ka kya hua?";
        ComplaintChatContext context = context("In Progress", "Electricity Department", "Rajesh Kumar");

        when(translationService.normalizeForAI(message, null)).thenReturn(message);
        when(currentUserService.currentUser()).thenReturn(Optional.of(citizen));
        when(complaintContextService.requestedComplaintBelongsElsewhere(citizen, message)).thenReturn(false);
        when(complaintContextService.resolveComplaint(citizen, message)).thenReturn(Optional.of(context));

        String answer = service.chat(new ChatRequest(message)).answer();

        assertThat(answer).contains("In Progress");
        assertThat(answer).contains("Electricity Department");
        assertThat(answer).doesNotContainIgnoringCase("officer se contact");
    }

    @Test
    void explainsWhenOfficerAssignmentIsUnavailable() {
        String message = "Meri complaint kis officer ko assigned hai?";
        ComplaintChatContext context = context("In Progress", "Water Department", null);

        when(translationService.normalizeForAI(message, null)).thenReturn(message);
        when(currentUserService.currentUser()).thenReturn(Optional.of(citizen));
        when(complaintContextService.requestedComplaintBelongsElsewhere(citizen, message)).thenReturn(false);
        when(complaintContextService.resolveComplaint(citizen, message)).thenReturn(Optional.of(context));

        String answer = service.chat(new ChatRequest(message)).answer();

        assertThat(answer).contains("abhi kisi specific officer ko assign nahi hui");
    }

    @Test
    void deniesComplaintDataForAnotherCitizen() {
        String message = "CMP-999 ka status kya hai?";

        when(translationService.normalizeForAI(message, null)).thenReturn(message);
        when(currentUserService.currentUser()).thenReturn(Optional.of(citizen));
        when(complaintContextService.requestedComplaintBelongsElsewhere(citizen, message)).thenReturn(true);

        String answer = service.chat(new ChatRequest(message)).answer();

        assertThat(answer).contains("aapke account me available nahi");
    }

    private ComplaintChatContext context(String status, String department, String officer) {
        return new ComplaintChatContext(
                "GRV-2026-000101",
                "Street light issue",
                status,
                department,
                "Street Light",
                officer,
                LocalDateTime.of(2026, 8, 5, 10, 0),
                LocalDateTime.of(2026, 8, 7, 10, 0),
                List.of(new ComplaintChatContext.TimelineItem(LocalDateTime.of(2026, 8, 5, 10, 0), "Complaint Registered"))
        );
    }
}
