package com.aicontractmanager.chatbot.service;

import com.aicontractmanager.chatbot.domain.ChatMessage;
import com.aicontractmanager.chatbot.domain.ChatSession;
import com.aicontractmanager.chatbot.dto.ChatAiRequest;
import com.aicontractmanager.chatbot.dto.ChatMessageRequest;
import com.aicontractmanager.chatbot.dto.ChatReply;
import com.aicontractmanager.chatbot.repository.ChatRepository;
import com.aicontractmanager.contract.domain.Contract;
import com.aicontractmanager.contract.service.ContractService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.aicontractmanager.chatbot.dto.ChatMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ChatbotService {
    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);
    private final ObjectMapper mapper;
    private final ChatRepository repository;
    private final ContractService contracts;
    private final RestClient aiClient;
    private final boolean aiEnabled;
    private final String chatPath;

    public ChatbotService(ChatRepository repository, ContractService contracts, RestClient.Builder builder, ObjectMapper mapper,
                          @Value("${app.ai.enabled:true}") boolean aiEnabled,
                          @Value("${app.ai.base-url:http://localhost:8000}") String aiBaseUrl,
                          @Value("${app.ai.chat-path:/chat}") String chatPath) {
        this.repository = repository;
        this.mapper = mapper;
        this.contracts = contracts;
        this.aiClient = builder.baseUrl(aiBaseUrl).build();
        this.aiEnabled = aiEnabled;
        this.chatPath = chatPath;
    }

    public List<ChatSession> listSessions(UUID userId) {
        return repository.findSessions(userId);
    }

    public ChatSession createSession(UUID userId, UUID contractId) {
        if (contractId != null) contracts.get(userId, contractId);
        UUID id = UUID.randomUUID();
        repository.createSession(id, userId, contractId);
        return repository.findSession(id, userId).orElseThrow();
    }

    public List<ChatMessage> listMessages(UUID userId, UUID sessionId) {
        requireSession(userId, sessionId);
        return repository.findMessages(sessionId);
    }

    public ChatReply send(UUID userId, UUID sessionId, ChatMessageRequest request) {
        ChatSession session = requireSession(userId, sessionId);
        if (!aiEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 챗봇이 비활성화되어 있습니다.");
        }

        UUID userMessageId = UUID.randomUUID();
        repository.saveMessage(userMessageId, sessionId, "USER", request.message().trim());
        List<ChatMessage> persisted = repository.findRecentMessages(sessionId, 11);
        List<ChatAiRequest.ChatTurn> history = persisted.stream()
                .filter(message -> !message.id().equals(userMessageId))
                .map(message -> new ChatAiRequest.ChatTurn(message.role(), message.content()))
                .toList();

        List<Contract> userContracts = contracts.list(userId).stream()
                .filter(contract -> session.contractId() == null || session.contractId().equals(contract.id()))
                .limit(20)
                .toList();
        List<ChatAiRequest.ContractContext> context = userContracts.stream().map(this::toContext).toList();

        try {
            JsonNode response = aiClient.post().uri(chatPath).body(new ChatAiRequest(request.message().trim(), history, context))
                    .retrieve().body(JsonNode.class);
            if (response == null || !response.hasNonNull("answer") || response.get("answer").asText().isBlank()) {
                throw new IllegalStateException("AI 챗봇이 답변을 반환하지 않았습니다.");
            }
            String answer = response.get("answer").asText();
            UUID assistantMessageId = UUID.randomUUID();
            ChatMetadata metadata = parseMetadata(response, context);
            repository.saveMessage(assistantMessageId, sessionId, "ASSISTANT", answer, metadata);
            JsonNode timings = response.get("timings");
            if (timings != null && timings.isObject()) {
                timings.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isNumber() && entry.getKey().matches("[A-Za-z0-9_]{1,64}")) {
                        log.info("Chat AI timing sessionId={} stage={} seconds={}", sessionId,
                                entry.getKey(), entry.getValue().doubleValue());
                    }
                });
            }
            ChatMessage saved = repository.findMessage(assistantMessageId, sessionId).orElseThrow();
            return new ChatReply(sessionId, saved);
        } catch (RestClientException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 챗봇 서버와 통신하지 못했습니다.", e);
        }
    }

    private ChatSession requireSession(UUID userId, UUID sessionId) {
        return repository.findSession(sessionId, userId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대화 세션을 찾을 수 없습니다."));
    }

    private ChatAiRequest.ContractContext toContext(Contract contract) {
        return new ChatAiRequest.ContractContext(contract.id().toString(), contract.title(), contract.category(),
                contract.providerName(), contract.status(), string(contract.contractEndDate()),
                string(contract.nextPaymentDate()), string(contract.cancellationDeadline()),
                contract.monthlyAmount(),
                truncate(contract.cancellationTerms(), 2500), truncate(contract.plainSummary(), 1000),
                string(contract.contractStartDate()), contract.penaltyAmount(), contract.amountVerified(),
                contract.currency(), repository.findRiskReports(contract.id()));
    }

    private ChatMetadata parseMetadata(JsonNode response, List<ChatAiRequest.ContractContext> context) {
        ObjectNode fields = mapper.createObjectNode();
        for (String field : List.of("answerType", "referencedContractIds", "legalSources", "needsMoreInfo")) {
            if (response.has(field)) fields.set(field, response.get(field));
        }
        try {
            ChatMetadata metadata = mapper.treeToValue(fields, ChatMetadata.class);
            List<UUID> allowed = context.stream().map(c -> UUID.fromString(c.contractId())).toList();
            if (!allowed.containsAll(metadata.referencedContractIds())) {
                throw new IllegalStateException("AI가 요청 범위 밖의 계약을 참조했습니다.");
            }
            return metadata;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("AI 응답 메타데이터 형식이 올바르지 않습니다.", e);
        }
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
