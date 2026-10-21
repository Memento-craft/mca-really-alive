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
| Concept | Combines senses into concepts |
| Emotion | Innate pleasure, pain and curiosity pools that set the dopamine level |
| Wernicke's | Language area (wired in, unused until language arrives) |
| Memory | Slow-leaking recurrent lobe that holds recent state |
| Decision | Between Concept and the Cerebellum |
| Cerebellum | Motor control; also predicts what the body will feel next |
| Motor | One pool of neurons per player control |

Wiring follows page 111: Sensory → Concept and Wernicke's, Occipital → Memory, Concept ↔ Emotion,
Concept ↔ Wernicke's, Concept/Wernicke's/Emotion ↔ Memory, Concept → Decision → Cerebellum, plus
Feely → Cerebellum for balance. Connections between lobes keep a map of where things are, like
the retinotopic maps of a real visual system.

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
speed, sprint rules and air control. It has a player's hitbox for each pose (standing, crouching,
swimming), sneaking stops it at ledges, and it shares step height, gravity, swimming and ladders
with players. Vanilla and MCA path-finding are switched off for that villager while its brain runs.

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
  hitbox and air speed, sneak edge stop) only change anything for villagers whose brain is on.
- Brains attach to anything that is a villager, which includes MCA's villagers.
- Brains run on worker threads; the server thread only reads senses and applies keys.

## Building

```
./gradlew -p brain-core test   # the neural core alone: no Minecraft needed
./gradlew build                # the mod
./gradlew runClient
```

## Status (phase 1)

- The neural core builds and its tests pass: stable firing, reward-learned key presses, decay and
  rebirth, cementing, growth, inheritance, save/load, simultaneous keys.
- The Minecraft side has **not been compiled or run yet**: the environment it was written in could
  not reach the NeoForge maven. Expect some fixes on the first build.
- Learning to turn toward something it sees is **not reliable yet**. The toy-world benchmark
  `MovementLearningTest` is disabled until it passes; that is the next milestone.
