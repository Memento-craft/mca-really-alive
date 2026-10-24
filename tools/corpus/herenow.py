"""Talk about the here and now: what a speaker sees, smells, feels, does and wants.

These are the conversations a villager's concept links can fill in. The sentence frame comes from
the language area ("i see a ..."), and the word that ends it from whatever the villager's concept
lobe is holding at that moment, so the frames here pair each question with many fillers and never
favour one filler for long.
"""

SEEN = ["cow", "pig", "sheep", "chicken", "horse", "wolf", "cat", "dog", "rabbit", "fox", "llama",
        "villager", "player", "zombie", "skeleton", "creeper", "spider", "witch", "ladder", "wall",
        "water", "lava", "fire", "tree", "flower", "door", "house", "torch", "chest", "bed", "food",
        "bread", "apple", "block", "hole", "hill", "cave", "fence", "field", "river", "rock", "path",
        "bird", "fish", "cloud", "sun", "moon", "boat", "cart", "bell", "well", "farm"]
SEEN_MANY = ["cows", "pigs", "sheep", "chickens", "horses", "wolves", "rabbits", "villagers",
             "zombies", "skeletons", "spiders", "trees", "flowers", "houses", "birds", "fish", "people"]
SMELLS = ["smoke", "food", "bread", "flowers", "water", "animals", "a zombie", "rain", "fish",
          "something burning", "soup", "the farm", "grass", "mud", "cooking", "pie", "the sea"]
FEELS = ["hungry", "hurt", "tired", "wet", "cold", "fine", "good", "happy", "scared", "sick",
         "full", "sleepy", "okay", "warm", "safe", "bored", "dizzy", "strong", "weak", "great"]
DOINGS = ["walking", "swimming", "jumping", "climbing", "digging", "building", "running",
          "sneaking", "standing here", "looking around", "eating", "resting", "falling",
          "exploring", "working", "waiting", "playing", "hiding", "fishing", "farming",
          "carrying blocks", "climbing the ladder", "digging a hole", "building a wall",
          "swimming in the river", "walking home", "looking for food", "running away"]
WANTS = ["food", "bread", "water", "help", "a bed", "to rest", "to go home", "blocks", "to swim",
         "to play", "something to eat", "an apple", "to sleep", "a friend", "to build a house",
         "to climb", "to dig", "some soup", "to get out of the water", "to be safe", "a torch"]
WHERES = ["in the water", "on the ladder", "by the wall", "near the tree", "in the field",
          "at home", "in the cave", "on the hill", "by the river", "in the house", "on the path",
          "near the fire", "next to you", "over here", "behind the house", "in the village"]


def a_or_an(word):
    return ("an " if word[0] in "aeiou" else "a ") + word


def _see(R):
    pick = R.choice
    x = pick(SEEN)
    many = pick(SEEN_MANY)
    q = pick(["what do you see?", "what can you see?", "what is that?", "what's that over there?",
              "do you see anything?", "look, what is that?", "what are you looking at?",
              "what's around you?", "what is over there?", "can you see anything?", "what's that?",
              "what did you see?", "what do you see over there?", "is something there?"])
    a = pick([f"i see {a_or_an(x)}.", f"{a_or_an(x)}.", f"there is {a_or_an(x)} over there.",
              f"i can see {a_or_an(x)}.", f"i think it's {a_or_an(x)}.", f"just {a_or_an(x)}.",
              f"{a_or_an(x)}, over there.", f"it's {a_or_an(x)}.", f"i see {many}.", f"{many}.",
              f"i'm looking at {a_or_an(x)}.", f"i saw {a_or_an(x)}.", f"a {x}, i think.",
              f"there are {many} over there.", f"the {x}.", f"look, {a_or_an(x)}!"])
    d = [q, a]
    if R.random() < 0.5:
        d.append(pick(["what else?", "anything else?", "where?", "really?", "oh, i see it.",
                       "is it close?", "how many?", "what is it doing?"]))
        d.append(pick([f"and {a_or_an(pick(SEEN))}.", "nothing else.", f"over by the {pick(SEEN)}.",
                       "yes, really.", "very close.", "just one.", "i don't know.", "it's just standing there.",
                       f"and some {pick(SEEN_MANY)}."]))
    return d


