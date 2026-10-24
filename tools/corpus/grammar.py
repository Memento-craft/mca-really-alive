"""A small English grammar for told sentences about everyday life: who did what to what, where,
when, how and why. Verbs only take objects that make sense for them, so the sentences stay sane
while the combinations run into the billions."""
import random

R = random.Random()


def pick(xs):
    return R.choice(xs)


PEOPLE = ["farmer", "baker", "fisherman", "blacksmith", "librarian", "shepherd", "butcher", "priest",
          "builder", "miner", "teacher", "doctor", "guard", "cook", "carpenter", "weaver", "trader",
          "merchant", "gardener", "hunter", "nurse", "tailor", "mason", "child", "girl", "boy", "woman",
          "man", "old man", "old woman", "stranger", "traveller", "neighbour", "mayor", "sailor",
          "knight", "princess", "king", "queen", "soldier", "student", "baby", "twins", "family",
          "grandmother", "grandfather", "mother", "father", "sister", "brother", "uncle", "aunt"]
ANIMALS = ["dog", "cat", "horse", "cow", "pig", "sheep", "goat", "chicken", "fox", "wolf", "rabbit",
           "bear", "owl", "parrot", "donkey", "llama", "duck", "goose", "mouse", "deer", "frog"]
PERSON_ADJ = ["old", "young", "tall", "short", "kind", "grumpy", "tired", "happy", "busy", "clever",
              "brave", "shy", "friendly", "quiet", "loud", "strong", "lazy", "hungry", "curious",
              "patient", "careful", "cheerful", "sleepy", "worried", "proud", "gentle", "wise", "silly"]
ANIMAL_ADJ = ["little", "big", "fluffy", "hungry", "lazy", "fast", "noisy", "old", "young", "wild",
              "tame", "brown", "white", "black", "spotted", "clever", "scared", "playful", "fat", "thin"]

OBJECTS = {
    "food": ["bread", "an apple", "a carrot", "some soup", "a cake", "a pie", "some cheese", "a fish",
             "some berries", "a potato", "some eggs", "a melon", "some honey", "a sandwich", "some stew"],
    "drink": ["water", "milk", "tea", "juice", "soup", "cider"],
    "tool": ["the axe", "a hammer", "the shovel", "a pickaxe", "the saw", "a hoe", "the shears", "a bucket",
             "the ladder", "a rope", "a knife", "the broom", "a fishing rod", "a needle"],
    "readable": ["a book", "a letter", "a map", "the sign", "a story", "the notice", "a poem", "an old scroll"],
    "wood": ["a tree", "the logs", "some wood", "an old oak", "a birch tree", "the branches"],
    "rock": ["stone", "coal", "iron ore", "the rock", "some gravel", "copper", "gold"],
    "animal": ["the cows", "the sheep", "the pigs", "the chickens", "the horse", "the goats", "the dog",
               "the cat", "the rabbits", "the ducks"],
    "person": ["a friend", "the baker", "the doctor", "a stranger", "the children", "the mayor",
               "a traveller", "the neighbours", "the guard", "an old friend", "the family next door"],
    "structure": ["a house", "a fence", "a wall", "a bridge", "a barn", "a tower", "a well", "a shed",
                  "a boat", "a chicken coop", "a little shop", "a garden", "a staircase", "a statue"],
    "crop": ["wheat", "carrots", "potatoes", "beetroots", "pumpkins", "melons", "seeds", "flowers",
             "an apple tree", "beans", "corn"],
    "thing": ["a coin", "a key", "a ring", "a shell", "a feather", "a hat", "a lantern", "a box",
              "a bag", "a shiny stone", "a bone", "a scarf", "a glove", "a bottle", "a toy"],
    "song": ["a song", "a lullaby", "an old tune", "a happy song", "a sad song"],
    "cloth": ["a coat", "a shirt", "a dress", "a blanket", "a scarf", "the curtains", "a hat"],
}

