import json
import os
import time
import logging

from pathlib import Path

from dotenv import load_dotenv
from google import genai
from google.genai import types

from chatbot.schemas import (
    ChatResponse,
    GeminiChatResult,
)

from chatbot.question_router import classify_question

from chatbot.contract_context import (
    build_contract_context,
)

from chatbot.contract_selector import (
    infer_categories,
    LEGAL_CATEGORIES,
)

from chatbot.prompts import SYSTEM_PROMPT

from rag.legal_retriever import retrieve_legal_rules


load_dotenv(
    Path(__file__).resolve().parent.parent / ".env"
)


GEMINI_MODEL = os.getenv(
    "GEMINI_CHAT_MODEL",
    "gemini-3.5-flash-lite",
)


logger = logging.getLogger(__name__)


def _history_to_dict(history):
    result = []

    for item in history[-10:]:

        if hasattr(item, "model_dump"):
            data = item.model_dump()
        else:
            data = dict(item)

        role = str(
            data.get("role", "")
        ).lower()

        if role not in ("user", "assistant"):
            continue

        result.append({
            "role": role,
            "content": str(
                data.get("content", "")
            )[:4000],
        })

    return result


def _normalize_legal_rule(rule):
    return {
        **rule,

        "ruleId": (
            rule.get("ruleId")
            or rule.get("rule_id")
        ),

        "lawName": (
            rule.get("lawName")
            or rule.get("law_name")
        ),

        "sourceUrl": (
            rule.get("sourceUrl")
            or rule.get("source_url")
        ),
    }


def _rule_to_source(rule):
    return {
        "ruleId": rule.get("ruleId"),
        "lawName": rule.get("lawName"),
        "article": rule.get("article"),
        "title": rule.get("title"),
        "sourceUrl": rule.get("sourceUrl"),
    }


def _build_search_query(
    message,
    history,
):
    # 현재 질문이 짧은 후속 질문이라면
    # 직전 사용자 질문을 함께 검색에 활용
    followup_words = (
        "그럼",
        "그러면",
        "그거",
        "그건",
        "그 경우",
        "얼마",
        "어떻게",
        "가능해",
    )

    is_followup = (
        len(message) <= 35
        and any(
            word in message
            for word in followup_words
        )
    )

    if not is_followup:
        return message

    for item in reversed(history):

        if item.get("role") == "user":

            return (
                item.get("content", "")
                + "\n"
                + message
            )

    return message


def _retrieve_legal_context(
    message,
    contracts,
    history,
):
    start = time.perf_counter()

    categories = infer_categories(
        message=message,
        contracts=contracts,
        history=history,
    )

    search_query = _build_search_query(
        message,
        history,
    )

    # 카테고리가 특정되지 않은 일반 법률 질문은
    # 현재 구축된 법률 카테고리를 검색
    if not categories:
        categories = sorted(LEGAL_CATEGORIES)

    collected_rules = {}

    retrieval_status = {}

    for category in categories:

        # 구독, 기타, 미분류는
        # 기존 계약 법률 RAG에서 제외
        if category not in LEGAL_CATEGORIES:

            retrieval_status[category] = (
                "LEGAL_DATA_NOT_AVAILABLE"
            )

            continue

        result = retrieve_legal_rules(
            query=search_query,
            category=category,
            top_k=3,
        )

        retrieval_status[category] = result.get(
            "status"
        )

        for rule in result.get("results", []):

            normalized = _normalize_legal_rule(
                rule
            )

            rule_id = normalized.get("ruleId")

            if not rule_id:
                continue

            collected_rules[rule_id] = normalized

    rules = list(
        collected_rules.values()
    )

    return {
        "categories": categories,

        "rules": rules,

        "status": retrieval_status,

        "retrieval_time": round(
            time.perf_counter() - start,
            6,
        ),
    }


def _generate_structured_answer(payload):
    api_key = os.getenv("GEMINI_API_KEY")

    if not api_key:
        raise ValueError(
            "GEMINI_API_KEY가 설정되지 않았습니다."
        )

    client = genai.Client(
        api_key=api_key
    )

    try:
        response = client.models.generate_content(
            model=GEMINI_MODEL,

            contents=json.dumps(
                payload,
                ensure_ascii=False,
            ),

            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_PROMPT,

                temperature=0.2,

                response_mime_type="application/json",

                response_schema=GeminiChatResult,
            ),
        )

        parsed = response.parsed

        if isinstance(parsed, GeminiChatResult):
            return parsed

        if isinstance(parsed, dict):
            return GeminiChatResult.model_validate(
                parsed
            )

        if not response.text:
            raise ValueError(
                "Gemini 응답이 비어 있습니다."
            )

        return GeminiChatResult.model_validate_json(
            response.text
        )

    finally:
        client.close()


