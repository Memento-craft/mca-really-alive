# MCA: Really Alive

A NeoForge 1.21.1 addon for Minecraft Comes Alive that gives villagers a learning brain: a real
spiking neural network, wired lobe to lobe following Joshua's notebook design, that sees, smells and
feels the world and moves using exactly a player's controls.

## How the brain works

Each villager has one network of about 5,000 leaky integrate-and-fire neurons (about 3,000 at
birth, growing with age) split into lobes:

| Lobe | Role |
|---|---|
| Occipital | Vision: 16×10 eye rays inside a player's field of view (nearness, solid, hazard, creature, climbable), plus the colour of what each ray hits at half that resolution |
| Olfactory | Scents drifting from nearby creatures, food, fire, flowers and water |
| Feely | Touch, motion, balance, health, plus a copy of its own key presses |
| Concept | Combines senses into concepts, in three areas: actions (from Feely), people and places (from sight and smell) |
| Frontal | The frontal part of the notebook's frontal/temporal concept system: gathers concepts, feelings, memories and words, holds them a little longer, and feeds back onto the concepts (attention) |
| Emotion | Innate pleasure, pain and curiosity pools that set the dopamine level |
| Wernicke's | Language: hears 10 kinds of game sound and any word (each word is a pattern over 32 word features), drives the voice (one speech pool per feature), and holds the word-sequence area that knows English |
| Memory | Slow-leaking, fast-learning lobe: each memory binds an action, people and a place, and memories chain to each other |
| Cerebellum | Motor control; also predicts what the body will feel next |
| Motor | One pool of neurons per player control, plus the voice |

Wiring follows page 111: Sensory → Concept and Wernicke's, Occipital → Memory, Concept ↔ Emotion,
Concept ↔ Wernicke's, Concept/Wernicke's/Emotion ↔ Memory, and Concept and Feely → Cerebellum. The frontal
part takes input from Concept and Emotion and loops with Concept, Memory and Wernicke's; it does not
drive the cerebellum (there is still no Decision lobe). Connections between lobes keep a map of where things are, like
the retinotopic maps of a real visual system. The memory layout (page 111, left) links each memory
across all three Concept areas and to the cerebellum's skills, so part of a scene recalls the rest:
in `MemoryRecallTest`, a person's smell alone brings to mind the place they are always found.
Villagers learn to talk the way children do: they babble words, hear themselves, and so learn to
say back a word they hear someone say (`SpeechImitationTest`: 21% of tries, 0% for a brain that
never hears its own voice). Because words are patterns over shared features, learning to repeat
some words carries over to every other word.

