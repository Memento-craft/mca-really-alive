# MCA: Really Alive

A NeoForge 1.21.1 addon for Minecraft Comes Alive that gives villagers a learning brain: a real
spiking neural network, wired lobe to lobe following Joshua's notebook design, that sees, smells and
feels the world and moves using exactly a player's controls.

## How the brain works

Each villager has one network of about 5,000 leaky integrate-and-fire neurons (about 3,000 at
birth, growing with age) split into lobes:

| Lobe | Role |
|---|---|
| Occipital | Vision: 16×10 eye rays inside a player's field of view (nearness, solid, hazard, creature) |
| Olfactory | Scents drifting from nearby creatures, food, fire, flowers and water |
| Feely | Touch, motion, balance, health, plus a copy of its own key presses |
| Concept | Combines senses into concepts, in three areas: actions (from Feely), people and places (from sight and smell) |
| Emotion | Innate pleasure, pain and curiosity pools that set the dopamine level |
| Wernicke's | Language: hears 10 kinds of game sound and any word (each word is a pattern over 32 word features), drives the voice (one speech pool per feature), and holds the word-sequence area that knows English |
| Memory | Slow-leaking, fast-learning lobe: each memory binds an action, people and a place, and memories chain to each other |
| Cerebellum | Motor control; also predicts what the body will feel next |
| Motor | One pool of neurons per player control, plus the voice |

Wiring follows page 111: Sensory → Concept and Wernicke's, Occipital → Memory, Concept ↔ Emotion,
Concept ↔ Wernicke's, Concept/Wernicke's/Emotion ↔ Memory, and Concept and Feely → Cerebellum. Connections between lobes keep a map of where things are, like
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
It was trained on a generated corpus of a million everyday conversations and scenes (18 million
words, about 3,000 distinct words; `tools/corpus`): greetings, markets, doctors, inns, jobs, family,
feelings, danger, farming, building, stories, world knowledge and plain chatter, each line in many
phrasings. On conversations it never saw it guesses the next word 74% of the time (counting word
pairs gets 39%). The trained English ships as one shared 8 MB file (`english.lang.gz`); each
villager adds what it hears in its own world in a small sheet of its own. When someone finishes a
line, the villager answers: the word the area expects next drives that word's speech pools, so the
answer comes out word by word through the same spiking voice it babbles with, and it stops when
the area expects the line to end. It always answers a player, and villagers talk among themselves
less with each line so they don't chatter forever. Answers are often apt ("what do bees make?" →
"bees make honey.", "i'm hungry." → "have some hot soup.") and sometimes garbled ("what do you do
for work?" → "i used to hate."): this is a few million counted synapses, not a large language
model, and it does not reason about what it says.

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