# past tense verb, object kind or None for no object
VERBS = [
    ("ate", "food"), ("cooked", "food"), ("baked", "food"), ("shared", "food"), ("sold", "food"),
    ("bought", "food"), ("dropped", "food"), ("smelled", "food"), ("drank", "drink"), ("spilled", "drink"),
    ("poured", "drink"), ("picked up", "tool"), ("used", "tool"), ("lost", "tool"), ("fixed", "tool"),
    ("borrowed", "tool"), ("cleaned", "tool"), ("sharpened", "tool"), ("read", "readable"), ("wrote", "readable"),
    ("found", "readable"), ("burned", "readable"), ("chopped down", "wood"), ("cut", "wood"), ("carried", "wood"),
    ("mined", "rock"), ("dug up", "rock"), ("smelted", "rock"), ("fed", "animal"), ("chased", "animal"),
    ("counted", "animal"), ("brushed", "animal"), ("milked", "animal"), ("led", "animal"), ("visited", "person"),
    ("helped", "person"), ("met", "person"), ("thanked", "person"), ("called", "person"), ("hugged", "person"),
    ("waved at", "person"), ("waited for", "person"), ("argued with", "person"), ("built", "structure"),
    ("painted", "structure"), ("repaired", "structure"), ("knocked down", "structure"), ("planted", "crop"),
    ("watered", "crop"), ("harvested", "crop"), ("found", "thing"), ("lost", "thing"), ("gave away", "thing"),
    ("hid", "thing"), ("polished", "thing"), ("sang", "song"), ("hummed", "song"), ("whistled", "song"),
    ("sewed", "cloth"), ("washed", "cloth"), ("folded", "cloth"), ("wore", "cloth"),
    ("slept", None), ("laughed", None), ("cried", None), ("ran", None), ("waited", None), ("danced", None),
    ("walked", None), ("rested", None), ("worked", None), ("sat", None), ("swam", None), ("prayed", None),
    ("shouted", None), ("hid", None), ("woke up", None), ("fell asleep", None), ("got lost", None),
]
ANIMAL_VERBS = [("ate", "food"), ("chased", "animal"), ("found", "thing"), ("dropped", "thing"),
                ("slept", None), ("ran", None), ("made a noise", None), ("jumped", None), ("hid", None),
                ("played", None), ("swam", None), ("rolled in the mud", None), ("followed", "person"),
                ("bit", "thing"), ("dug up", "thing"), ("ran away", None), ("came home", None)]

ADVERBS = ["quickly", "slowly", "quietly", "loudly", "carefully", "happily", "sadly", "angrily",
           "gently", "suddenly", "finally", "proudly", "nervously", "bravely", "lazily", "patiently",
           "secretly", "together", "alone", "again"]
PLACES = ["in the kitchen", "at the market", "by the river", "in the forest", "on the farm",
          "behind the barn", "in the garden", "at the library", "near the well", "on the hill",
          "in the village square", "at the church", "on the bridge", "in the cave", "at the tavern",
          "on the beach", "in the mine", "under the old tree", "next to the bakery", "in the field",
          "at home", "in the meadow", "by the lake", "outside the walls", "at the blacksmith's"]
TIMES = ["this morning", "yesterday", "last night", "at dawn", "at noon", "in the evening",
         "on monday", "last week", "after lunch", "before dinner", "one day", "long ago",
         "in the spring", "in the winter", "during the storm", "after the festival", "at sunset",
         "early in the morning", "late at night", "on a rainy day", "on a sunny afternoon"]
REASONS = ["because it was hungry", "because it was cold", "because it was late",
           "because everyone was busy", "because the sun was shining", "because it started to rain",
           "because the festival was coming", "because a friend asked", "because winter was near",
           "so the children could play", "so nobody would be hungry", "so the house stayed warm",
           "because it was fun", "because there was no other way", "because the old one broke"]
AFTERS = ["and then went home", "and then had a nap", "and everyone cheered", "but nobody noticed",
          "and felt much better", "but it started to rain", "and then it got dark", "and smiled",
          "but then the dog ran off", "and the whole village heard about it", "and was very proud",
          "but forgot to close the door", "and then sat by the fire", "and laughed about it later"]


