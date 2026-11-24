from decimal import Decimal, InvalidOperation
from typing import Any, Literal
from uuid import UUID

from pydantic import (
    BaseModel,
    Field,
    AliasChoices,
    field_validator,
)


class ChatMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str

    @field_validator("role", mode="before")
    @classmethod
    def normalize_role(cls, value):
        if isinstance(value, str):
            return value.strip().lower()
        return value


class ChatRequest(BaseModel):
    message: str = Field(
        min_length=1,
        max_length=4000,
        validation_alias=AliasChoices(
            "question", "message"
        ),
    )

    contracts: list[dict[str, Any]] = Field(
        default_factory=list,
        validation_alias=AliasChoices(
            "contractContext", "contracts"
        ),
    )

    contractId: str | None = None

    history: list[ChatMessage] = Field(
        default_factory=list,
        max_length=10,
    )

    @field_validator("message")
    @classmethod
    def validate_message(cls, value):
        value = value.strip()

        if not value:
            raise ValueError("질문을 입력해 주세요.")

        return value

    @field_validator("contractId")
    @classmethod
    def validate_contract_id(cls, value):
        if value is None:
            return None

        return str(UUID(value))

    @field_validator("contracts")
    @classmethod
    def normalize_contracts(cls, contracts):
        normalized = []

        for contract in contracts:
            item = dict(contract)

            contract_id = (
                item.get("contractId")
                or item.get("id")
            )

            if contract_id is None:
                raise ValueError(
                    "계약 ID가 누락되었습니다."
                )

            contract_id = str(UUID(str(contract_id)))

            item["id"] = contract_id
            item["contractId"] = contract_id

            for field in ("category", "status", "currency"):
                value = item.get(field)

                if isinstance(value, str):
                    item[field] = value.strip().upper()

            for field in (
                "monthlyAmount",
                "penaltyAmount",
            ):
                value = item.get(field)

                if value is None:
                    continue

                try:
                    amount = Decimal(str(value))

                    if not amount.is_finite() or amount < 0:
                        raise ValueError(
                            f"{field}: 올바르지 않은 금액"
                        )

                    if amount == amount.to_integral_value():
                        item[field] = int(amount)
                    else:
                        if field == "monthlyAmount":
                            raise ValueError(
                                "monthlyAmount는 정수여야 합니다."
                            )

                        item[field] = float(amount)

                except (InvalidOperation, OverflowError):
                    raise ValueError(
                        f"{field}: 잘못된 금액 형식"
                    )

            normalized.append(item)

        return normalized


class ChatResponse(BaseModel):
    answer: str

    answerType: Literal[
        "CONTRACT",
        "LEGAL",
        "HYBRID",
    ]

    referencedContractIds: list[str] = Field(
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
        description="사용자에게 전달할 한국어 답변"
    )

    answerType: Literal[
        "CONTRACT",
        "LEGAL",
        "HYBRID",
    ]

    referencedContractIds: list[str] = Field(
        default_factory=list,
        description="실제 답변에 사용한 계약 UUID 목록",
    )

    needsMoreInfo: bool = False

    usedRuleIds: list[str] = Field(
        default_factory=list,
        description="실제 답변에 사용한 법률 ruleId 목록",
    )