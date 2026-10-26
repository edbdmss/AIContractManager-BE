
import re
from dataclasses import dataclass, field

import pymupdf


# 1. 정형 개인정보 탐지
PII_PATTERNS = [
    # 주민등록번호
    re.compile(r"(?<!\d)\d{6}\s*-\s*[1-8]\d{6}(?!\d)"),

    # 휴대전화
    re.compile(r"(?<!\d)01[016789][-\s]?\d{3,4}[-\s]?\d{4}(?!\d)"),

    # 일반 전화번호
    re.compile(r"(?<!\d)0\d{1,2}[-\s]\d{3,4}[-\s]\d{4}(?!\d)"),

    # 이메일
    re.compile(r"[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}"),
]


# 2. 개인 / 사업자 역할 구분
PERSON_ROLES = (
    "계약자",
    "이용자",
    "고객",
    "신청인",
    "임대인",
    "임차인",
    "개인",
)

BUSINESS_ROLES = (
    "사업자",
    "사업장",
    "법인",
    "회사",
    "업체",
    "공급자",
)


# 3. 이름 탐지
NAME_PATTERN = re.compile(
    r"(?P<label>"
    r"계약자|이용자|고객명|고객|성명|신청인|"
    r"임대인|임차인|"
    r"[갑을]\s*[\(（][^)）\n]+[\)）]|"
    r"[갑을]"
    r")\s*[:：]\s*"
    r"(?P<value>[가-힣]{2,5})"
    r"(?=\s|$|[,，(（])"
)


# 4. 주소 탐지
ADDRESS_PATTERN = re.compile(
    r"(?P<label>"
    r"사업장\s*주소|사업자\s*주소|법인\s*주소|회사\s*주소|"
    r"계약자\s*주소|이용자\s*주소|고객\s*주소|"
    r"임대인\s*주소|임차인\s*주소|"
    r"거주지|자택\s*주소|"
    r"[갑을]\s*[\(（][^)）\n]+[\)）]\s*주소|"
    r"[갑을]\s*주소|주소"
    r")\s*[:：]\s*"
    r"(?P<value>[^\n\r]+)"
)


# 5. 갑·을 역할 탐지
PARTY_PATTERN = re.compile(
    r"(?P<party>[갑을])\s*"
    r"[\(（]\s*"
    r"(?P<role>[^)）\n]+)"
    r"\s*[\)）]"
)


@dataclass
class MaskingResult:
    pdf_bytes: bytes
    masked_values: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)


def is_business_role(label: str) -> bool:
    return any(role in label for role in BUSINESS_ROLES)


def is_person_role(label: str) -> bool:
    return any(role in label for role in PERSON_ROLES)


def get_party_roles(text: str) -> dict[str, str]:
    """갑·을의 역할을 개인/사업자 기준으로 분류한다."""

    roles = {}

    for match in PARTY_PATTERN.finditer(text):
        party = match.group("party")
        role = match.group("role").strip()

        person = is_person_role(role)
        business = is_business_role(role)

        # 개인인지 사업자인지 알 수 없는 표현은 역할로 사용하지 않음
        if person == business:
            continue

        role_type = "개인" if person else "사업자"

        # 같은 갑/을에 서로 다른 유형이 발견되면 전송 차단
        if party in roles and roles[party] != role_type:
            raise ValueError(
                f"{party}의 개인/사업자 역할이 충돌합니다."
            )

        roles[party] = role_type

    return roles


def should_mask(
    label: str,
    party_roles: dict[str, str],
) -> bool | None:
    """
    True  : 개인 정보 → 마스킹
    False : 사업자 정보 → 유지
    None  : 역할 불명확 → 경고
    """

    normalized = re.sub(r"\s+", "", label)

    # 갑/을이 포함된 항목은 전체 문서 역할 우선 확인
    if normalized.startswith(("갑", "을")):
        party = normalized[0]

        # 갑(임대인), 을(법인)처럼 역할이 직접 적힌 경우
        explicit_role = re.search(
            r"[\(（]([^)）]+)[\)）]",
            normalized
        )

        role = (
            explicit_role.group(1)
            if explicit_role
            else party_roles.get(party, "")
        )

        person = is_person_role(role)
        business = is_business_role(role)

        if person and not business:
            return True

        if business and not person:
            return False

        return None

    person = is_person_role(normalized)
    business = is_business_role(normalized)

    if person and not business:
        return True

    if business and not person:
        return False

    if normalized in ("성명", "거주지", "자택주소"):
        return True

    return None


