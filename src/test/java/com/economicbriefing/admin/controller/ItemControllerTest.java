package com.economicbriefing.admin.controller;

import java.util.Optional;

import com.economicbriefing.admin.entity.PipelineItemEntity;
import com.economicbriefing.admin.repository.PipelineItemRepository;
import com.economicbriefing.admin.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ItemController.class)
@Import({SecurityConfig.class, AdminTestConfig.class})
@ActiveProfiles("test")
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PipelineItemRepository itemRepo;

    private static final String AUTH = "Bearer test-admin-token";

    @Test
    void shouldReturnItemById() throws Exception {
        PipelineItemEntity item = new PipelineItemEntity();
        item.setId(1L);
        item.setRunId("run-1");
        item.setOriginalTitle("금리 인상 뉴스");
        item.setSource("한국경제");

        when(itemRepo.findById(1L)).thenReturn(Optional.of(item));

        mockMvc.perform(get("/api/admin/items/1")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalTitle").value("금리 인상 뉴스"));
    }

    @Test
    void shouldReturn404ForMissingItem() throws Exception {
        when(itemRepo.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/items/999")
                        .header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
    }

    @Test
    void shouldRejectWithoutAuthToken() throws Exception {
        mockMvc.perform(get("/api/admin/items/1"))
                .andExpect(status().isUnauthorized());
    }
}
