
import json
import os
import time
from pathlib import Path

from dotenv import load_dotenv
from google import genai
from google.genai import types

from chatbot.schemas import ChatResponse, GeminiChatResult
from chatbot.question_router import classify_question
from chatbot.contract_context import build_contract_context
from chatbot.contract_selector import select_contract
from chatbot.prompts import SYSTEM_PROMPT
from rag.legal_retriever import retrieve_legal_rules


load_dotenv(Path(__file__).resolve().parent.parent / ".env")

GEMINI_MODEL = os.getenv(
    "GEMINI_CHAT_MODEL",
    "gemini-3.5-flash-lite",
)

def _normalize_legal_rule(rule):
    return {
        "ruleId": rule.get("ruleId") or rule.get("rule_id"),
        "lawName": rule.get("lawName") or rule.get("law_name"),
        "article": rule.get("article"),
        "title": rule.get("title"),
        "sourceUrl": rule.get("sourceUrl") or rule.get("source_url"),
    }

def _history_to_dict(history):
    result = []
    for item in history[-10:]:
        if hasattr(item, "model_dump"):
            data = item.model_dump()
        else:
            data = dict(item)

        if data.get("role") in ("user", "assistant"):
            result.append({
                "role": data["role"],
                "content": str(data.get("content", ""))[:2000],
            })
    return result


def _normalize_legal_rule(rule):
    return {
        **rule,
        "ruleId": rule.get("ruleId") or rule.get("rule_id"),
        "lawName": rule.get("lawName") or rule.get("law_name"),
        "sourceUrl": rule.get("sourceUrl") or rule.get("source_url"),
    }

def _rule_to_source(rule):
    return {
        "ruleId": rule.get("ruleId"),
        "lawName": rule.get("lawName"),
        "article": rule.get("article"),
        "title": rule.get("title"),
        "sourceUrl": rule.get("sourceUrl"),
    }


def _generate_structured_answer(payload):
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise ValueError("GEMINI_API_KEY가 설정되지 않았습니다.")

    client = genai.Client(api_key=api_key)

    response = client.models.generate_content(
        model=GEMINI_MODEL,
        contents=json.dumps(payload, ensure_ascii=False, default=str),
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM_PROMPT + """

[구조화 응답 규칙]
- 반드시 지정된 JSON 스키마로 응답한다.
- answer에는 사용자에게 보여줄 답변만 작성한다.
- 추가 정보가 없어서 정확한 답변이 불가능하거나
  추가 질문이 필요하면 needsMoreInfo를 true로 설정한다.
- usedRuleIds에는 실제 답변의 근거로 사용한
  법률 규칙 ID만 넣는다.
- 제공된 legal_rules에 없는 ID를 만들지 않는다.
- 계약 정보와 법률 정보를 구분한다.
- 이전 대화는 질문 맥락을 이해하는 데만 활용한다.
- 이전 대화의 내용이 현재 계약 정보와 충돌하면
  현재 제공된 계약 정보를 우선한다.
- 계약이나 법률 데이터에 없는 사실은 추측하지 않는다.
- 단순 계약 조회에는 불필요한 법률 설명을 넣지 않는다.
""",
            temperature=0.2,
            response_mime_type="application/json",
            response_schema=GeminiChatResult,
        ),
    )

    if response.parsed is not None:
        if isinstance(response.parsed, GeminiChatResult):
            return response.parsed
        return GeminiChatResult.model_validate(response.parsed)

    if not response.text:
        raise ValueError("Gemini가 빈 응답을 반환했습니다.")

    return GeminiChatResult.model_validate_json(response.text)


