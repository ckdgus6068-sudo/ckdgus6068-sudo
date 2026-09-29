#!/usr/bin/env python3
"""
HWPX 네임스페이스 후처리 + 형식 참조 검증 유틸리티

(1) python-hwpx가 생성한 HWPX 파일의 XML 네임스페이스 프리픽스를
    한컴오피스 표준 프리픽스(hh/hc/hp/hs)로 교체한다.
    이 처리를 하지 않으면 한글 Viewer(특히 macOS)에서 문서가 빈 페이지로 보일 수 있다.

(2) 교체 후, 본문(Contents/*.xml)이 참조하는 모든 paraPrIDRef/charPrIDRef 가
    header.xml 에 실제로 정의돼 있는지 검증한다. 끊긴 참조가 하나라도 있으면
    한글에서 서식이 무너지거나 빈 페이지가 나오므로, 조용히 통과시키지 않고
    즉시 오류로 멈춘다(어떤 생성 경로로 만든 파일이든 동작하는 최종 방어선).

사용법:
  CLI:    python fix_namespaces.py <file.hwpx>
          python fix_namespaces.py <file.hwpx> --no-validate   # 검증 생략(권장하지 않음)
  Import: exec(open("fix_namespaces.py").read())
          fix_hwpx_namespaces("output.hwpx")     # 교체 + 검증
          validate_hwpx_references("output.hwpx") # 검증만
"""

import zipfile
import os
import re
import sys


NS_MAP = {
    "http://www.hancom.co.kr/hwpml/2011/head": "hh",
    "http://www.hancom.co.kr/hwpml/2011/core": "hc",
    "http://www.hancom.co.kr/hwpml/2011/paragraph": "hp",
    "http://www.hancom.co.kr/hwpml/2011/section": "hs",
}


class ReferenceError(ValueError):
    """본문이 header에 없는 paraPr/charPr 를 참조할 때(=빈 페이지 원인)."""


# HWPML 에서 "참조 없음"을 뜻하는 센티넬 값(0xFFFFFFFF). 정의가 없어도 정상.
_SENTINEL = {"4294967295"}
_SECTION_RE = re.compile(r"Contents/section\d+\.xml$")


def validate_hwpx_references(hwpx_path):
    """본문(section)이 참조하는 모든 paraPr/charPr 가 header.xml 에 정의돼 있는지 검증한다.

    끊긴 참조가 있으면 ReferenceError 를 던진다. 정상이면 조용히 통과한다.
    HWPX 포맷의 불변식(본문이 참조하는 형식 ID는 반드시 header에 정의)을 강제하므로
    모든 종류의 HWPX 에 안전하게 적용된다.
    """
    with zipfile.ZipFile(hwpx_path, "r") as z:
        names = z.namelist()
        header_name = next(
            (n for n in names if n.endswith("header.xml") and n.startswith("Contents/")),
            None,
        )
        section_names = [n for n in names if _SECTION_RE.match(n)]
        if header_name is None or not section_names:
            # header 또는 section 이 없는 패키지는 검증 대상이 아님
            return
        header = z.read(header_name).decode("utf-8")
        para_defs = set(re.findall(r'<\w*:?paraPr\s+id="(\d+)"', header))
        char_defs = set(re.findall(r'<\w*:?charPr\s+id="(\d+)"', header))

        para_refs, char_refs = set(), set()
        for n in section_names:
            body = z.read(n).decode("utf-8")
            para_refs |= set(re.findall(r'paraPrIDRef="(\d+)"', body))
            char_refs |= set(re.findall(r'charPrIDRef="(\d+)"', body))

    miss_p = para_refs - para_defs - _SENTINEL
    miss_c = char_refs - char_defs - _SENTINEL
    if miss_p or miss_c:
        raise ReferenceError(
            "[참조 검증 실패] header.xml 에 정의되지 않은 형식을 본문이 참조합니다 "
            "(이대로 두면 한글에서 서식이 깨지거나 빈 페이지). "
            f"없는 paraPr={sorted(miss_p, key=int)}, 없는 charPr={sorted(miss_c, key=int)}. "
            "양식(header.xml)을 보존한 채 본문만 채웠는지 확인하세요. "
            "빈 문서로 새로 조립하지 말고 양식 zip을 복사해 사용해야 합니다."
        )


def fix_hwpx_namespaces(hwpx_path, validate=True):
    """
    HWPX 파일의 ns0:/ns1: 등 자동 생성 프리픽스를
    한컴오피스 표준 프리픽스(hh/hc/hp/hs)로 교체한다.

    Args:
        hwpx_path: 수정할 .hwpx 파일 경로
        validate: True면 교체 후 paraPr/charPr 참조 무결성을 검증한다(기본값).
    """
    tmp_path = hwpx_path + ".tmp"

    with zipfile.ZipFile(hwpx_path, "r") as zin:
        with zipfile.ZipFile(tmp_path, "w", zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                data = zin.read(item.filename)

                if item.filename.startswith("Contents/") and item.filename.endswith(".xml"):
                    text = data.decode("utf-8")

                    ns_aliases = {}
                    for match in re.finditer(r'xmlns:(ns\d+)="([^"]+)"', text):
                        alias, uri = match.group(1), match.group(2)
                        if uri in NS_MAP:
                            ns_aliases[alias] = NS_MAP[uri]

                    for old_prefix, new_prefix in ns_aliases.items():
                        text = text.replace(f"xmlns:{old_prefix}=", f"xmlns:{new_prefix}=")
                        text = text.replace(f"<{old_prefix}:", f"<{new_prefix}:")
                        text = text.replace(f"</{old_prefix}:", f"</{new_prefix}:")

                    data = text.encode("utf-8")

                zout.writestr(item, data)

    os.replace(tmp_path, hwpx_path)

    if validate:
        validate_hwpx_references(hwpx_path)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    do_validate = "--no-validate" not in sys.argv

    if len(args) != 1:
        print("Usage: python fix_namespaces.py <file.hwpx> [--no-validate]")
        print("  Fixes namespace prefixes for Hangul Viewer compatibility,")
        print("  then validates that every paraPr/charPr reference exists in header.xml.")
        sys.exit(1)

    path = args[0]
    if not os.path.exists(path):
        print(f"Error: File not found: {path}")
        sys.exit(1)

    try:
        fix_hwpx_namespaces(path, validate=do_validate)
    except ReferenceError as e:
        # 빈 페이지가 될 파일을 그대로 통과시키지 않는다.
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(2)

    print(f"Fixed namespaces: {path}")
    if do_validate:
        print("Reference check: OK (모든 paraPr/charPr 참조가 header에 존재)")