def _smell(R):
    pick = R.choice
    s = pick(SMELLS)
    q = pick(["what do you smell?", "what's that smell?", "do you smell something?", "what smells like that?",
              "can you smell that?", "what is that smell?"])
    a = pick([f"i smell {s}.", f"it smells like {s}.", f"{s}, i think.", f"smells like {s}.", f"{s}.",
              f"yes, {s}.", f"i think it's {s}."])
    return [q, a] + ([pick(["mmm.", "ugh.", "me too.", "really?", "nice."])] if R.random() < 0.4 else [])


def _feel(R):
    pick = R.choice
    f = pick(FEELS)
    q = pick(["how do you feel?", "how are you feeling?", "are you okay?", "what's wrong?", "how do you feel now?",
              "are you alright?", "is something wrong?", "how are you doing?"])
    a = pick([f"i feel {f}.", f"i'm {f}.", f"{f}.", f"i am {f}.", f"i'm a bit {f}.", f"very {f}.",
              f"i feel very {f}.", f"i'm so {f}.", f"pretty {f}."])
    return [q, a] + ([pick(["oh no.", "good.", "i'm sorry.", "me too.", "glad to hear it.", "why?"])] if R.random() < 0.5 else [])


def _doing(R):
    pick = R.choice
    act = pick(DOINGS)
    q = pick(["what are you doing?", "what are you up to?", "what are you doing there?", "what are you doing now?",
              "hey, what are you doing?", "what were you doing?", "what's going on?"])
    a = pick([f"i'm {act}.", f"i am {act}.", f"just {act}.", f"{act}.", f"i'm just {act}.", f"i was {act}.",
              f"{act}, why?"])
    return [q, a] + ([pick(["oh, okay.", "can i help?", "be careful.", "looks fun.", "nice."])] if R.random() < 0.5 else [])


def _want(R):
    pick = R.choice
    w = pick(WANTS)
    q = pick(["what do you want?", "do you need anything?", "what would you like?", "what do you need?",
              "is there something you want?", "what do you want to do?"])
    a = pick([f"i want {w}.", f"i need {w}.", f"{w}, please.", f"i'd like {w}.", f"{w}.", f"just {w}."])
    return [q, a] + ([pick(["okay.", "here you go.", "me too.", "i'll help.", "maybe later."])] if R.random() < 0.5 else [])


def _where(R):
    pick = R.choice
    q = pick(["where are you?", "where are you now?", "where did you go?", "where are you standing?"])
    a = pick([f"i'm {pick(WHERES)}.", f"{pick(WHERES)}.", f"i am {pick(WHERES)}.", f"right {pick(WHERES)}."])
    return [q, a] + ([pick(["i'm coming.", "stay there.", "okay.", "i see you."])] if R.random() < 0.5 else [])


def _remark(R):
    """The speaker starts: something it noticed, felt or wants."""
    pick = R.choice
    x = pick(SEEN)
    first = pick([f"look, {a_or_an(x)}!", f"{a_or_an(x)}!", f"watch out, {a_or_an(x)}!", f"there's {a_or_an(x)}.",
                  f"i see {a_or_an(x)}.", f"hey, {a_or_an(x)}!", f"i'm {pick(FEELS)}.", f"i smell {pick(SMELLS)}.",
                  f"i want {pick(WANTS)}.", "ouch!", f"i'm {pick(DOINGS)}.", f"oh, {a_or_an(x)}."])
    reply = pick(["where?", "i see it.", "really?", "oh!", "me too.", "be careful.", "so do i.", "are you okay?",
                  "let's go.", "nice.", "i know."])
    return [first, reply]


def here_and_now(R):
    return R.choice([_see, _see, _see, _smell, _feel, _feel, _doing, _doing, _want, _where, _remark, _remark])(R)