def resolve_address_role(
    text: str,
    match: re.Match,
    party_roles: dict[str, str],
) -> bool | None:
    """주소 항목의 소유자 역할 판단"""

    label = match.group("label")
    normalized = re.sub(r"\s+", "", label)

    # 갑 주소 / 을 주소는 전체 문서의 역할 활용
    if normalized.startswith(("갑", "을")):
        return should_mask(label, party_roles)

    # 사업장 주소, 이용자 주소 등 명시된 항목
    if normalized != "주소":
        return should_mask(label, party_roles)

    # 단순 '주소:'는 같은 줄 앞의 문맥 확인
    line_start = text.rfind("\n", 0, match.start()) + 1
    prefix = text[line_start:match.start()]

    person = is_person_role(prefix)
    business = is_business_role(prefix)

    if person and not business:
        return True

    if business and not person:
        return False

    # 앞줄이 사업자/개인 항목인지 확인
    previous_text = text[:line_start].rstrip()
    previous_line = (
        previous_text.split("\n")[-1]
        if previous_text
        else ""
    )

    person = is_person_role(previous_line)
    business = is_business_role(previous_line)

    if person and not business:
        return True

    if business and not person:
        return False

    return None


def find_personal_info(
    text: str,
    party_roles: dict[str, str],
) -> tuple[set[str], list[str]]:
    """마스킹할 개인정보와 검토 경고 반환"""

    values = set()
    warnings = []

    # 전화번호, 이메일, 주민등록번호
    for pattern in PII_PATTERNS:
        for match in pattern.finditer(text):
            values.add(match.group())

    # 개인 이름
    for match in NAME_PATTERN.finditer(text):
        label = match.group("label")
        value = match.group("value")

        decision = should_mask(label, party_roles)

        if decision is True:
            values.add(value)

        elif decision is None:
            warnings.append(
                f"이름 역할 확인 필요: {label}"
            )

    # 주소
    for match in ADDRESS_PATTERN.finditer(text):
        label = match.group("label")
        value = match.group("value").strip()

        if not value:
            continue

        decision = resolve_address_role(
            text,
            match,
            party_roles,
        )

        if decision is True:
            values.add(value)

        elif decision is None:
            warnings.append(
                f"주소 역할 확인 필요: {label}"
            )

    return values, warnings


def mask_pdf(pdf_bytes: bytes) -> MaskingResult:
    """PDF 개인정보 탐지 및 영구 삭제"""

    if not pdf_bytes:
        raise ValueError("PDF 파일이 비어 있습니다.")

    all_values = set()
    all_warnings = []

    with pymupdf.open(
        stream=pdf_bytes,
        filetype="pdf",
    ) as doc:

        # 1. 전체 페이지에서 갑·을 역할 확인
        full_text = "\n".join(
            page.get_text("text")
            for page in doc
        )

        party_roles = get_party_roles(full_text)

        # 실제 개인정보를 로그에 출력하지 않음
        print(
            f"[DEBUG] 갑을 역할: {party_roles}",
            flush=True,
        )

        # 2. 페이지별 개인정보 탐지
        for page_number, page in enumerate(
            doc,
            start=1,
        ):
            text = page.get_text("text")

            if not text.strip():
                all_warnings.append(
                    f"{page_number}페이지: "
                    "추출 가능한 텍스트 없음 (OCR 필요)"
                )
                continue

            values, warnings = find_personal_info(
                text,
                party_roles,
            )

            all_values.update(values)

            all_warnings.extend(
                f"{page_number}페이지: {warning}"
                for warning in warnings
            )

            # 3. 개인정보 위치 탐색 후 삭제
            for value in values:
                rects = page.search_for(value)

                if not rects:
                    all_warnings.append(
                        f"{page_number}페이지: "
                        "마스킹 위치 탐색 실패"
                    )
                    continue

                for rect in rects:
                    page.add_redact_annot(
                        rect,
                        fill=(0, 0, 0),
                    )

            page.apply_redactions(
                images=pymupdf.PDF_REDACT_IMAGE_PIXELS
            )

        # 4. 삭제된 데이터를 제외하고 PDF 저장
        masked_pdf = doc.tobytes(
            garbage=4,
            deflate=True,
            clean=True,
        )

    # 5. 마스킹 결과 재검증
    with pymupdf.open(
        stream=masked_pdf,
        filetype="pdf",
    ) as check_doc:
        remaining_text = "\n".join(
            page.get_text("text")
            for page in check_doc
        )

    for value in all_values:
        if value in remaining_text:
            all_warnings.append(
                "마스킹 후 개인정보 문자열이 남아 있습니다."
            )

    return MaskingResult(
        pdf_bytes=masked_pdf,
        masked_values=sorted(all_values),
        warnings=all_warnings,
    )
