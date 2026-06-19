"""마스킹 후보 자동 추출 — 검토용 '명단 초안' 생성.

ML·인터넷 없이 패턴 + 문맥 단서로 동작한다(오프라인·저사양 친화).
문서에서 인물/법인 후보를 찾아 역할을 '추정'해 라벨 초안을 달아준다.
OCR이 부정확하면 오탐·누락이 있을 수 있으므로 결과는 반드시 사람이 검토·수정한다.
"""
import re
from collections import OrderedDict

# 한국 인명: 2~3자, 글자 사이 공백 허용("여 인 석")
NAME = r"[가-힣](?:\s?[가-힣]){1,2}"

# (정규식, 추정역할) — 오탐을 줄이려 '강한 단서'만 사용. 캡처그룹=이름.
PERSON_RULES = [
    (r"(?:피고소인|피의자)\s*\d*\s*[.:：]?\s*(" + NAME + r")", "피의자"),
    (r"(" + NAME + r")\s*[(（]\s*\d{6}\s*-\s*\d{7}", "피의자"),
    (r"고소외\s*\d*\s*[:：]?\s*(" + NAME + r")", "고소외"),
    (r"담당\s*변호사\s*[:：]?\s*(" + NAME + r")", "대리인"),
    (r"지인\s*인?\s*(" + NAME + r")\s*변호사", "참고인"),
    (r"(?:^|[\s(（:：,])(" + NAME + r")\s*변호사", "참고인"),
    (r"대표이사\s*[:：]?\s*(" + NAME + r")\s*[)）]", "회사대표"),
]
JOSA = ("에게", "께서", "은", "는", "이", "가", "을", "를", "과", "와", "의", "도", "께", "씨", "군", "양")
STOP = {
    "피고소인", "고소인", "고소외", "참고인", "피의자", "대리인", "담당", "대표",
    "주식회사", "법무법인", "대표이사", "사업부지", "투자금", "수사기관",
    "고소장", "의견서", "본건", "당시", "이하", "관련", "해당", "지인",
}
# 회사명 추출용 — 문법어/조사(오탐 방지)
ORG_STOP = {
    "로서", "있는", "하는", "되는", "같은", "위한", "통한", "따른", "대한", "관한",
    "관련", "의한", "라는", "에서", "으로", "로써", "그", "이", "본", "위", "및",
    "또는", "당시", "라고", "함은", "함을", "지난", "당사", "해당", "각각",
}
ORG_JOSA = ("으로부터", "로부터", "으로서", "으로써", "으로", "에서", "에게", "로써",
            "은", "는", "이", "가", "을", "를", "의", "에", "와", "과", "도", "로")


def _strip_org_josa(name):
    for j in sorted(ORG_JOSA, key=len, reverse=True):
        if len(name) - len(j) >= 2 and name.endswith(j):
            return name[:-len(j)]
    return name


def _extract_orgs(raw):
    """한 줄에서 법인/회사명을 추출(접두/접미형 구분, 문법어 제외)."""
    out = []
    for m in re.finditer(r"주식회사", raw):
        after = raw[m.end():].lstrip()
        before = raw[:m.start()].rstrip()
        am = re.match(r"[가-힣A-Za-z0-9]{2,10}", after)
        if am:  # 접두형: '주식회사 OOO' → 뒤 이름 사용
            nm = _strip_org_josa(am.group(0))
            if nm and nm not in ORG_STOP:
                out.append("주식회사 " + nm)
        else:   # 접미형: 'OOO 주식회사' → 앞 1~2어절 사용(문법어 제외)
            toks = re.findall(r"[가-힣A-Za-z0-9]+", before)
            picked = []
            for w in reversed(toks[-2:]):
                if w in ORG_STOP:
                    break
                picked.insert(0, w)
            if picked:
                out.append(" ".join(picked) + " 주식회사")
    for m in re.finditer(r"법무법인\s*([가-힣]{2,10})", raw):
        out.append("법무법인 " + m.group(1))
    for m in re.finditer(r"([가-힣]{2,8})은행", raw):
        nm = m.group(1)
        if nm not in ORG_STOP:
            out.append(nm + "은행")
    for m in re.finditer(r"([가-힣A-Za-z0-9]{2,16})\s*제\s*(\d+)\s*호", raw):
        nm = m.group(1)
        if nm not in ORG_STOP:
            out.append(nm + "제" + m.group(2) + "호")
    return out
