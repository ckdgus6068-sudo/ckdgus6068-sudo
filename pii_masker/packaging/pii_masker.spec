# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller 스펙 — 오프라인 단일 실행본(.exe) 빌드.

Windows에서 빌드:  pyinstaller packaging/pii_masker.spec
산출물: dist/pii_masker/ (폴더형) — 인터넷 없이 동작.

엔진별로 데이터/숨은 import가 다르므로, 사용하는 엔진의 블록만 켠다.
OCR/NER 모델은 models/ 폴더에 넣어 동봉하면 함께 패키징된다.
"""
from PyInstaller.utils.hooks import collect_all

block_cipher = None

datas = [("../assets/NanumGothic.ttf", "assets")]   # 한글 라벨 폰트(동봉)
binaries = []
hiddenimports = []


def add(pkg):
    d, b, h = collect_all(pkg)
    datas.extend(d); binaries.extend(b); hiddenimports.extend(h)


# --- 사용하는 엔진만 활성화 -------------------------------------------
# EasyOCR 사용 시:
# add("easyocr"); add("torch"); add("torchvision")
# PaddleOCR 사용 시(오프라인 권장):
# add("paddleocr"); add("paddlex"); add("paddle")
# NER(transformers) 사용 시:
# add("transformers"); add("tokenizers")

# 동봉 모델(있으면): models/ 전체를 데이터로 포함
import os
if os.path.isdir("../models"):
    datas.append(("../models", "models"))


a = Analysis(
    ["../gui.py"],
    pathex=[".."],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    runtime_hooks=[],
    excludes=[],
    cipher=block_cipher,
)
pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)
exe = EXE(pyz, a.scripts, [], exclude_binaries=True, name="pii_masker",
          console=False, disable_windowed_traceback=False)
coll = COLLECT(exe, a.binaries, a.datas, name="pii_masker")
