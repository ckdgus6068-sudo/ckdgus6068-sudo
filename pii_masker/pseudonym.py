"""일관된 가명화(pseudonymization).

같은 값은 문서 전체·여러 문서에 걸쳐 같은 라벨로 치환한다.
대응표(원본값 ↔ 라벨)는 로컬 JSON으로 저장해 나중에 복원할 수 있다.
"""
import json
from pathlib import Path

# 개인정보 종류별 라벨 접두어(한글)
_PREFIX = {
    "RRN": "주민번호",
    "BIZNO": "사업자번호",
    "EMAIL": "이메일",
    "PHONE": "전화",
    "ACCOUNT": "계좌",
    "CASENO": "사건번호",
    "ADDR": "주소",
    "NAME": "성명",
    "ORG": "법인",
}


class Pseudonymizer:
    def __init__(self, mapping_path: str | None = None):
        self.mapping_path = Path(mapping_path) if mapping_path else None
        # value -> label
        self.value_to_label: dict[str, str] = {}
        # 종류별 카운터
        self._counter: dict[str, int] = {}
        if self.mapping_path and self.mapping_path.exists():
            data = json.loads(self.mapping_path.read_text(encoding="utf-8"))
            self.value_to_label = data.get("value_to_label", {})
            self._counter = data.get("counter", {})

    def label_for(self, ptype: str, value: str, fixed_label: str | None = None) -> str:
        """값에 대한 가명 라벨을 반환(없으면 새로 부여)."""
        if value in self.value_to_label:
            return self.value_to_label[value]
        if fixed_label:
            label = fixed_label
        else:
            self._counter[ptype] = self._counter.get(ptype, 0) + 1
            prefix = _PREFIX.get(ptype, ptype)
            label = f"{prefix}{self._counter[ptype]}"
        self.value_to_label[value] = label
        return label

    def save(self):
        if not self.mapping_path:
            return
        self.mapping_path.parent.mkdir(parents=True, exist_ok=True)
        # 복원용 역매핑도 함께 저장
        label_to_value: dict[str, str] = {}
        for v, l in self.value_to_label.items():
            label_to_value.setdefault(l, v)
        payload = {
            "value_to_label": self.value_to_label,
            "label_to_value": label_to_value,
            "counter": self._counter,
        }
        self.mapping_path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
        )
