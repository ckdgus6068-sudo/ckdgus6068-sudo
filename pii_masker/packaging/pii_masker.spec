# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller 스펙 — 오프라인 단일 실행본 빌드 (기본: EasyOCR).

Windows에서 빌드:  pyinstaller packaging/pii_masker.spec
산출물: dist/pii_masker/ (폴더형). 이 폴더를 통째로 복사하면 설치·인터넷 없이
        pii_masker.exe 더블클릭으로 실행된다.

오프라인 핵심:
  - EasyOCR 한글 모델(.pth)을 models/easyocr/ 에 넣으면 함께 묶이고,
    실행 시 자동으로 그 모델을 쓰며 다운로드를 시도하지 않는다.
  - 빌드 PC의  C:\\Users\\<사용자>\\.EasyOCR\\model  폴더 안의 파일들을
    이 프로젝트의  models\\easyocr\\  로 복사해 두고 빌드할 것.
"""
import os
from PyInstaller.utils.hooks import collect_all

block_cipher = None

datas = [("../assets/NanumGothic.ttf", "assets")]   # 한글 라벨 폰트(동봉)
binaries = []
hiddenimports = []


def add(pkg):
    d, b, h = collect_all(pkg)
    datas.extend(d); binaries.extend(b); hiddenimports.extend(h)


# --- EasyOCR(기본 엔진) + torch 수집 -------------------------------------
add("easyocr")
add("torch")
add("torchvision")
add("skimage")          # easyocr 의존
add("scipy")

# (선택) PaddleOCR 쓸 경우 위 easyocr 블록 대신:
#   add("paddleocr"); add("paddlex"); add("paddle")
# (선택) NER(transformers) 쓸 경우:
#   add("transformers"); add("tokenizers")

# 동봉 모델: models/easyocr/ (EasyOCR .pth) 와 기타 models/ 전체 포함
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
          console=True, disable_windowed_traceback=False)
coll = COLLECT(exe, a.binaries, a.datas, name="pii_masker")
