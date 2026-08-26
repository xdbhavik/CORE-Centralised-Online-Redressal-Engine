package com.SIH.mark1;

import com.SIH.mark1.dto.request.CreateComplaintRequest;
import com.SIH.mark1.dto.response.ComplaintResponse;
import com.SIH.mark1.model.*;
import com.SIH.mark1.repository.*;
import com.SIH.mark1.service.ComplaintService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ComplaintApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ComplaintService complaintService;

    @Test
    @WithMockUser(username = "9876543210", roles = {"CITIZEN"})
    public void testCreateComplaintApi() throws Exception {
        // 1. Prepare Request DTO
        CreateComplaintRequest request = new CreateComplaintRequest();
        request.setTitle("API Test Grievance");
        request.setDescription("This is a test of the Complaint Creation API.");
        request.setLatitude(new BigDecimal("28.7041"));
        request.setLongitude(new BigDecimal("77.1025"));
        request.setAddress("Delhi, India");
        request.setLanguage("HINDI");

        // 2. Prepare Mocked Response
        ComplaintResponse mockResponse = ComplaintResponse.builder()
                .complaintId(101L)
                .complaintNumber("GRV-2026-000101")
                .status("AI_ANALYZED")
                .message("Complaint registered successfully. Your complaint number is GRV-2026-000101")
                .build();

        // 3. Mock the Service behavior
        // Since we are mocking the service, we focus purely on the HTTP API layer (Controller).
        Mockito.when(complaintService.createComplaint(eq("9876543210"), any(CreateComplaintRequest.class)))
               .thenReturn(mockResponse);

        // 4. Perform POST request and assert response
        mockMvc.perform(post("/api/v1/complaints")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.complaintId").value(101))
                .andExpect(jsonPath("$.complaintNumber").value("GRV-2026-000101"))
                .andExpect(jsonPath("$.status").value("AI_ANALYZED"))
                .andExpect(jsonPath("$.message").value("Complaint registered successfully. Your complaint number is GRV-2026-000101"));
                
        System.out.println("====== API TEST PASSED: POST /api/v1/complaints ======");
    }
}

