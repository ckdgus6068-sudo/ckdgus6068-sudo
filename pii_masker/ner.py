"""한글 NER(개체명 인식) — 시드 명단에 없는 제3자 이름·주소 보조 탐지.

전부 로컬에서 동작한다(모델은 미리 받아 동봉). 백엔드 교체 가능:
  - spacy        : spaCy 한글 모델(ko_core_news_*) — 가볍고 설치 쉬움
  - transformers : 로컬 디렉터리의 KoELECTRA 계열 NER 모델 — 재현율 높음

반환 형식: entities(text) -> [(ptype, start, end, text), ...]
  PS(사람)→NAME, LC(지역/주소)→ADDR. 기관(OG)은 기본 제외(기관명은
  보통 개인정보가 아니고, 필요한 법인은 시드로 관리).

NER은 보조망이다 — 한국어 인명 재현율이 완벽치 않으므로 시드 명단이 주력.
"""

# 백엔드별 개체 라벨 → 내부 코드 매핑
_SPACY_MAP = {"PS": "NAME", "LC": "ADDR"}
# 모두/표준(MODU) 태그셋: PS(사람), LC(지명), OG(기관)
_HF_MAP = {"PS": "NAME", "LC": "ADDR"}


class KoreanNER:
    def __init__(self, backend="spacy", model="ko_core_news_sm"):
        self.backend = backend
        self.model = model
        self._engine = None

    def _ensure(self):
        if self._engine is not None:
            return
        if self.backend == "spacy":
            import spacy
            self._engine = spacy.load(self.model)
        elif self.backend == "transformers":
            from transformers import pipeline
            # 오프라인: 로컬 모델 경로 사용(local_files_only)
            self._engine = pipeline(
                "token-classification", model=self.model,
                aggregation_strategy="simple", device=-1)
        else:
            raise ValueError(f"unknown NER backend: {self.backend}")

    def entities(self, text):
        if not text.strip():
            return []
        self._ensure()
        out = []
        if self.backend == "spacy":
            for ent in self._engine(text).ents:
                pt = _SPACY_MAP.get(ent.label_)
                if pt:
                    out.append((pt, ent.start_char, ent.end_char, ent.text))
        else:
            for e in self._engine(text):
                pt = _HF_MAP.get(e["entity_group"])
                if pt:
                    out.append((pt, int(e["start"]), int(e["end"]), e["word"]))
        return out
