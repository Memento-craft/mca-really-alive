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
CORPUS=train.txt,soda_clean.txt EVAL=soda_eval.txt BITS=24 MIN_COUNT=10 KEEP=96 MAX_SYNAPSES=96 TEST_HEAP=13g \
  MODEL_OUT=brain-core/src/main/resources/reallyalive/english.lang.gz \
  ./gradlew -p brain-core test --tests '*EnglishProbeTest' -i
```

The remembered exchanges (`exchanges.bin.gz`) are built from the same files, every n-th pair of lines, and every
pair of the hand-written conversations (use absolute paths, the build runs in `brain-core`):

```
CORPUS=soda_clean.txt:3,train.txt:40,tools/corpus/dialogues.txt EXCHANGES_OUT=brain-core/src/main/resources/reallyalive/exchanges.bin.gz \
  ./gradlew -p brain-core test --tests '*ExchangeMemoryTest' -i
```

The topic links (`topics.bin.gz`) are built from SODA alone:

```
CORPUS=soda_clean.txt TOPICS_OUT=brain-core/src/main/resources/reallyalive/topics.bin.gz TEST_HEAP=6g \
  ./gradlew -p brain-core test --tests '*TopicLinksTest' -i
```

And the tone links (`tones.bin.gz`), with an absolute output path:

```
CORPUS=soda_clean.txt TONES_OUT=$PWD/brain-core/src/main/resources/reallyalive/tones.bin.gz TEST_HEAP=6g \
  ./gradlew -p brain-core test --tests '*ToneLinksTest' -i
```

Sources: `generate.py` (scripted scenarios and word lists), `trees.py` (branching conversations
where every reply fits its line), `facts.py` (questions and answers about the world), `grammar.py`
(told sentences where verbs only take objects that fit), `chatter.py` (likes, experiences, wishes
and favours over a sorted lexicon), `herenow.py` (what the speaker sees, smells, feels, does and
wants right now, the lines the concept links fill in) and `dialogues.txt` (hand-written conversations). All of these are
written for this project. `soda.py` downloads SODA (Kim et al. 2023, https://huggingface.co/datasets/allenai/soda),
real everyday conversations under CC BY 4.0: anything shipping the trained English must keep that credit.