def subject():
    if R.random() < 0.75:
        noun = pick(PEOPLE)
        adj = pick(PERSON_ADJ) + " " if R.random() < 0.5 else ""
        if R.random() < 0.15:
            return pick(["i", "we", "my friend", "my " + pick(["mother", "father", "sister", "brother", "neighbour"])]), False
        return f"the {adj}{noun}", False
    noun = pick(ANIMALS)
    adj = pick(ANIMAL_ADJ) + " " if R.random() < 0.6 else ""
    return f"the {adj}{noun}", True


def clause():
    subj, is_animal = subject()
    verb, kind = pick(ANIMAL_VERBS if is_animal else VERBS)
    parts = [subj]
    if R.random() < 0.25:
        parts.append(pick(ADVERBS))
    parts.append(verb)
    if kind:
        parts.append(pick(OBJECTS[kind]))
    if R.random() < 0.6:
        parts.append(pick(PLACES))
    s = " ".join(parts)
    if R.random() < 0.4:
        s = pick(TIMES) + " " + s
    r = R.random()
    if r < 0.2:
        s += " " + pick(REASONS).replace("it was hungry", "they were hungry" if not is_animal else "it was hungry")
    elif r < 0.4:
        s += " " + pick(AFTERS)
    return s + "."


def told(rng):
    global R
    R = rng
    return [" ".join(clause() for _ in range(R.randint(1, 3)))]


# --- more of everything, for a wider vocabulary ---
PEOPLE += ["fisherwoman", "midwife", "innkeeper", "potter", "beekeeper", "carter", "ferryman", "miller",
           "brewer", "cobbler", "glassblower", "herbalist", "scribe", "musician", "painter", "sculptor",
           "dancer", "storyteller", "monk", "nun", "pilgrim", "beggar", "thief", "pirate", "captain",
           "explorer", "messenger", "toddler", "teenager", "bride", "groom", "widow", "orphan", "chief",
           "elder", "apprentice", "master", "cousin", "nephew", "niece", "godmother", "landlord", "tenant"]
ANIMALS += ["badger", "hedgehog", "otter", "beaver", "squirrel", "mole", "weasel", "hawk", "eagle",
            "sparrow", "robin", "swan", "heron", "pigeon", "magpie", "lamb", "calf", "foal", "piglet",
            "kitten", "puppy", "chick", "ox", "mule", "boar", "stag", "toad", "lizard", "camel", "panda"]
PERSON_ADJ += ["elderly", "cranky", "polite", "rude", "generous", "greedy", "honest", "sneaky", "nervous",
               "calm", "bold", "timid", "clumsy", "graceful", "smart", "foolish", "gentle", "fierce",
               "hardworking", "absent-minded", "talkative", "thoughtful", "stubborn", "humble", "jolly",
               "serious", "mysterious", "famous", "lonely", "homesick", "excited", "anxious", "grateful"]
ANIMAL_ADJ += ["tiny", "huge", "muddy", "wet", "shaggy", "striped", "golden", "grey", "sleepy",
               "hungry", "curious", "friendly", "angry", "loyal", "skinny", "furry", "speckled", "lost"]
OBJECTS["food"] += ["a bowl of porridge", "some roast mutton", "a slice of pumpkin pie", "a handful of nuts",
                    "some pickled beets", "a warm pancake", "a jar of jam", "some smoked fish",
                    "a wheel of cheese", "some cabbage soup", "a basket of plums", "some fried mushrooms",
                    "a cup of beans", "a sweet roll", "some grilled corn", "a baked apple"]
OBJECTS["tool"] += ["the chisel", "a trowel", "the pitchfork", "a sickle", "the plough", "a mallet",
                    "the tongs", "a spindle", "the anvil", "a crowbar", "the wheelbarrow", "a lantern"]
