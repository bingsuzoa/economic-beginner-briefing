package com.economicbriefing.domain.article;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NewsCategoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeToSnakeCase() throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(NewsCategory.INTEREST_RATE);
        assertEquals("\"interest_rate\"", json);
    }

    @Test
    void shouldSerializeAllValuesToSnakeCase() throws JsonProcessingException {
        assertEquals("\"deposit_saving\"", objectMapper.writeValueAsString(NewsCategory.DEPOSIT_SAVING));
        assertEquals("\"jeonse_monthly_rent\"", objectMapper.writeValueAsString(NewsCategory.JEONSE_MONTHLY_RENT));
        assertEquals("\"cost_of_living\"", objectMapper.writeValueAsString(NewsCategory.COST_OF_LIVING));
        assertEquals("\"government_support\"", objectMapper.writeValueAsString(NewsCategory.GOVERNMENT_SUPPORT));
        assertEquals("\"employment_income\"", objectMapper.writeValueAsString(NewsCategory.EMPLOYMENT_INCOME));
        assertEquals("\"household_debt\"", objectMapper.writeValueAsString(NewsCategory.HOUSEHOLD_DEBT));
        assertEquals("\"other\"", objectMapper.writeValueAsString(NewsCategory.OTHER));
    }

    @Test
    void shouldDeserializeFromSnakeCase() throws JsonProcessingException {
        NewsCategory category = objectMapper.readValue("\"interest_rate\"", NewsCategory.class);
        assertEquals(NewsCategory.INTEREST_RATE, category);
    }

    @Test
    void shouldDeserializeAllValues() throws JsonProcessingException {
        assertEquals(NewsCategory.HOUSING, objectMapper.readValue("\"housing\"", NewsCategory.class));
        assertEquals(NewsCategory.LOAN, objectMapper.readValue("\"loan\"", NewsCategory.class));
        assertEquals(NewsCategory.TAX, objectMapper.readValue("\"tax\"", NewsCategory.class));
        assertEquals(NewsCategory.PENSION, objectMapper.readValue("\"pension\"", NewsCategory.class));
        assertEquals(NewsCategory.INSURANCE, objectMapper.readValue("\"insurance\"", NewsCategory.class));
    }

    @Test
    void shouldRoundTripAllCategories() throws JsonProcessingException {
        for (NewsCategory category : NewsCategory.values()) {
            String json = objectMapper.writeValueAsString(category);
            NewsCategory deserialized = objectMapper.readValue(json, NewsCategory.class);
            assertEquals(category, deserialized);
        }
    }

    @Test
    void shouldHave16Categories() {
        assertEquals(16, NewsCategory.values().length);
    }

    @Test
    void fromValueShouldBeCaseInsensitive() {
        assertEquals(NewsCategory.INTEREST_RATE, NewsCategory.fromValue("INTEREST_RATE"));
        assertEquals(NewsCategory.INTEREST_RATE, NewsCategory.fromValue("interest_rate"));
    }

    @Test
    void fromValueShouldThrowForInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> NewsCategory.fromValue("invalid"));
    }
}
