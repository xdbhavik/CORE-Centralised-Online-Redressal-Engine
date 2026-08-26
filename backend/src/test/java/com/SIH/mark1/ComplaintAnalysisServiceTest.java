package com.SIH.mark1;

import com.SIH.mark1.ai.client.OpenRouterClient;
import com.SIH.mark1.ai.client.SarvamTranslationClient;
import com.SIH.mark1.ai.config.AIProperties;
import com.SIH.mark1.ai.dto.AIResponse;
import com.SIH.mark1.ai.duplicate.ResourceExtractor;
import com.SIH.mark1.ai.parser.ResponseParser;
import com.SIH.mark1.ai.prompt.ComplaintPromptBuilder;
import com.SIH.mark1.ai.rag.RAGService;
import com.SIH.mark1.ai.service.ComplaintAnalysisService;
import com.SIH.mark1.ai.service.TranslationService;
import com.SIH.mark1.ai.util.LanguageUtil;
import com.SIH.mark1.ai.validation.CategoryValidator;
import com.SIH.mark1.ai.validation.DepartmentValidator;
import com.SIH.mark1.ai.validation.PriorityValidator;
import com.SIH.mark1.dto.request.CreateComplaintRequest;
import com.SIH.mark1.repository.CategoryRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComplaintAnalysisServiceTest {

    private TranslationService newTranslationService(OpenRouterClient openRouterClient) {
        SarvamTranslationClient sarvamTranslationClient = mock(SarvamTranslationClient.class);
        when(sarvamTranslationClient.translateToEnglish(anyString(), anyString())).thenReturn(Optional.empty());
        return new TranslationService(new LanguageUtil(), openRouterClient, sarvamTranslationClient);
    }

    @Test
    void localFallbackClassifiesHinglishWaterComplaint() {
        OpenRouterClient openRouterClient = mock(OpenRouterClient.class);
        TranslationService translationService = newTranslationService(openRouterClient);
        RAGService ragService = mock(RAGService.class);
        ResponseParser responseParser = mock(ResponseParser.class);
        DepartmentValidator departmentValidator = mock(DepartmentValidator.class);
        CategoryValidator categoryValidator = mock(CategoryValidator.class);
        PriorityValidator priorityValidator = mock(PriorityValidator.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);

        when(ragService.retrieveContext(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new RAGService.RagContext(List.of(), ""));
        when(openRouterClient.complete(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(categoryValidator.validate("Water Supply")).thenReturn("Water Supply");
        when(departmentValidator.validate(eq("Water Department"), anyInt())).thenReturn("Water Department");
        when(priorityValidator.validate("HIGH")).thenReturn("HIGH");

        ComplaintAnalysisService service = new ComplaintAnalysisService(
                translationService,
                ragService,
                new ComplaintPromptBuilder(),
                openRouterClient,
                responseParser,
                departmentValidator,
                categoryValidator,
                priorityValidator,
                categoryRepository,
                new AIProperties(true, new AIProperties.ChatModelConfig("local", "", "", "test-model", 1000), null, null, null),
                new ResourceExtractor());

        CreateComplaintRequest request = new CreateComplaintRequest();
        request.setDescription("Pani nahi aa raha 3 din se");
        request.setLanguage("hi");

        AIResponse response = service.analyze(request);

        Assertions.assertEquals("Water Department", response.department());
        Assertions.assertEquals("Water Supply", response.category());
        Assertions.assertEquals("HIGH", response.priority());
        Assertions.assertTrue(response.confidence() >= 70);
    }

    @Test
    void localFallbackClassifiesHinglishRoadComplaintWithEnglishSummary() {
        OpenRouterClient openRouterClient = mock(OpenRouterClient.class);
        TranslationService translationService = newTranslationService(openRouterClient);
        RAGService ragService = mock(RAGService.class);
        ResponseParser responseParser = mock(ResponseParser.class);
        DepartmentValidator departmentValidator = mock(DepartmentValidator.class);
        CategoryValidator categoryValidator = mock(CategoryValidator.class);
        PriorityValidator priorityValidator = mock(PriorityValidator.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);

        when(ragService.retrieveContext(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new RAGService.RagContext(List.of(), ""));
        when(openRouterClient.complete(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(categoryValidator.validate("Road Repair")).thenReturn("Road Repair");
        when(departmentValidator.validate(eq("Road Department"), anyInt())).thenReturn("Road Department");
        when(priorityValidator.validate("MEDIUM")).thenReturn("MEDIUM");

        ComplaintAnalysisService service = new ComplaintAnalysisService(
                translationService,
                ragService,
                new ComplaintPromptBuilder(),
                openRouterClient,
                responseParser,
                departmentValidator,
                categoryValidator,
                priorityValidator,
                categoryRepository,
                new AIProperties(true, new AIProperties.ChatModelConfig("local", "", "", "test-model", 1000), null, null, null),
                new ResourceExtractor());

        CreateComplaintRequest request = new CreateComplaintRequest();
        request.setDescription("string mere area main rasta bahot kharab hain");
        request.setLanguage("hi");

        AIResponse response = service.analyze(request);

        Assertions.assertEquals("Road Department", response.department());
        Assertions.assertEquals("Road Repair", response.category());
        Assertions.assertEquals("Residents are facing a road maintenance issue.", response.summary());
        Assertions.assertTrue(response.confidence() >= 70);
    }

    @Test
    void localFallbackClassifiesPowerOutageComplaint() {
        OpenRouterClient openRouterClient = mock(OpenRouterClient.class);
        TranslationService translationService = newTranslationService(openRouterClient);
        RAGService ragService = mock(RAGService.class);
        ResponseParser responseParser = mock(ResponseParser.class);
        DepartmentValidator departmentValidator = mock(DepartmentValidator.class);
        CategoryValidator categoryValidator = mock(CategoryValidator.class);
        PriorityValidator priorityValidator = mock(PriorityValidator.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);

        when(ragService.retrieveContext(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new RAGService.RagContext(List.of(), ""));
        when(openRouterClient.complete(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(categoryValidator.validate("Power Supply")).thenReturn("Power Supply");
        when(departmentValidator.validate(eq("Electricity Department"), anyInt())).thenReturn("Electricity Department");
        when(priorityValidator.validate("HIGH")).thenReturn("HIGH");

        ComplaintAnalysisService service = new ComplaintAnalysisService(
                translationService,
                ragService,
                new ComplaintPromptBuilder(),
                openRouterClient,
                responseParser,
                departmentValidator,
                categoryValidator,
                priorityValidator,
                categoryRepository,
                new AIProperties(true, new AIProperties.ChatModelConfig("local", "", "", "test-model", 1000), null, null, null),
                new ResourceExtractor());

        CreateComplaintRequest request = new CreateComplaintRequest();
        request.setDescription("ye ghar ke andar das din se light nahi aa rahi hai");
        request.setLanguage("hi");

        AIResponse response = service.analyze(request);

        Assertions.assertEquals("Electricity Department", response.department());
        Assertions.assertEquals("Power Supply", response.category());
        Assertions.assertEquals("HIGH", response.priority());
        Assertions.assertTrue(response.confidence() >= 70);
    }

    @Test
    void localFallbackClassifiesDevanagariLightComplaintWithEnglishSummary() {
        OpenRouterClient openRouterClient = mock(OpenRouterClient.class);
        TranslationService translationService = newTranslationService(openRouterClient);
        RAGService ragService = mock(RAGService.class);
        ResponseParser responseParser = mock(ResponseParser.class);
        DepartmentValidator departmentValidator = mock(DepartmentValidator.class);
        CategoryValidator categoryValidator = mock(CategoryValidator.class);
        PriorityValidator priorityValidator = mock(PriorityValidator.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);

        when(ragService.retrieveContext(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new RAGService.RagContext(List.of(), ""));
        when(openRouterClient.complete(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(categoryValidator.validate("Power Supply")).thenReturn("Power Supply");
        when(departmentValidator.validate(eq("Electricity Department"), anyInt())).thenReturn("Electricity Department");
        when(priorityValidator.validate("HIGH")).thenReturn("HIGH");

        ComplaintAnalysisService service = new ComplaintAnalysisService(
                translationService,
                ragService,
                new ComplaintPromptBuilder(),
                openRouterClient,
                responseParser,
                departmentValidator,
                categoryValidator,
                priorityValidator,
                categoryRepository,
                new AIProperties(true, new AIProperties.ChatModelConfig("local", "", "", "test-model", 1000), null, null, null),
                new ResourceExtractor());

        CreateComplaintRequest request = new CreateComplaintRequest();
        request.setDescription("ये घर के अंदर दस दिन से लाइट नहीं आ रही है, इसे जल्दी फिक्स करो।");
        request.setLanguage("hi");

        AIResponse response = service.analyze(request);

        Assertions.assertEquals("Electricity Department", response.department());
        Assertions.assertEquals("Power Supply", response.category());
        Assertions.assertEquals("HIGH", response.priority());
        Assertions.assertTrue(response.confidence() >= 70);
        // Summary must be English even for a Devanagari complaint
        Assertions.assertFalse(response.summary().isBlank());
        Assertions.assertTrue(response.summary().chars().noneMatch(ch -> ch >= 0x0900 && ch <= 0x097F),
                "Summary must not contain Devanagari characters: " + response.summary());
    }
}
