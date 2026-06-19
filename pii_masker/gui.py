"""개인정보 마스킹 도구 — 데스크톱 GUI (Tkinter, 오프라인 동작).

PDF 선택 → 관련자 명단(고소인/피의자 등) 입력 → OCR 엔진 선택 → 마스킹.
결과는 '<원본이름>_masked.pdf'와 감사 리포트(json)로 출력 폴더에 저장되고,
가명 대응표(mapping.json)는 여러 파일에 걸쳐 일관되게 유지된다.

표준 라이브러리만으로 GUI를 구성하므로 추가 의존성이 없다(무거운 OCR/NER
모듈은 '실행' 시에만 import).
"""
import json
import os
import queue
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

ENGINES = ["tesseract", "easyocr", "paddleocr"]
# OCR 정밀도 프리셋 → (render_scale, canvas_size)
PRECISION = {
    "빠름(저메모리)": (1.5, 1024),
    "보통": (2.0, 1600),
    "정밀(느림)": (3.0, 2560),
}


class App:
    def __init__(self, root):
        self.root = root
        root.title("개인정보 마스킹 도구 (오프라인)")
        root.geometry("780x680")
        self.files: list[str] = []
        self.parties: list[dict] = []
        self.logq: queue.Queue = queue.Queue()

        self._build_files(root)
        self._build_parties(root)
        self._build_options(root)
        self._build_run(root)
        self._poll_log()

    # --- 1. 입력 파일 ---------------------------------------------------
    def _build_files(self, root):
        f = ttk.LabelFrame(root, text="1. 마스킹할 PDF 파일")
        f.pack(fill="x", padx=10, pady=6)
        self.file_list = tk.Listbox(f, height=4)
        self.file_list.pack(side="left", fill="both", expand=True, padx=6, pady=6)
        btns = ttk.Frame(f); btns.pack(side="right", padx=6)
        ttk.Button(btns, text="추가", command=self.add_files).pack(fill="x")
        ttk.Button(btns, text="제거", command=self.remove_file).pack(fill="x", pady=4)

    def add_files(self):
        for p in filedialog.askopenfilenames(filetypes=[("PDF", "*.pdf")]):
            if p not in self.files:
                self.files.append(p)
                self.file_list.insert("end", p)

    def remove_file(self):
        for i in reversed(self.file_list.curselection()):
            self.file_list.delete(i)
            del self.files[i]

    # --- 2. 관련자 명단 -------------------------------------------------
    def _build_parties(self, root):
        f = ttk.LabelFrame(root, text="2. 관련자 명단 (가명을 고정할 인물/법인)")
        f.pack(fill="x", padx=10, pady=6)
        form = ttk.Frame(f); form.pack(fill="x", padx=6, pady=4)
        ttk.Label(form, text="가명").grid(row=0, column=0)
        ttk.Label(form, text="이름들(쉼표)").grid(row=0, column=1)
        ttk.Label(form, text="주민번호 등(쉼표)").grid(row=0, column=2)
        self.e_label = ttk.Entry(form, width=12)
        self.e_names = ttk.Entry(form, width=28)
        self.e_ids = ttk.Entry(form, width=24)
        self.e_label.grid(row=1, column=0, padx=2)
        self.e_names.grid(row=1, column=1, padx=2)
        self.e_ids.grid(row=1, column=2, padx=2)
        ttk.Button(form, text="추가", command=self.add_party).grid(row=1, column=3, padx=4)

        self.party_list = tk.Listbox(f, height=4)
        self.party_list.pack(fill="both", expand=True, padx=6, pady=4)
        io = ttk.Frame(f); io.pack(fill="x", padx=6, pady=2)
        ttk.Button(io, text="명단 초안 자동생성", command=self.make_draft).pack(side="left")
        ttk.Button(io, text="명단 불러오기(JSON)", command=self.load_parties).pack(side="left", padx=4)
        ttk.Button(io, text="명단 저장(JSON)", command=self.save_parties).pack(side="left")
        ttk.Button(io, text="선택 제거", command=self.remove_party).pack(side="left", padx=4)

    def add_party(self):
        label = self.e_label.get().strip()
        if not label:
            return
        names = [s.strip() for s in self.e_names.get().split(",") if s.strip()]
        ids = [s.strip() for s in self.e_ids.get().split(",") if s.strip()]
        self.parties.append({"label": label, "names": names, "ids": ids})
        self.party_list.insert("end", f"{label}  |  {', '.join(names)}  |  {', '.join(ids)}")
        self.e_label.delete(0, "end"); self.e_names.delete(0, "end"); self.e_ids.delete(0, "end")

    def remove_party(self):
        for i in reversed(self.party_list.curselection()):
            self.party_list.delete(i)
            del self.parties[i]

    def load_parties(self):
        p = filedialog.askopenfilename(filetypes=[("JSON", "*.json")])
        if not p:
            return
        data = json.loads(Path(p).read_text(encoding="utf-8"))
        for party in data.get("parties", []):
            self.parties.append(party)
            self.party_list.insert("end", f"{party['label']}  |  "
                                   f"{', '.join(party.get('names', []))}  |  "
                                   f"{', '.join(party.get('ids', []))}")

    def make_draft(self):
        if not self.files:
            messagebox.showwarning("확인", "먼저 1번에서 PDF를 추가하세요.")
            return
        if not messagebox.askyesno(
                "명단 초안 자동생성",
                "첫 번째 PDF를 분석해 마스킹 후보(인물·회사) 명단 초안을 만듭니다.\n"
                "· OCR이라 시간이 걸립니다(엔진 tesseract 권장)\n"
                "· 역할(피의자/참고인 등)은 추정값이라 반드시 검토·수정하세요\n\n진행할까요?"):
            return
        self.run_btn.config(state="disabled")
        self.prog.start(12)
        threading.Thread(target=self._draft_worker, daemon=True).start()

    def _draft_worker(self):
        try:
            from masker import Masker
            scale, canvas = self._ocr_params()
            m = Masker(ocr_engine=self.engine.get(), render_scale=scale, canvas_size=canvas)
            self._logmsg(f"명단 초안 분석 중 (engine={self.engine.get()}, 정밀도={self.precision.get()}) ...")
            parties, report = m.suggest_parties(
                self.files[0],
                progress=lambda d, t: self._logmsg(f"  분석 {d}/{t} 페이지..."))
            self._logmsg(report)
            self.root.after(0, lambda: self._load_draft(parties))
        except Exception as e:
            self._logmsg(f"[오류] {type(e).__name__}: {e}")
        finally:
            self.root.after(0, self._done)

    def _load_draft(self, parties):
        self.parties.clear()
        self.party_list.delete(0, "end")
        for p in parties:
            self.parties.append(p)
            self.party_list.insert("end", f"{p['label']}  |  "
                                   f"{', '.join(p.get('names', []))}  |  "
                                   f"{', '.join(p.get('ids', []))}")
        self._logmsg(f"→ 초안 {len(parties)}건을 명단에 넣었습니다. 라벨을 확인하고, "
                     "필요하면 '명단 저장(JSON)'으로 보관 후 메모장에서 수정하세요.")

    def save_parties(self):
        p = filedialog.asksaveasfilename(defaultextension=".json",
                                         filetypes=[("JSON", "*.json")])
        if p:
            Path(p).write_text(json.dumps({"parties": self.parties},
                               ensure_ascii=False, indent=2), encoding="utf-8")

    # --- 3. 옵션 --------------------------------------------------------
    def _build_options(self, root):
        f = ttk.LabelFrame(root, text="3. 옵션")
        f.pack(fill="x", padx=10, pady=6)
        ttk.Label(f, text="OCR 엔진").grid(row=0, column=0, padx=6, pady=4, sticky="w")
        self.engine = tk.StringVar(value="tesseract")
        ttk.OptionMenu(f, self.engine, "tesseract", *ENGINES).grid(row=0, column=1, sticky="w")
        self.use_ner = tk.BooleanVar(value=False)
        ttk.Checkbutton(f, text="NER 보조 탐지(제3자 이름·주소 / 과마스킹 주의)",
                        variable=self.use_ner).grid(row=0, column=2, padx=12, sticky="w")
        ttk.Label(f, text="OCR 정밀도").grid(row=1, column=0, padx=6, pady=4, sticky="w")
        self.precision = tk.StringVar(value="보통")
        ttk.OptionMenu(f, self.precision, "보통", *PRECISION.keys()).grid(row=1, column=1, sticky="w")
        ttk.Label(f, text="(정밀=정확↑·느림·메모리↑ / 빠름=저메모리)").grid(
            row=1, column=2, columnspan=2, sticky="w")
        ttk.Label(f, text="출력 폴더").grid(row=2, column=0, padx=6, pady=4, sticky="w")
        self.outdir = tk.StringVar()
        ttk.Entry(f, textvariable=self.outdir, width=50).grid(row=2, column=1, columnspan=2, sticky="w")
        ttk.Button(f, text="선택", command=self.pick_outdir).grid(row=2, column=3, padx=4)

    def pick_outdir(self):
        d = filedialog.askdirectory()
        if d:
            self.outdir.set(d)

    def _ocr_params(self):
        """선택된 정밀도 → (render_scale, canvas_size)."""
        return PRECISION[self.precision.get()]

    # --- 4. 실행 --------------------------------------------------------
    def _build_run(self, root):
        f = ttk.Frame(root); f.pack(fill="both", expand=True, padx=10, pady=6)
        self.run_btn = ttk.Button(f, text="마스킹 실행", command=self.run)
        self.run_btn.pack(fill="x")
        self.prog = ttk.Progressbar(f, mode="indeterminate")
        self.prog.pack(fill="x", pady=4)
        self.log = tk.Text(f, height=12)
        self.log.pack(fill="both", expand=True)

    def _logmsg(self, s):
        self.logq.put(s)

    def _poll_log(self):
        try:
            while True:
                s = self.logq.get_nowait()
                self.log.insert("end", s + "\n")
                self.log.see("end")
        except queue.Empty:
            pass
        self.root.after(150, self._poll_log)

    def run(self):
        if not self.files:
            messagebox.showwarning("확인", "마스킹할 PDF를 추가하세요.")
            return
        if not self.outdir.get():
            messagebox.showwarning("확인", "출력 폴더를 선택하세요.")
            return
        self.run_btn.config(state="disabled")
        self.prog.start(12)
        threading.Thread(target=self._worker, daemon=True).start()

    def _worker(self):
        try:
            from masker import Masker  # 무거운 import는 실행 시점에
            ner = None
            if self.use_ner.get():
                from ner import KoreanNER
                ner = KoreanNER(backend="spacy", model="ko_core_news_sm")
            outdir = Path(self.outdir.get())
            outdir.mkdir(parents=True, exist_ok=True)
            parties_path = outdir / "_parties.json"
            parties_path.write_text(json.dumps({"parties": self.parties},
                                    ensure_ascii=False, indent=2), encoding="utf-8")
            mapping_path = outdir / "mapping.json"
            scale, canvas = self._ocr_params()
            # 여러 파일에 걸쳐 가명을 일관 유지하려면 Masker를 한 번 만들어 재사용
            masker = Masker(parties_path=str(parties_path), mapping_path=str(mapping_path),
                            ocr_engine=self.engine.get(), ner=ner,
                            render_scale=scale, canvas_size=canvas)
            for f in self.files:
                name = Path(f).stem
                out_pdf = outdir / f"{name}_masked.pdf"
                audit = outdir / f"{name}_audit.json"
                self._logmsg(f"처리 중: {Path(f).name} (engine={self.engine.get()}, 정밀도={self.precision.get()}) ...")
                masker.audit = []
                items = masker.process(
                    f, str(out_pdf), audit_path=str(audit),
                    progress=lambda d, t: self._logmsg(f"  {d}/{t} 페이지..."))
                low = sum(1 for a in items if a.conf < (0.6 if a.conf <= 1 else 60))
                self._logmsg(f"  완료: {len(items)}건 마스킹 (저신뢰 {low}건) → {out_pdf.name}")
            self._logmsg("모든 작업 완료. 가명 대응표: mapping.json")
        except Exception as e:
            self._logmsg(f"[오류] {type(e).__name__}: {e}")
        finally:
            self.root.after(0, self._done)

    def _done(self):
        self.prog.stop()
        self.run_btn.config(state="normal")


def main():
    root = tk.Tk()
    App(root)
    root.mainloop()


if __name__ == "__main__":
    main()
