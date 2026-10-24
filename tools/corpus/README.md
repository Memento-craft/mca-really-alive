# English corpus

`generate.py` writes the conversations Wernicke's language area is trained on: one utterance per
line, speakers alternating, a blank line between conversations.

```
python3 tools/corpus/generate.py train.txt 1000000 1
python3 tools/corpus/generate.py eval.txt 3000 999 --no-hand-written
```

Then train, measure on the held-out set and write the shared English file:

```
CORPUS=train.txt EVAL=eval.txt TRAIN=1000000 MIN_COUNT=3 \
  MODEL_OUT=brain-core/src/main/resources/reallyalive/english.lang.gz \
  ./gradlew -p brain-core test --tests '*EnglishProbeTest' -i
```

Sources: `generate.py` (scripted scenarios and word lists), `trees.py` (branching conversations
where every reply fits its line), `facts.py` (questions and answers about the world), `grammar.py`
(told sentences where verbs only take objects that fit), `chatter.py` (likes, experiences, wishes
and favours over a sorted lexicon), `herenow.py` (what the speaker sees, smells, feels, does and
wants right now, the lines the concept links fill in) and `dialogues.txt` (hand-written conversations). Everything is
written for this project, so there are no licence questions about the training data.
