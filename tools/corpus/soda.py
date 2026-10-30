"""Real everyday conversations for the English base, from SODA (allenai/soda, CC BY 4.0).

SODA: Kim et al., "SODA: Million-scale Dialogue Distillation with Social Commonsense
Contextualization", 2023. https://huggingface.co/datasets/allenai/soda

Downloads the parquet files, writes one line per turn with a blank line between conversations,
and drops conversations that use words seen fewer than MIN_COUNT times in the whole set (mostly
rare names), which keeps the vocabulary near 25,000 words.

    pip install pyarrow
    python3 tools/corpus/soda.py soda_train.txt soda_eval.txt
"""
import collections
import re
import sys
import urllib.request

import pyarrow.parquet as pq

URL = "https://huggingface.co/datasets/allenai/soda/resolve/main/{}.parquet"
MIN_COUNT = 50
TOKEN = re.compile(r"[a-z0-9][a-z0-9']*|[.?!,]")
QUOTES = str.maketrans({"’": "'", "‘": "'", "“": '"', "”": '"'})


def dialogues(split):
    path = f"soda_{split}.parquet"
    urllib.request.urlretrieve(URL.format(split), path)
    for d in pq.read_table(path, columns=["dialogue"]).column("dialogue").to_pylist():
        lines = [re.sub(r"\s+", " ", l).translate(QUOTES).strip() for l in d if l and l.strip()]
        if len(lines) >= 2:
            yield lines


def main(train_out, eval_out):
    train = list(dialogues("train"))
    counts = collections.Counter(w for d in train for l in d for w in TOKEN.findall(l.lower()))

    def common(d):
        return all(counts[w] >= MIN_COUNT for l in d for w in TOKEN.findall(l.lower()))

    for out, ds in ((train_out, train), (eval_out, list(dialogues("test"))[:3000])):
        with open(out, "w") as f:
            for d in ds:
                if common(d):
                    f.write("\n".join(d) + "\n\n")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
