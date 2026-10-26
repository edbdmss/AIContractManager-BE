
from datetime import date
from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field, field_validator


class ContractExtraction(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str = Field(min_length=1, max_length=200)
    category: str = Field(min_length=1, max_length=50)

    providerName: str | None = Field(default=None, max_length=200)

    contractStartDate: date | None = None
    contractEndDate: date | None = None
    autoRenewalDate: date | None = None
    cancellationDeadline: date | None = None

    monthlyAmount: Decimal | None = Field(default=None, ge=0)
    penaltyAmount: Decimal | None = Field(default=None, ge=0)

    cancellationTerms: str | None = None
    plainSummary: str | None = None

    @field_validator("title", "category")
    @classmethod
    def validate_required_text(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("빈 문자열은 허용하지 않습니다.")
        return value