def process_chat(request):
    total_start = time.perf_counter()

    # ========================================
    # 1. 질문 및 대화 이력 준비
    # ========================================

    preparation_start = time.perf_counter()

    message = request.message.strip()

    contracts = request.contracts

    history = _history_to_dict(
        request.history
    )

    preparation_time = round(
        time.perf_counter() - preparation_start,
        6,
    )

    # ========================================
    # 2. 계약 Context 구성
    # ========================================

    context = build_contract_context(
        contracts=contracts,
        contract_id=request.contractId,
    )

    context_time = context.pop(
        "context_time"
    )

    selected_contracts = context["contracts"]

    # ========================================
    # 3. 법률 RAG 필요 여부 판단
    # ========================================

    route = classify_question(
        message=message,

        has_contract=bool(
            selected_contracts
        ),

        history=history,
    )

    routing_time = route["routing_time"]

    # ========================================
    # 4. 필요한 경우에만 법률 RAG 검색
    # ========================================

    legal_context = {
        "categories": [],
        "rules": [],
        "status": {},
        "retrieval_time": 0.0,
    }

    if route["needs_legal_rag"]:

        legal_context = _retrieve_legal_context(
            message=message,

            contracts=selected_contracts,

            history=history,
        )

    legal_rules = legal_context["rules"]

    # ========================================
    # 5. Gemini 입력 구성
    # ========================================

    payload_start = time.perf_counter()

    payload = {
        "user_question": message,

        "history": history,

        "contract_context": context,

        "legal_rules": legal_rules,

        "legal_retrieval_status": (
            legal_context["status"]
        ),

        "legal_retrieval_categories": (
            legal_context["categories"]
        ),

        "subscription_policy_available": False,

        "contract_context_complete": False,
    }

    payload_time = round(
        time.perf_counter() - payload_start,
        6,
    )

    # ========================================
    # 6. Gemini 답변 생성
    # ========================================

    generation_start = time.perf_counter()

    generated = _generate_structured_answer(
        payload
    )

    generation_time = round(
        time.perf_counter() - generation_start,
        6,
    )

    # ========================================
    # 7. 계약 ID 및 법률 출처 검증
    # ========================================

    validation_start = time.perf_counter()

    valid_contract_ids = {
        str(contract["id"])

        for contract in selected_contracts

        if contract.get("id")
    }

    referenced_contract_ids = list(
        dict.fromkeys(
            contract_id

            for contract_id
            in generated.referencedContractIds

            if contract_id in valid_contract_ids
        )
    )

    used_rule_ids = set(
        generated.usedRuleIds
    )

    legal_sources = [
        _rule_to_source(rule)

        for rule in legal_rules

        if rule.get("ruleId") in used_rule_ids
    ]

    answer_type = generated.answerType

    if legal_sources:

        answer_type = (
            "HYBRID"
            if referenced_contract_ids
            else "LEGAL"
        )

    answer = generated.answer.strip()

    if not answer:
        raise ValueError(
            "생성된 답변이 비어 있습니다."
        )

    # 검색된 법률 근거가 없는 법률 답변은
    # 확인되지 않은 판단임을 명시
    if (
        answer_type in ("LEGAL", "HYBRID")
        and not legal_sources
    ):
        answer += (
            "\n\n※ 확인된 법률 검색 근거가 없어 "
            "구체적인 법적 판단은 추가 확인이 필요합니다."
        )

    validation_time = round(
        time.perf_counter() - validation_start,
        6,
    )

    # ========================================
    # 8. 실행시간 측정
    # ========================================

    total_time = round(
        time.perf_counter() - total_start,
        6,
    )

    timings = {
        "preparation_time": preparation_time,

        "context_time": context_time,

        "routing_time": routing_time,

        "retrieval_time": (
            legal_context["retrieval_time"]
        ),

        "payload_time": payload_time,

        "generation_time": generation_time,

        "validation_time": validation_time,

        "total_time": total_time,
    }

    # 사용자 질문과 계약 원문은 로그에 출력하지 않음
    print(
        "[CHAT] "
        f"answerType={answer_type}, "
        f"contracts={len(selected_contracts)}, "
        f"referenced={len(referenced_contract_ids)}, "
        f"legalRules={len(legal_rules)}, "
        f"legalSources={len(legal_sources)}, "
        f"total={total_time:.6f}s",
        flush=True,
    )

    print(
        "[CHAT TIMINGS] "
        + json.dumps(
            timings,
            ensure_ascii=False,
        ),
        flush=True,
    )

    # ========================================
    # 9. 최종 응답 반환
    # ========================================

    return ChatResponse(
        answer=answer,

        answerType=answer_type,

        referencedContractIds=(
            referenced_contract_ids
        ),

        legalSources=legal_sources,

        needsMoreInfo=(
            generated.needsMoreInfo
        ),

        timings=timings,
    )