OBJECTS["thing"] += ["a pebble", "a seashell", "a silver spoon", "a wooden doll", "a music box",
                     "a pocket watch", "a feather pen", "a candle stub", "a rusty nail", "an old coin",
                     "a pine cone", "a bird's nest", "a horseshoe", "a locket", "a marble", "a dice"]
OBJECTS["structure"] += ["a windmill", "a dock", "a lighthouse", "a greenhouse", "a stable", "a beehive",
                         "a pigpen", "a treehouse", "a chimney", "a cellar", "a porch", "a gate", "a dam"]
OBJECTS["crop"] += ["cabbages", "onions", "garlic", "peas", "squash", "sunflowers", "herbs", "oats",
                    "barley", "flax", "strawberries", "grapes", "turnips", "lettuce"]
VERBS += [("tasted", "food"), ("burned", "food"), ("wrapped", "food"), ("stole", "food"), ("traded", "thing"),
          ("dropped", "tool"), ("hung up", "tool"), ("sold", "tool"), ("studied", "readable"),
          ("copied", "readable"), ("tore", "readable"), ("stacked", "wood"), ("split", "wood"),
          ("sorted", "rock"), ("sold", "rock"), ("groomed", "animal"), ("sheared", "animal"),
          ("rescued", "animal"), ("scared", "animal"), ("greeted", "person"), ("comforted", "person"),
          ("teased", "person"), ("forgave", "person"), ("ignored", "person"), ("followed", "person"),
          ("guided", "person"), ("cheered for", "person"), ("designed", "structure"), ("cleaned", "structure"),
          ("decorated", "structure"), ("finished", "structure"), ("weeded", "crop"), ("picked", "crop"),
          ("stored", "crop"), ("counted", "thing"), ("buried", "thing"), ("dropped", "thing"),
          ("taught", "song"), ("practised", "song"), ("mended", "cloth"), ("dyed", "cloth"), ("ironed", "cloth"),
          ("yawned", None), ("sneezed", None), ("coughed", None), ("smiled", None), ("frowned", None),
          ("shivered", None), ("sighed", None), ("giggled", None), ("whispered", None), ("complained", None),
          ("stretched", None), ("knelt down", None), ("climbed up", None), ("hurried home", None),
          ("stayed inside", None), ("gave up", None), ("kept going", None), ("listened", None), ("dreamed", None)]
ADVERBS += ["cheerfully", "grumpily", "politely", "rudely", "eagerly", "calmly", "clumsily", "gracefully",
            "softly", "boldly", "shyly", "wisely", "foolishly", "honestly", "kindly", "silently",
            "noisily", "anxiously", "gratefully", "hungrily", "sleepily", "barely", "almost", "nearly"]
PLACES += ["at the docks", "on the windmill", "by the beehives", "in the stable", "at the inn",
           "on the porch", "in the attic", "in the cellar", "at the crossroads", "by the waterfall",
           "on the mountain path", "in the swamp", "at the edge of the desert", "in the jungle",
           "on the frozen lake", "under the bridge", "at the town hall", "in the schoolhouse",
           "at the graveyard", "in the greenhouse", "at the lighthouse", "in the orchard"]
TIMES += ["at midnight", "on tuesday", "on thursday", "on saturday", "in the autumn", "in the summer",
          "the day before yesterday", "a moment ago", "every morning", "now and then", "once upon a time",
          "after the harvest", "before the storm", "during the night", "at first light", "at dusk"]
REASONS += ["because nobody else would", "because the baby was crying", "because the river was rising",
            "because the mayor said so", "because the moon was full", "because it smelled wonderful",
            "so the guests would be happy", "so the animals would be safe", "because it was the right thing to do",
            "because the roof was leaking", "because a storm was coming", "because they missed home"]
AFTERS += ["and nobody believed it", "and the children clapped", "but the cat knocked it over",
           "and then fell fast asleep", "but felt silly afterwards", "and promised to do it again",
           "and the neighbours came to watch", "but the rain spoiled everything", "and it was perfect"]
