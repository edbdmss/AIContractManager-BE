package com.aicontractmanager.chatbot;

import com.aicontractmanager.chatbot.domain.ChatMessage;
import com.aicontractmanager.chatbot.dto.ChatAiRequest;
import com.aicontractmanager.chatbot.dto.ChatMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChatPayloadTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void metadataSurvivesStorageAndIsFlattenedInMessage() throws Exception {
        UUID contractId = UUID.randomUUID();
        ChatMetadata metadata = new ChatMetadata(ChatMetadata.AnswerType.HYBRID, List.of(contractId),
                List.of(new ChatMetadata.LegalSource("rule", "법률", "제1조", "제목", "https://example.com")), false);
        ChatMetadata restored = mapper.readValue(mapper.writeValueAsString(metadata), ChatMetadata.class);
        var json = mapper.valueToTree(new ChatMessage(UUID.randomUUID(), "ASSISTANT", "답변", null, restored));
        assertEquals("HYBRID", json.path("answerType").asText());
        assertEquals(contractId.toString(), json.path("referencedContractIds").get(0).asText());
        assertEquals("rule", json.path("legalSources").get(0).path("ruleId").asText());
        assertFalse(json.has("metadata"));
        assertFalse(json.has("timings"));
    }

    @Test
    void oldMessagesHaveEmptyListsAndUnknownMetadata() {
        var json = mapper.valueToTree(new ChatMessage(UUID.randomUUID(), "USER", "질문", null, ChatMetadata.empty()));
        assertTrue(json.path("answerType").isNull());
        assertTrue(json.path("needsMoreInfo").isNull());
        assertEquals(0, json.path("legalSources").size());
    }

    @Test
    void contractAmountsAreNumbersAndPaymentDateKeepsItsName() {
        var context = new ChatAiRequest.ContractContext(UUID.randomUUID().toString(), "계약", "OTHER",
                null, "ACTIVE", null, "2026-12-01", null, new BigDecimal("17000"), null, null,
                "2026-11-01", new BigDecimal("20000"), true, "KRW", mapper.createArrayNode());
        var json = mapper.valueToTree(context);
        assertTrue(json.path("monthlyAmount").isNumber());
        assertTrue(json.path("penaltyAmount").isNumber());
        assertTrue(json.path("providerName").isNull());
        assertEquals("2026-12-01", json.path("nextPaymentDate").asText());
        assertFalse(json.has("autoRenewalDate"));
    }
}
