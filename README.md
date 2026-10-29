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
It works the same for what anyone says about anything, in sentences it never heard before:
told "a strange man in a purple coat arrived at the inn." and then something else, and asked
"what was the man wearing?", it answers "a strange man in a purple coat arrived at the inn.". The
question has to be mostly about what the line said, counting word forms ("dried", "dry") and
words that go together across a turn by the topic links ("wearing", "coat"), words that stand
for like things because they bring up the same answers ("wedding" for "married"), and names
("who did pedro marry?"), and to share at least one telling word with it, so ordinary chat
doesn't bring old lines back. On 20 new sentences each asked about after other talk it answers
19 (3 before), and 20 of 20 on each of two more sets written afterwards; asked the same
questions without being told, it makes up an answer that happens to be right once in 60.
Remembered answers also keep track of whether they answered a question or a telling, so news
("the bridge fell down yesterday.") is no longer answered as "what is a bridge?" was.
It also reasons over what it was told. Asked a yes or no question, it says "yes ," with the line
that says everything asked, and "no ," when that line says the opposite ("there is no bread.") or
the same thing the other way round: told "the mill is older than the church.", asked "is the
church older than the mill?" it says "no , the mill is older than the church.". It never makes up
a no from a line that only shares words. It chains up to four lines that share a thing, so told
"tom is taller than ben." and "ben is taller than sam.", asked "is tom taller than sam?" it says
yes, and the same for in, on, under, before, after, north of and so on. It picks out the most
("who is the oldest?" from several "older than" lines, "youngest" by reading them the other way
round), counts ("i had five apples." "i ate two." "how many apples do i have?" "three."), and
works out days ("today is monday." "what day is tomorrow?" "tuesday."). On 20 new questions of
each kind: yes or no 19 right, 0 wrong; chains 20 of 20 on three sets; most, counting and days
20 of 20 on two sets. It remembers the last 15 lines of a talk, so a fact told ten lines earlier
still comes back (19 of 20, 1 before), and a greeting word ("hey boss,") is not taken for part
of a question. Asked a comparison it was never told about ("is a horse bigger than a mouse?"), it
weighs how likely its language area finds the sentence and the same sentence the other way round,
and answers with the likelier one when it is clearly likelier: 19 of 20 right, 0 wrong (6 right
and 4 wrong before). Remembered yes or no answers are turned round to fit the question, and
dropped when they are about other things.
It keeps track of what changed: told "mary went to the kitchen." and then "mary went to the
garden.", asked "where is mary?" it says the garden; "tom gave the key to anna." makes "anna has
the key ."; "then it jumped onto the wall." after "the cat was on the roof." is said as "then the
cat jumped onto the wall ."; "i closed the door." answers "is the door open?" with no. On 20 such
talks it gets 18 right and none wrong (5 right and 10 wrong before), and 20 of 20 on a set written
afterwards. A common name ("john", "paris") now counts as a name.
It works out sums ("what is 12 plus 9?" "12 plus 9 is 21 ."; minus, times, divided by, take away)
and what comes before or after a day, a month or a number ("what comes before monday?" "sunday
comes before monday ."). On 20 everyday questions written afterwards (sums, what comes next,
opposites, colours, how many legs) it answers 16, 8 before; what it gets wrong now are things it
would have to know of the world, such as how many wheels a bike has.
What it is told is also kept with its own saved exchanges, whole, as an exchange the line
answered itself (which ordinary recall leaves alone), so it is remembered after much other talk
and after the world is saved and loaded: told five things among 30 other lines, saved, loaded,
and asked after ten more, it answers 18 of 20 (1 before) and 20 of 20 on another set. A line told
that long ago has to be mostly about what is asked, even when the question is about the speaker,
since someone else may have said it.
It works out family ties from what it was told, lately or long ago: each tie both ways round
("anna is ben's mother." answers "who is anna's son?" with "ben is anna's son ."), a parent's
parent as a grandparent, a sibling's parents as one's own, and "my" as the speaker ("the priest is
my uncle." "who is the priest's nephew?" "you are the priest's nephew ."). 19 of 20 (3 before),
20 of 20 on a set written afterwards, none wrong.
The game also tells each villager its own family from MCA's family tree (parents, grandparents,
partner, children, siblings, by name), so asked "who is your mother?" it says "rosa is my mother
.", asked "who is lena's husband?" it says "i am lena's husband .", and it works out more from
what it is told ("ana is rosa's mother." "who is your grandmother?" "ana is my grandmother ."). MCA
is reached by reflection, so without it a villager just has no family to tell of.
Every 10 seconds the game also tells it the time of day, the weather where it stands and the land
it lives in, by biome: "what time is it?" "it's evening .", "is it raining?" "yes , it's raining .",
"where do you live?" "i live in the snowy taiga .". These answer questions only, not tellings like
"the weather is nice today.".
It is told what it holds and carries too: "what are you holding?" "i'm holding an iron hoe .", "what
do you have?" "i have three bread , twelve wheat seeds and an apple .", "do you have any carrots?"
"no , i don't have any carrots .". It only says no about things that are game items, so "do you
have a minute?" is answered as before.
And it is told the creatures it can see within 16 blocks: "what do you see?" "i see two cows , a pig
and four sheep .", "how many cows are there?" "there are two cows .", "are there any zombies
around?" "no , i don't see any zombies .".
It knows how things are made, from the game's own crafting and smelting recipes: "how do i make
bread?" "to make bread , you need three wheat .", "how can i get iron ingots?" "to get iron ingots ,
smelt raw iron in a furnace .".
It answers why and what-if from what it was told: told "if it rains, the field floods." it answers
"what happens if it rains?" with "the field floods .", and told also "it rained all night." it
answers "why did the field flood?" with "because it rained all night .". A cause told on the next
line counts too ("the road is closed." "a tree fell on it." gives "because a tree fell on it .").
It follows a short question that leans on the one before, as people do: after "where is mary?",
"and john?" is answered with "john is in the garden .", after "is mary in the kitchen?" with "no ,
john is in the garden .", and after "what does ivan own?", "where does he keep it?" with "he keeps
it in the stable .".
It knows what things are like from what it was told: told "pia has a red hat." it answers "what
colour is pia's hat?", knows that "is the axe sharp?" is "no , the axe is dull ." (it knows the
opposites of common qualities), and that "whose garden is bigger?" is answered by "hilda has a big
garden .".
It keeps the order of what it was told: after "tom fed the pigs." "then he milked the cow." "then
he went to the market.", it answers "what did tom do first?" ("tom fed the pigs ."), "what did tom
do after he fed the pigs?" ("he milked the cow .") and "did tom go to the market first?" ("no ,
tom fed the pigs first ."), also when the player tells of themselves ("what did i do last?" "you
went to the market .") and after other questions in between.
It compares amounts, days and months: after "tom has three apples." "anna has five apples.", "who
has more apples?" is "anna has five apples ."; "how many more coins does lou have?" after "pia has
4 coins." "lou has 9 coins." is "lou has five more coins ."; "which comes first, friday or sunday?"
is "friday comes first ."; and "is four less than two?" is "no , four is more than two .".
Every villager also knows what anyone learns growing up, from a shipped list of plain facts
(`knowledge.txt`, about 300 lines): "how many legs does a spider have?" "a spider has eight legs .",
"what is glass made from?" "glass is made from sand .", and of its own world, "do zombies burn in the
sun?" "yes , zombies burn in the sun .". These are recalled like lines told long ago, but only when
nothing it was told answers, and never for questions about the one talking or about now ("is it
raining?"). A fact with a word the villager has never heard ("enderman") comes to mind once someone
says it.
It reasons from what kind of thing something is: told "rex is a dog.", it answers "how many legs
does rex have?" with "a dog has four legs .", and told "the monster by the gate is a skeleton.",
"does it burn in the sun?" with "yes , skeletons burn in the sun .". Told "i keep bees.", "what
do my bees make?" is "a bee makes honey .", and told "i run the bakery.", "what do i do?" is "you
run the bakery .".
It reasons over more than one step: "the key is in the box." "the box is in the barn." answers
"where is the key?" with both; "tom is taller than sam." "sam is taller than ben." answers "who
is taller, tom or ben?" with "tom is taller than ben ."; "tom , anna and ben went to the market."
answers "how many people went?" with three; "if it rains, we stay home." and "it is raining."
answer "what do we do?" with "we stay home ."; "no zombie can swim." makes the zombie in the river
unable to swim; "the rat took the cheese." moves the cheese. It does not take "the gates opened."
with "when the horn sounds, the gates open." as proof that the horn sounded: it says it doesn't
know.
It has everyday common sense from the shipped facts: "is a carrot a fruit?" "no , a carrot is a
vegetable , not a fruit ."; "which one is not a fruit, an apple, a pear or a stone?" "a stone is
not a fruit ."; "how do i get iron?" "to get iron , you mine iron ore with a stone pickaxe .";
"what should i do if i am cold?" "you should sit by a fire ."; and "what is bigger, a house or a
mouse?" "a house is bigger than a mouse .". A fact only answers if it gives what is asked.
It judges whether it is safe out from what the game tells it: "is it safe to go outside?" at night
is "no , it's night , and monsters come out at night ."; with zombies in sight, "no , there are two
zombies around ." (it knows from the facts which creatures are dangerous); in a storm, "no , there's
a storm ."; otherwise "yes , it looks safe .".
It says how it feels and why, from its body and what it sees: "how do you feel?" when its health
is low is "i'm hurt , i need to rest ."; with a monster in sight, "i'm scared , there are two zombies
around ."; at night, "i'm tired , it's late at night ."; "why are you scared?" "because there are
two skeletons around .". "how are you?" gets its usual friendly reply unless something is wrong.
Not heard, it says it again: "what?" repeats its last line, and "what did you say?" gets "i said
...".
It works out who "he", "she", "they" and "it" stand for from the latest line that tells of such a
one: "tom went to the mill." "where is he?" is "where is tom?"; "the guards are at the gate."
"where are they?" gives "the guards are at the gate .". With no one told of at all, it asks:
"where is he?" gets "who do you mean ?" and "what does it do?" gets "what do you mean ?"; an "it"
that stands for nothing ("what time is it?", "is it raining?") is answered as before.
It generalises from cases told alike: after "tom's goat is brown.", "ben's goat is brown." and
"ana's goat is brown.", "what colour are goats?" is "each goat i heard of is brown ."; "the miller
woke early on monday / tuesday / wednesday." makes "when does the miller wake up?" "the miller woke
early each day i heard of ."; one case against it is named ("are all goats brown?" "no , ben's goat
is white ."; "are the stones heavy?" "most were , but the third stone was light .").
It reasons from rules told of a kind: "all blicks are furry." "zim is a blick." "is zim furry?"
gives "yes , all blicks are furry , and zim is a blick ."; "no glorps can fly." makes "can pip fly?"
"no"; "all snorks live in caves." makes "where does ari live?" "ari lives in caves ."; rules chain
("all fips are blue." "all blue things are cold." "is kip cold?" "yes"). It won't draw what doesn't
follow: "mo is not a dax ." gets "i don't know , all dax are friendly , but mo is not a dax ."; "some
vims are green" gets "maybe ..."; and "all mogs are tall." "tess is tall." "is tess a mog?" gets "i
don't know , tess is tall , but not all tall things are mogs .".
It works out where things lie from each other: "the well is north of the mill." "the mill is north
of the church." "is the church south of the well?" gives "yes , the well is north of the mill , and
the mill is north of the church ."; it turns told lines round ("the lake is west of the farm."
"where is the farm?" "the farm is east of the lake ."), names the furthest one ("which is furthest
north?"), and handles east/west, left/right, above/below/under and next to. When the lines don't
settle it, it says "i don't know" and what it was told.
It notices when it is told something that goes against what it was told before: "the cart is red."
then "the cart is blue." gets "wait , you said the cart is red ."; so do two counts ("tom has three
apples." "tom has five apples."), two names, two kinds ("tom is a baker." "tom is a farmer."), a line
and its "not", and a comparison turned round. Moving somewhere or a state that changes ("the gate is
open." "the gate is closed.") is not taken as a contradiction.
It answers what it is asked to suppose: "if it is night, should i go outside?" "no , not at night ,
monsters come out at night ."; "if there are zombies, is it safe outside?" "no , zombies are
dangerous .". "sara is taller than tim." "who is shorter?" gives "tim is shorter than sara ."; one
line told in steps ("i woke up, then i ate breakfast, then i went to work.") answers "what did i do
after breakfast?" with "you went to work ."; and "is a carrot a fruit or a vegetable?" is answered
without a "yes".
Counts told against another's work in either order ("tom has twice as many sheep as ben." "ben has
four sheep." "how many sheep does tom have?" "tom has eight sheep ."), and "how many sheep do they
have together?" adds them up.
It reasons about what it can and can't do: "a sword costs ten emeralds." "i have six emeralds." "can i
buy a sword?" gives "no , a sword costs ten emeralds , and you only have six ."; "how many more
emeralds do i need?" "you need four more emeralds ."; "the path to the mine is blocked." "can i get to
the mine by the path?" "no , the path to the mine is blocked ."; "why do we need wood?" after "we need
wood to fix the roof." is "to fix the roof ."; and "i had twelve arrows." "i shot five of them." leaves
seven.
It works with numbers told in a talk (`NumberSense`): "lisa is 8." "her brother is 3 years
older." gives "lisa's brother is eleven ."; "i have 3 cows and 4 pigs." gives "you have seven
animals ."; "kai has 5 apples." "mia has twice as many." gives ten; "a loaf costs two emeralds."
prices three loaves; "the shop opens at nine." "it is eight now." makes "is the shop open?" a no;
"the trip takes three days." "we left on monday." arrives on thursday.
When nothing it knows tells what is asked, it says "i don't know ." rather than repeat a line
that is only about the same thing: "what colour is my cat?" after "i have a cat.", "who bought the
sword?" after "the blacksmith made a sword.", "why is the farmer sick?" after only "the farmer is
sick.", or "where is my hat?" when no one said. A line gives an answer only if it has what the
question asks for: a colour, a name, a count, a place, a time, a reason, or the doing asked
about. A thing known by a name ("my dog is called max.") is still asked of its kind: "how many
legs does max have?" "a dog has four legs .".
All of this holds up in one long talk mixing chat, things told and every kind of question: a
line told twenty lines back still answers, and a later change ("the cart is in the yard now.")
wins over what it once answered from an older line.
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