# 역할 라벨 순서(보고서 가독성)
ROLE_ORDER = ["피의자", "고소인측", "고소외", "참고인", "대리인", "회사대표",
              "관계인", "수사관계인", "인물"]
ROLE_RANK = {r: i for i, r in enumerate(ROLE_ORDER)}


def _strip_josa(name):
    for j in sorted(JOSA, key=len, reverse=True):
        if len(name) - len(j) >= 2 and name.endswith(j):
            return name[:-len(j)]
    return name


def _better_role(old, new):
    return new if ROLE_RANK.get(new, 99) < ROLE_RANK.get(old, 99) else old


def suggest(line_texts):
    """line_texts: [줄별 원문 텍스트]. 반환: dict(persons, orgs)."""
    persons = OrderedDict()  # name -> {role, count, sample}
    orgs = OrderedDict()     # org -> {count, sample}

    def add_person(name, role, sample):
        name = _strip_josa(re.sub(r"\s+", "", name))  # 이름 글자 사이 공백 제거
        if len(name) < 2 or name in STOP:
            return
        if name in persons:
            persons[name]["count"] += 1
            persons[name]["role"] = _better_role(persons[name]["role"], role)
        else:
            persons[name] = {"role": role, "count": 1, "order": len(persons),
                             "sample": sample.strip()[:60]}

    for raw in line_texts:
        # 인물: 강한 문맥 단서 규칙(이름의 글자 사이 공백은 캡처 후 제거)
        for pat, role in PERSON_RULES:
            for m in re.finditer(pat, raw):
                add_person(m.group(1), role, raw)
        # 법인/회사명(접두·접미 구분, 문법어 제외)
        for org in _extract_orgs(raw):
            org = re.sub(r"\s+", " ", org).strip()
            if org in orgs:
                orgs[org]["count"] += 1
            else:
                orgs[org] = {"count": 1, "sample": raw.strip()[:60]}

    return {"persons": persons, "orgs": orgs}


def build_draft(found):
    """suggest() 결과 → (parties 리스트, 사람이 읽을 검토 리포트 문자열)."""
    persons, orgs = found["persons"], found["orgs"]
    parties = []
    report = ["=== 마스킹 후보 검토 초안 (반드시 확인·수정하세요) ===", ""]

    # 인물: 역할별 번호 매기기
    counters = {}
    # 역할 순 → 문서 등장 순 정렬
    items = sorted(persons.items(),
                   key=lambda kv: (ROLE_RANK.get(kv[1]["role"], 99), kv[1]["order"]))
    report.append("[인물 후보]")
    for name, info in items:
        role = info["role"]
        counters[role] = counters.get(role, 0) + 1
        label = f"{role}{counters[role]}"
        parties.append({"label": label, "names": [name], "ids": []})
        report.append(f"  {label}\t← {name}  (등장 {info['count']}회)  예: {info['sample']}")

    # 법인: 회사N / 법무법인N / 은행N
    report.append("")
    report.append("[법인/회사 후보]")
    org_counters = {}
    for org, info in sorted(orgs.items(), key=lambda kv: -kv[1]["count"]):
        if "법무법인" in org:
            kind = "법무법인"
        elif org.endswith("은행"):
            kind = "은행"
        else:
            kind = "회사"
        org_counters[kind] = org_counters.get(kind, 0) + 1
        label = f"{kind}{org_counters[kind]}"
        parties.append({"label": label, "names": [org], "ids": []})
        report.append(f"  {label}\t← {org}  (등장 {info['count']}회)")

    report += ["", "※ 주민번호·전화·계좌 등 정형정보는 명단 없이 자동 마스킹됩니다.",
               "※ 역할(피의자/참고인 등)은 추정값입니다. 라벨을 직접 확인·수정하세요."]
    return parties, "\n".join(report)