def process_chat(request):
    total_start = time.perf_counter()
    timings = {}

    message = request.message.strip()
    contracts = request.contracts or []
    history = request.history or []

    if not message:
        raise ValueError("질문을 입력해 주세요.")

    # 1. 질문과 관련된 계약 선택
    selection = select_contract(
        message=message,
        contracts=contracts,
        contract_id=request.contractId,
        history=history,
    )
    timings["contract_selection"] = selection["selection_time"]

    if selection["reason"] == "INVALID_CONTRACT_ID":
        raise ValueError("요청한 계약 ID를 찾을 수 없습니다.")

    # 2. 질문 유형 분류
    route_start = time.perf_counter()

    # 이전 대화에서 계약을 명확히 지칭한 경우,
    # 현재의 짧은 후속 질문과 함께 유형 분류에 활용한다.
    routing_message = message
    if selection["reason"] == "HISTORY_MATCH":
        previous_user_messages = [
            h["content"]
            for h in _history_to_dict(history)
            if h["role"] == "user"
        ]
        if previous_user_messages:
            routing_message = (
                previous_user_messages[-1] + "\n" + message
            )

    route = classify_question(
        routing_message,
        has_contract=selection["contract"] is not None,
    )

    answer_type = route.get("answerType") or route.get("type")

    if answer_type not in ("CONTRACT", "LEGAL", "HYBRID"):
        raise ValueError(
            f"지원하지 않는 질문 분류 결과입니다: {route}"
    )
    timings["question_routing"] = round(
        time.perf_counter() - route_start, 6
    )

    # 3. 계약이 불명확한 경우
    # 순수 법률 일반 질문은 계약 선택 없이 진행할 수 있다.
    if selection["ambiguous"] and answer_type != "LEGAL":
        names = [
            f"- {c.get('title') or c.get('contractName') or '이름 없는 계약'}"
            f" (ID: {c.get('id')})"
            for c in selection["candidates"][:5]
        ]

        answer = (
            "어떤 계약에 대해 질문하시는지 확인이 필요해요.\n"
            + "\n".join(names)
            + "\n계약명을 알려주시거나 해당 계약을 선택해 주세요."
        )

        timings.update({
            "contract_context": 0.0,
            "legal_retrieval": 0.0,
            "gemini_generation": 0.0,
            "total": round(time.perf_counter() - total_start, 6),
        })

        return ChatResponse(
            answer=answer,
            answerType="CONTRACT",
            referencedContractIds=[],
            legalSources=[],
            needsMoreInfo=True,
            timings=timings,
        )

    selected_contract = selection["contract"]
    selected_id = (
        selected_contract.get("id")
        if selected_contract else None
    )

    # 4. 계약 컨텍스트 구성
    context_start = time.perf_counter()

    context = build_contract_context(
        contracts=[selected_contract] if selected_contract else [],
        contract_id=selected_id,
    )

    timings["contract_context"] = round(
        time.perf_counter() - context_start, 6
    )

    # 5. 법률 RAG 검색
    legal_rules = []
    retrieval_start = time.perf_counter()

    if answer_type in ("LEGAL", "HYBRID"):
        category = (
            selected_contract.get("category", "OTHER")
            if selected_contract else "OTHER"
        )

        retrieval = retrieve_legal_rules(
            query=routing_message,
            category=category,
            top_k=3,
        )
        legal_rules = retrieval.get("results", [])
        legal_rules = [
            _normalize_legal_rule(rule)
            for rule in retrieval.get("results", [])
        ]
        print(
            "[CHAT] 실제 법률 검색 결과:",
            json.dumps(legal_rules, ensure_ascii=False, indent=2),
            flush=True,
        )

    timings["legal_retrieval"] = round(
        time.perf_counter() - retrieval_start, 6
    )

    # 6. Gemini에 전달할 정보 구성
    payload = {
        "user_question": message,
        "answer_type": answer_type,
        "selected_contract": context,
        "legal_rules": legal_rules,
        "history": _history_to_dict(history),
        "instructions": {
            "use_only_provided_contract": True,
            "use_only_retrieved_legal_rules": True,
            "distinguish_contract_and_law": True,
        },
    }

    # 7. 구조화 답변 생성
    generation_start = time.perf_counter()

    generated = _generate_structured_answer(payload)
    print(
    "[CHAT] 검색된 법률 ID:",
    [rule.get("ruleId") for rule in legal_rules],
    flush=True,
    )

    print(
        "[CHAT] Gemini가 사용한 법률 ID:",
        generated.usedRuleIds,
        flush=True,
    )

    timings["gemini_generation"] = round(
        time.perf_counter() - generation_start, 6
    )

    # 8. Gemini가 사용했다고 주장한 법률 ID 검증
    allowed_rules = {
        str(rule.get("ruleId")): rule
        for rule in legal_rules
        if rule.get("ruleId") is not None
    }

    used_ids = set(generated.usedRuleIds)
    verified_sources = [
        _rule_to_source(rule)
        for rule_id, rule in allowed_rules.items()
        if rule_id in used_ids
    ]

    # 법률 검색을 수행하지 않았다면 법률 출처를 반환하지 않음
    if answer_type == "CONTRACT":
        verified_sources = []

    timings["total"] = round(
        time.perf_counter() - total_start, 6
    )

    return ChatResponse(
        answer=generated.answer,
        answerType=answer_type,
        referencedContractIds=(
            [selected_id] if selected_id is not None else []
        ),
        legalSources=verified_sources,
        needsMoreInfo=generated.needsMoreInfo,
        timings=timings,
    )
