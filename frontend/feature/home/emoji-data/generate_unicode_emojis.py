"""
リアクションのピッカーで出す Unicode の絵文字と、検索に使う名前を Kotlin のソースに書き出す。

使い方:
  curl -sSO https://unicode.org/Public/emoji/<版>/emoji-test.txt
  CLDR の cldr-json から annotations と annotationsDerived の ja / en を取ってくる
  python3 generate_unicode_emojis.py <emoji-test.txt> <ann-ja.json> <der-ja.json> <ann-en.json> <der-en.json> > UnicodeEmojiData.kt
"""
import json
import re
import sys

# 端末のフォントが追いついていない絵文字は豆腐になるので、ここまでの版にする
MAX_EMOJI_VERSION = 15.1
# 肌の色違いは数が多いので出さない
SKIN_TONES = {chr(c) for c in range(0x1F3FB, 0x1F400)}
GROUPS = [
    "Smileys & Emotion",
    "People & Body",
    "Animals & Nature",
    "Food & Drink",
    "Travel & Places",
    "Activities",
    "Objects",
    "Symbols",
    "Flags",
]
# JVM の文字列定数は UTF-8 で 64KB までなので分けて持つ
CHUNK_BYTES = 50000


def annotations(path, key):
    data = json.load(open(path, encoding="utf-8"))
    return data[key]["annotations"]


def terms_of(emoji, *tables):
    terms = []
    for table in tables:
        entry = table.get(emoji) or table.get(emoji.replace("️", ""))
        if entry is None:
            continue
        terms += entry.get("tts", []) + entry.get("default", [])
    seen = []
    for term in terms:
        term = term.strip().lower().replace("|", " ").replace("\t", " ")
        if term and term not in seen:
            seen.append(term)
    return seen


def main():
    test_path, ann_ja, der_ja, ann_en, der_en = sys.argv[1:6]
    ja = [annotations(ann_ja, "annotations"), annotations(der_ja, "annotationsDerived")]
    en = [annotations(ann_en, "annotations"), annotations(der_en, "annotationsDerived")]
    group = None
    lines = []
    pattern = re.compile(r"^[0-9A-F ]+;\s*fully-qualified\s*#\s*(\S+)\s+E(\d+\.\d+)\s")
    for raw in open(test_path, encoding="utf-8"):
        if raw.startswith("# group:"):
            group = raw.split(":", 1)[1].strip()
            continue
        match = pattern.match(raw)
        if match is None or group not in GROUPS:
            continue
        emoji, version = match.group(1), float(match.group(2))
        if version > MAX_EMOJI_VERSION or any(c in SKIN_TONES for c in emoji):
            continue
        terms = terms_of(emoji, *ja) + [t for t in terms_of(emoji, *en) if t not in terms_of(emoji, *ja)]
        lines.append(f"{emoji}\t{GROUPS.index(group)}\t{'|'.join(terms)}")

    chunks = []
    current = []
    size = 0
    for line in lines:
        line_size = len(line.encode("utf-8")) + 1
        if current and size + line_size > CHUNK_BYTES:
            chunks.append(current)
            current, size = [], 0
        current.append(line)
        size += line_size
    chunks.append(current)

    print("package net.matsudamper.kmp.activitypub.frontend.screen.reaction")
    print()
    print("// emoji-data/generate_unicode_emojis.py で作る。手で書き換えない")
    print()
    print("/**")
    print(" * 1 行が 1 つの絵文字で、`絵文字<TAB>分類の番号<TAB>検索に使う名前を | で繋いだもの`。CLDR の並び")
    print(" */")
    print("internal val UNICODE_EMOJI_DATA: List<String> = listOf(")
    for index in range(len(chunks)):
        print(f"    UNICODE_EMOJI_DATA_{index},")
    print(")")
    for index, chunk in enumerate(chunks):
        # 生文字列なので、テンプレートになる $ だけを書き換える
        body = "\n".join(chunk).replace("$", "${'$'}")
        print()
        print(f'private const val UNICODE_EMOJI_DATA_{index}: String = """')
        print(body)
        print('"""')


main()