They also grow up with English. Wernicke's word-sequence area (`LanguageArea`) learns, by Hebbian
co-activity alone, which word comes next: context cells stand for the last one to four words and
for the words of the line it is answering, and each grows synapses onto the word cells that follow.
It was trained on two sources together: a generated corpus of a million everyday conversations and
scenes (`tools/corpus`): greetings, markets, doctors, inns, jobs, family, feelings, danger, farming,
building, stories, world knowledge and talk about what one sees, smells, feels, does and wants; and
about a million real everyday conversations from SODA (Kim et al. 2023, "SODA: Million-scale
Dialogue Distillation with Social Commonsense Contextualization", allenai/soda, CC BY 4.0), keeping
only conversations whose words all occur at least 50 times (25,000 distinct words). Context cells
now reach back five words and also to the line before last, over 16 million context cells with up
to 96 synapses each. On SODA conversations it never saw it guesses the next word 44.5% of the time
(the earlier generated-only English got 17%), and 72% on held-out generated ones. The trained
English ships as one shared 146 MB file (`english.lang.gz`, synapses seen fewer than 10 times
pruned, so rare words such as "zombie" keep their place after common contexts) and takes about
470 MB of server memory, shared by all villagers; each
villager adds what it hears in its own world in a small sheet of its own. When someone finishes a
line, the villager answers: the word the area expects next drives that word's speech pools, so the
answer comes out word by word through the same spiking voice it babbles with, and it stops when
the area expects the line to end. It always answers a player, and villagers talk among themselves
less with each line so they don't chatter forever. Answers are often apt ("what do bees make?" →
"bees make honey.", "i'm hungry." → "have some hot soup.") and sometimes garbled ("what do you do
for work?" → "i used to hate."): this is a few million counted synapses, not a large language
model.

Before answering, the frontal part thinks the reply through: it tries 24 ways of opening, follows
each to the end of a line, and keeps the line that comes most naturally as an answer to what was
said (rather than as something one could say to anything), says what the villager perceives, and
was not said a moment ago. Villagers also remember exchanges, the way the hippocampus keeps
episodes (`ExchangeMemory`, 41 MB, about 1.4 million exchanges from the same corpora, with names left out):
each remembered answer is linked to the words of the line it answered (rare words strongly, small words faintly), so hearing a line
brings up answers people gave to lines like it ("watch out! zombies!" brings up "i'll get my
sword.", "stay behind me."), and those are weighed next to the lines it makes up. Playing one side
of 120 real talks it never saw, the share of stock answers fell from 68% to 19%.
Answers also keep to what was said (`TopicLinks`, 1.3 MB): Hebbian links between the telling words
of a line and those of the answers that followed it in SODA ("toys" goes with "dolls", "legos";
"rain" with "umbrella", "soaked"), so a line whose words go with what it heard is preferred. Asked
"what flavor was it?" a villager now says "chocolate chip mint." rather than a name; judged blind
on 25 changed answers to those real talks, the new answer was better 14 times, worse 4.
The tone of an answer has to fit too (`ToneLinks`, 1.3 MB): among the 3,000 commonest words, how
much more or less often a word of a line and a word of its answer come together than by chance,
so "my grandma passed away" is answered with "sorry" rather than "that's great!", and "thank you"
with "you're welcome". The last sentence of what was heard counts twice. Judged blind on 24
changed answers, better 11 times, worse 2.
Villagers also remember what they were told earlier in a talk. Asked a short question about the
speaker ("what's my dog's name?"), the line the speaker said about themselves lately that shares
the most telling words with it ("i have a dog named rex.") comes back, its words are held in mind,
and it can be said back the other way round: "you have a dog named rex.", "you live in the big
house by the river.", "your favourite colour is green.". Asked why, the line told just after comes
back: "i'm sad today." "my cat ran away." ... "why am i sad?" "because your cat ran away." (10 of 10
such questions, 0 before).
Each villager also keeps exchanges of its own (the last 256, saved with it): what it answered
itself, so asked the same thing again, even days later, it gives the same answer ("i work as a
mason."), what others answered its lines, and what it overheard players say to each other. Hear
"what is the password to the vault?" answered with "the password is pumpkin.", and asked it later,
the villager says "the password is pumpkin."
The game also tells each villager who it is (`SelfKnowledge`): the name MCA gave it, its
profession, and whether it is still a child, so asked "what's your name?" an MCA villager says "my
name is misu." and asked about work, "i'm a farmer."

What villagers say is tied to what they perceive (`ConceptLinks`). Every word cell has Hebbian
links to the meaning cells of the concept system: the Concept lobe, helped by the frontal part,
Memory and Emotion. When someone names a thing (the game does this now and then while a player or
villager is near, like a parent saying "a cow!"; config `namingThings`), or a word is overheard in
talk, the word's links move toward how far each meaning cell is from its usual activity at that
moment: the covariance form of Hebb's rule. Over many moments a word ends up linked to what was
there every time, and small words heard everywhere ("you", "the") end up meaning nothing. The links
work both ways: hearing "zombie" stirs the cells seeing one does, and while answering, the language
area frames the line ("i see a ...") while the links pick the word whose meaning is present now.
The frontal part plans a word ahead, so "a" beats "you" after "i see" when there is a zombie to
name. In `GroundedSpeechTest`, villagers who had things named answer "what do you see?", "what do
you smell?" and "how do you feel?" with the right thing about three times in four ("a ladder.",
"i smell flowers.", "i feel very hurt."), against never for villagers who heard nothing named.
A word that fits the moment but was not the one named also learns what it is not (links against
the cells most stirred then), which with colour vision lets cows and zombies come apart.

The frontal part also holds in mind what was just there: how present each word's meaning was a
moment ago fades over a few seconds, and is pushed out sooner by whatever else comes up. Seen cells
quiet down (they even dip below usual) as soon as a thing walks out of view, so without this a
villager asked "what did you see?" two seconds later named it once in 20 tries; with it, 16 in 20.
And villagers speak up on their own: when something new comes up strongly and someone is within a
few blocks, the frontal part thinks through a few ways of opening a line, keeps the most natural one
that gets to what it noticed within five words, and says it ("look, a zombie!", "is fire hot?").
If no line would, it says nothing, and alone it never speaks up.

Learning happens live, in game:

- **Hebbian plasticity (STDP)** inside the lobes: neurons that fire together wire together.
- **Reward learning** from the Cerebellum to the Motor pools: dopamine from the Emotion lobe keeps
  key presses that turned out well and undoes ones that didn't.
- **Decay and rebirth**: a neuron that fires far too little or too much loses health; at zero its
  connections are deleted and it rewires toward quiet, under-connected neurons. Neurons that often
  fire together in a healthy way cement and decay more slowly.
- **Growth**: new neurons are added to the most active lobes as a villager grows up, shaped by
  genes inherited from both parents.
- **Motor babbling**: young villagers press keys at random, less as they mature, so they can find
  out what their body does.

## Moving exactly like a player

The output is never one chosen action. Every tick each motor pool is read and decoded into the
full set of player inputs at once: W/A/S/D, jump, sneak, sprint and mouse look. A brain-driven
villager then moves through the same physics a player does. It has a player's walk speed, sneak
speed, sprint rules and air control, sneaking stops it at ledges, and it shares step height,
gravity, swimming and ladders with players. It keeps its own MCA size (villagers vary in height
and width), and crouching or swimming shrinks it by the same proportions they shrink a player. Vanilla and MCA path-finding are switched off for that villager while its brain runs.

## Using it

Brains are opt-in per villager unless `autoEnable` is set in the server config.

```
/reallyalive brain enable @e[type=villager,distance=..10]
/reallyalive brain info <villager>
/reallyalive brain babble <villagers> <0..4>     # how much it flails; 0 shows only what it learned
/reallyalive goal <villagers> <pos>              # something it can smell and enjoys reaching
/reallyalive watch <villager>                    # its keys and lobe activity above your hotbar
/reallyalive brain disable <villagers>           # hand it back to MCA's normal AI
```

Brains are saved to `<world>/reallyalive/brains/<uuid>.brain`, not in chunk data.

## Compatibility

- It never replaces MCA or vanilla classes. Three small mixins (move/look control access, player
  pose sizes and air speed, sneak edge stop) only change anything for villagers whose brain is on.
- Brains attach to anything that is a villager, which includes MCA's villagers.
- Brains run on worker threads; the server thread only reads senses and applies keys.

## Building

The trained files in `brain-core/src/main/resources/reallyalive/` are stored with Git LFS, so
install it before cloning (`git lfs install`), or run `git lfs pull` in a clone made without it.

```
./gradlew -p brain-core test   # the neural core alone: no Minecraft needed
./gradlew build                # the mod
./gradlew runGameTestServer    # in-game tests with MCA Reborn loaded
./gradlew runClient            # dev client, with MCA Reborn
```

Dev runs pull MCA Reborn (NeoForge 1.21.1) from Modrinth's maven; the addon's jar does not depend
on it. The game tests spawn MCA villagers and check that the brain takes over and hands back their
body, that MCA's own AI cannot move them while it runs, that the brain's own keys move them, and
that holding the same keys as a player (walk, sprint, sprint-jump, sneak up to a ledge) moves them
exactly as far as the player on every tick.

`SensesTests` check the eyes in game: a wall fills the view at the right distance, things on the
left are seen on the left, cows are creatures and zombies dangerous ones, lava is a hazard and
water a liquid, and seeing a wall stirs the occipital lobe far more than open sky. Blocks a player
cannot see (barriers, light blocks) are looked through.

`HandsTests` check the two mouse buttons: holding attack digs dirt by hand in 15 ticks and stone
with a wooden pickaxe in 23, exactly a player's times, letting go loses the progress, and the drop
goes to the villager's hand or inventory; a click hits the creature in the crosshair for a fist's 1
damage; use places one block per click and another every 4 ticks while held, never inside a
creature. They also check the hand senses and that a ladder is seen as climbable but the wall
beside it is not.

`LearningTests` are obstacle courses with a still cow standing on the goal: open ground (the
control), a one-block step, a ladder up a wall, a pool, and a ledge to sneak up to. Each course has
a required test where a villager holding a player's keys gets through, and an optional benchmark
where a newborn villager's own brain tries 150 times. The benchmarks run the server at sprint
speed with the brain kept in lockstep (one step per tick, as in real time) and log how many of the
first and last 10 tries reached the goal. The step course (a two-high ledge, 16 dirt in hand)
can only be climbed by placing a step, and also reports how often place is pressed.

`SpeechTests` check hearing and the voice. Game sounds are sorted into ten kinds (steps, villager
and player voices, hostile mobs, animals, digging, building, doors, water, bells) and heard louder
near than far. Words typed in chat are heard within 16 blocks, followed by the end of the line
together with the sound of a player's voice. A villager's words are heard by itself (as its own)
and by villagers near it as it says them, and when it ends a line the line shows in nearby players'
chat as `<name> how is it going?`. Optional benchmarks let a newborn babble for 20k ticks and count
how often it says back a word said to it, and log what an adult answers to "hello.".

## Status (phase 1)

- The neural core builds and its tests pass: stable firing, reward-learned key presses, decay and
  rebirth, cementing, growth, inheritance, save/load, simultaneous keys.
- The mod builds and runs with MCA Reborn 7.7.36 on NeoForge 21.1.264, and the game tests pass.
  MCA's inherited speed gene is ignored while a brain drives a villager, so it moves at exactly a
  player's speed. Not yet tried in a client by hand.
- Newborns have two inborn reflexes that learning builds on: keep the head level, and turn toward a
  creature they see. Dopamine is a reward prediction error from a small critic (Concept, Emotion
  and Memory activity), so only "better than expected" reinforces a key.
- In-game benchmarks (one run, brain from 3269660 with the climbable sight channel; results
  vary between runs), first 10 → last 10 tries, newborn / adult: open ground 1→10 / 0→10, jump
  the step 1→10 / 2→10, swim the pool 2→9 / 5→10, ledge 5→9 / 8→10 with no falls (sneaking
  10–15% near the edge; that is only reported), ladder 0→0 / 0→1 (on the ladder 0–1% of the
  time; it jumps at the wall beside the 1-wide ladder).
- Toy-world benchmark `MovementLearningTest`: after 30k ticks, brains that feel good about nearing
  a goal they see reach about 9 goals per 3000 ticks (all 6 seeds); unrewarded brains reach none.
