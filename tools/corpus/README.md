# English corpus

`generate.py` writes the conversations Wernicke's language area is trained on: one utterance per
line, speakers alternating, a blank line between conversations.

```
python3 tools/corpus/generate.py train.txt 1000000 1
python3 tools/corpus/generate.py eval.txt 3000 999 --no-hand-written
```

Then train, measure on the held-out set and write the shared English file:

```
python3 tools/corpus/soda.py soda_clean.txt soda_eval.txt
CORPUS=train.txt,soda_clean.txt EVAL=soda_eval.txt BITS=23 MIN_COUNT=60 KEEP=48 \
  MODEL_OUT=brain-core/src/main/resources/reallyalive/english.lang.gz \
  ./gradlew -p brain-core test --tests '*EnglishProbeTest' -i
```

The remembered exchanges (`exchanges.bin.gz`) are built from the same files, every n-th pair of lines:

```
CORPUS=soda_clean.txt:12,train.txt:40 EXCHANGES_OUT=brain-core/src/main/resources/reallyalive/exchanges.bin.gz \
  ./gradlew -p brain-core test --tests '*ExchangeMemoryTest' -i
```

Sources: `generate.py` (scripted scenarios and word lists), `trees.py` (branching conversations
where every reply fits its line), `facts.py` (questions and answers about the world), `grammar.py`
(told sentences where verbs only take objects that fit), `chatter.py` (likes, experiences, wishes
and favours over a sorted lexicon), `herenow.py` (what the speaker sees, smells, feels, does and
wants right now, the lines the concept links fill in) and `dialogues.txt` (hand-written conversations). All of these are
written for this project. `soda.py` downloads SODA (Kim et al. 2023, https://huggingface.co/datasets/allenai/soda),
real everyday conversations under CC BY 4.0: anything shipping the trained English must keep that credit.
