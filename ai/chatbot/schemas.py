
from typing import Any, Literal
from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=2000)

    # Spring Boot가 권한 확인 후 전달하는 계약 정보
    contracts: list[dict[str, Any]] = Field(
        default_factory=list
    )

    # 특정 계약에 관한 질문일 때 사용
    contractId: int | None = None

    # 최근 대화 기록
    history: list[ChatMessage] = Field(
        default_factory=list,
        max_length=10
    )


class ChatResponse(BaseModel):
    answer: str

    answerType: Literal[
        "CONTRACT",
        "LEGAL",
        "HYBRID"
    ]

    referencedContractIds: list[int] = Field(
        default_factory=list
    )

    legalSources: list[dict[str, Any]] = Field(
        default_factory=list
    )

    needsMoreInfo: bool = False

    timings: dict[str, float] = Field(
        default_factory=dict
    )




class GeminiChatResult(BaseModel):
    answer: str = Field(
        description="사용자에게 보여줄 한국어 답변"
    )
    needsMoreInfo: bool = Field(
        description="정확한 답변을 위해 추가 정보가 필요한지 여부"
    )
    usedRuleIds: list[str] = Field(
        default_factory=list,
        description="실제 답변에 사용한 검색 결과의 ruleId 목록"
    )
