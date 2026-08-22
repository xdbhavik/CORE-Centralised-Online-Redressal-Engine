package com.SIH.mark1.ai.service;

import com.SIH.mark1.ai.client.OpenRouterClient;
import com.SIH.mark1.ai.config.AIProperties;
import com.SIH.mark1.ai.dto.AIResponse;
import com.SIH.mark1.ai.duplicate.ResourceExtractor;
import com.SIH.mark1.ai.parser.ResponseParser;
import com.SIH.mark1.ai.prompt.ComplaintPromptBuilder;
import com.SIH.mark1.ai.rag.RAGService;
import com.SIH.mark1.ai.validation.CategoryValidator;
import com.SIH.mark1.ai.validation.DepartmentValidator;
import com.SIH.mark1.ai.validation.PriorityValidator;
import com.SIH.mark1.dto.request.CreateComplaintRequest;
import com.SIH.mark1.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ComplaintAnalysisService {

    private final TranslationService translationService;
    private final RAGService ragService;
    private final ComplaintPromptBuilder promptBuilder;
    private final OpenRouterClient openRouterClient;
    private final ResponseParser responseParser;
    private final DepartmentValidator departmentValidator;
    private final CategoryValidator categoryValidator;
    private final PriorityValidator priorityValidator;
    private final CategoryRepository categoryRepository;
    private final AIProperties properties;
    private final ResourceExtractor resourceExtractor;

    public ComplaintAnalysisService(TranslationService translationService,
                                    RAGService ragService,
                                    ComplaintPromptBuilder promptBuilder,
                                    OpenRouterClient openRouterClient,
                                    ResponseParser responseParser,
                                    DepartmentValidator departmentValidator,
                                    CategoryValidator categoryValidator,
                                    PriorityValidator priorityValidator,
                                    CategoryRepository categoryRepository,
                                    AIProperties properties,
                                    ResourceExtractor resourceExtractor) {
        this.translationService = translationService;
        this.ragService = ragService;
        this.promptBuilder = promptBuilder;
        this.openRouterClient = openRouterClient;
        this.responseParser = responseParser;
        this.departmentValidator = departmentValidator;
        this.categoryValidator = categoryValidator;
        this.priorityValidator = priorityValidator;
        this.categoryRepository = categoryRepository;
        this.properties = properties;
        this.resourceExtractor = resourceExtractor;
    }

    public AIResponse analyze(CreateComplaintRequest request) {
        String rawComplaint = ((request.getTitle() == null ? "" : request.getTitle()) + " "
                + (request.getDescription() == null ? "" : request.getDescription())).trim();
        String normalizedComplaint = translationService.normalizeForAI(rawComplaint, request.getLanguage());
        RAGService.RagContext ragContext = ragService.retrieveContext(normalizedComplaint);
        String prompt = promptBuilder.build(normalizedComplaint, ragContext.context());

        AIResponse modelResponse = openRouterClient.complete(prompt)
                .map(responseParser::parseAnalysis)
                .orElseGet(() -> localAnalysis(normalizedComplaint, ragContext));

        // Safety net: whenever Nemotron is unavailable, unconfident or returns UNKNOWN,
        // always try the local keyword classifier — even when no RAG chunks were retrieved.
        if (modelResponse.confidence() < 70 || "UNKNOWN".equalsIgnoreCase(modelResponse.department())) {
            AIResponse localResponse = localAnalysis(normalizedComplaint, ragContext);
            if (!"UNKNOWN".equalsIgnoreCase(localResponse.department())
                    || "UNKNOWN".equalsIgnoreCase(modelResponse.department())) {
                modelResponse = localResponse;
            }
        }

        return validate(modelResponse);
    }

    private AIResponse localAnalysis(String complaint, RAGService.RagContext context) {
        String lower = complaint.toLowerCase(Locale.ROOT);
        Classification classification = classifyComplaintText(lower);
        String priority = detectPriority(lower, classification);
        String scope = detectScope(lower, classification);
        ResourceExtractor.ExtractedResource resource = resourceExtractor.extract(
                complaint, classification.department(), classification.category());
        int confidence = classification.department().equals("UNKNOWN")
                ? 45
                : Math.min(96, classification.confidence() + Math.min(context.chunks().size(), 3) * 2);
        return new AIResponse(
                classification.title(),
                translationService.summarizeInEnglish(complaint),
                classification.department(),
                classification.category(),
                priority,
                confidence,
                scope,
                resource.type(),
                resource.identifier());
    }

    private Classification classifyComplaintText(String text) {
        if (containsAny(text, "dirty water", "contaminated water", "ganda pani", "गंदा पानी", "ગંદુ પાણી", "ગંદું પાણી")) {
            return new Classification("Water Quality", "Water Department", "Water Quality", 84);
        }
        if (containsAny(text, "water", "pipeline", "pipe", "leakage", "leak", "pani", "paani", "पानी", "jal", "પાણી", "પાઇપ", "નળ")
                || containsAny(text, "no water supply", "nahi aa raha", "नहीं आ रहा", "પાણી નથી આવતું")) {
            return new Classification("Water Supply", "Water Department", "Water Supply", 82);
        }
        if (containsAny(text, "light nahi", "light not coming", "batti nahi", "no light", "power cut", "power outage", "electricity cut", "bijli nahi", "બજલી નથી", "વીજળી નથી", "લાઇટ નહી", "જ ડાય", "જાણ્યું નથી", "light not working", "batti band", "લાઇટ બંધ", "બજલી કતા", "लाइट नहीं", "बिजली नहीं", "बिजली कटा", "बिजली नहीं आ रही", "लाइट नहीं आ रही", "बिजली कटी", "बिजली नहीं आ रहा", "लाइट बंद")) {
            // DB category for a power outage is "Power Supply" (see categories table / Electricity Department.md)
            return new Classification("Power Outage", "Electricity Department", "Power Supply", 85);
        }
        if (containsAny(text, "street light", "streetlight", "light pole", "lamp post", "શેરી લાઈટ", "લાઈટ નથી", "સ્ટ્રીટ લાઈટ")) {
            return new Classification("Street Light", "Electricity Department", "Street Light", 82);
        }
        if (containsAny(text, "electric", "electricity", "power", "transformer", "wire", "bijli", "बिजली", "current", "વીજળી", "કરંટ", "ટ્રાન્સફોર્મર")) {
            return new Classification("Power Supply", "Electricity Department", "Power Supply", 82);
        }
        if (containsAny(text, "road", "route", "pothole", "footpath", "broken road", "damaged road",
                "bad road", "road damaged", "road repair", "rasta", "raasta", "rastha", "sadak", "सड़क",
                "gaddha", "गड्ढा", "kharab road", "rasta kharab", "raasta kharab", "sadak kharab",
                "રોડ", "ખાડો", "ખાડા", "રસ્તો", "રસ્તા", "સડક", "રોડ ખરાબ", "રસ્તો ખરાબ", "રસ્તા ખરાબ")) {
            return new Classification("Road Repair", "Road Department", "Road Repair", 82);
        }
        if (containsAny(text, "bridge", "pul", "પુલ", "સેતુ", "bridge tut", "bridge broken", "bridge damaged", "bridge kharab", "પુલ તૂટી", "પુલ ખરાબ")) {
            // DB has no separate "Bridge Repair" category — bridge damage falls under "Road Repair"
            return new Classification("Bridge Repair", "Road Department", "Road Repair", 84);
        }
        if (containsAny(text, "drainage", "drain", "sewer", "nali", "naali", "नाली", "सीवर", "નાળું", "ગટર", "ડ્રેનેજ")) {
            return new Classification("Drainage", "Sanitation Department", "Drainage", 84);
        }
        if (containsAny(text, "garbage", "waste", "trash", "kachra", "कचरा", "safai", "सफाई", "કચરો", "કચરા", "સફાઈ")) {
            return new Classification("Garbage Collection", "Sanitation Department", "Garbage Collection", 82);
        }
        return new Classification("Complaint Analysis", "UNKNOWN", "UNKNOWN", 45);
    }

    private String detectPriority(String text, Classification classification) {
        if (containsAny(text, "burst", "broken pipeline", "pipeline is broken", "accident", "sewer overflow", "overflow", "exposed wire", "sparking", "fire",
                "3 days", "three days", "3 din", "teen din", "10 days", "ten days", "das din", "10 din", "दस दिन", "દસ દિન",
                "2 days", "two days", "do din", "दो दिन", "7 days", "seven days", "ek hafta", "one week", "एक हफ्ता",
                "urgent", "emergency", "danger", "जल्दी", "तुरंत",
                "અકસ્માત", "આગ", "તાત્કાલિક", "ભય", "ફાટી ગયું", "ઓવરફ્લો")) {
            return "HIGH";
        }
        if ("UNKNOWN".equals(classification.department())) {
            return "MEDIUM";
        }
        return "MEDIUM";
    }

    private String detectScope(String text, Classification classification) {
        if (containsAny(text, "meter", "connection id", "property id", "tax account", "mere ghar", "my house", "my home", "મારું ઘર", "મારા ઘર")) {
            return "INDIVIDUAL";
        }
        if (containsAny(text, "main road", "public", "pipeline", "traffic signal", "street light", "streetlight", "footpath", "pothole",
                "મુખ્ય રોડ", "જાહેર", "ટ્રાફિક સિગ્નલ", "ફૂટપાથ")) {
            return "PUBLIC_INFRASTRUCTURE";
        }
        if (containsAny(text, "area", "colony", "society", "locality", "ward", "mohalla", "hamari society", "our society",
                "વિસ્તાર", "સોસાયટી", "વોર્ડ", "મોહલ્લો")) {
            return "LOCAL_AREA";
        }
        if ("Road Department".equals(classification.department())) {
            return "PUBLIC_INFRASTRUCTURE";
        }
        return "LOCAL_AREA";
    }

    private boolean containsAny(String text, String... keywords) {
        return List.of(keywords).stream().anyMatch(text::contains);
    }

    private record Classification(String title, String department, String category, int confidence) {
    }

    private AIResponse validate(AIResponse response) {
        String category = categoryValidator.validate(response.category());
        String department = departmentValidator.validate(response.department(), response.confidence());

        if ("UNKNOWN".equals(department) && !"UNKNOWN".equals(category)) {
            department = categoryRepository.findByCategoryName(category)
                    .map(foundCategory -> foundCategory.getDepartment().getDepartmentName())
                    .orElse("UNKNOWN");
        }

        // Summary stored in the backend must ALWAYS be English:
        // summarize → if still non-English (e.g. model echoed Hindi), translate via Sarvam AI.
        String summary = translationService.summarizeInEnglish(response.summary());
        summary = translationService.ensureEnglish(summary);
        if (summary == null || summary.isBlank()) {
            summary = translationService.ensureEnglish(response.title());
        }

        return new AIResponse(
                response.title(),
                summary,
                department,
                category,
                priorityValidator.validate(response.priority()),
                Math.max(0, Math.min(100, response.confidence())),
                response.scope(),
                response.resourceType(),
                response.resourceIdentifier()
        );
    }

    private String summarize(String complaint) {
        String cleaned = complaint.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= 140) {
            return cleaned;
        }
        return cleaned.substring(0, 137).trim() + "...";
    }

    public String modelName() {
        return properties.modelName();
    }
}

