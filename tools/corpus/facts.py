"""World knowledge as questions and answers: what things are, look like, do, eat and are made of,
plus numbers, days, months, opposites and the meanings of words."""
import random

R = random.Random()


def pick(xs):
    return R.choice(xs)


# name, plural, kind, colour, size (1 tiny .. 9 huge), where, does, eats, sound
ANIMALS = [
    ("cow", "cows", "farm animal", "black and white", 7, "fields", "gives milk", "grass", "moo"),
    ("pig", "pigs", "farm animal", "pink", 6, "farms", "rolls in the mud", "carrots and potatoes", "oink"),
    ("sheep", "sheep", "farm animal", "white", 6, "meadows", "gives wool", "grass", "baa"),
    ("chicken", "chickens", "bird", "white", 3, "farms", "lays eggs", "seeds", "cluck"),
    ("horse", "horses", "animal", "brown", 8, "plains", "carries people", "hay and apples", "neigh"),
    ("donkey", "donkeys", "animal", "grey", 7, "farms", "carries heavy loads", "hay", "hee haw"),
    ("dog", "dogs", "pet", "brown", 4, "houses", "guards the house", "meat and bones", "woof"),
    ("cat", "cats", "pet", "orange", 3, "houses", "catches mice", "fish", "meow"),
    ("wolf", "wolves", "wild animal", "grey", 5, "forests", "hunts in packs", "meat", "howl"),
    ("fox", "foxes", "wild animal", "orange", 4, "forests", "sneaks around at night", "berries and chickens", "yip"),
    ("rabbit", "rabbits", "small animal", "brown", 2, "meadows", "hops and digs burrows", "carrots", "squeak"),
    ("bear", "bears", "wild animal", "brown", 9, "mountains", "sleeps all winter", "fish and honey", "growl"),
    ("bee", "bees", "insect", "yellow and black", 1, "gardens", "makes honey", "nectar from flowers", "buzz"),
    ("fish", "fish", "water animal", "silver", 2, "rivers and seas", "swims", "small bugs", "blub"),
    ("frog", "frogs", "small animal", "green", 1, "swamps", "jumps and swims", "flies", "ribbit"),
    ("turtle", "turtles", "animal", "green", 3, "beaches", "carries its shell", "seagrass", "nothing much"),
    ("owl", "owls", "bird", "brown", 3, "forests", "hunts at night", "mice", "hoot"),
    ("duck", "ducks", "bird", "white", 3, "ponds", "swims and flies", "bread and seeds", "quack"),
    ("parrot", "parrots", "bird", "red and green", 2, "jungles", "copies sounds", "seeds and fruit", "squawk"),
    ("goat", "goats", "animal", "white", 5, "mountains", "climbs steep rocks", "almost anything", "bleat"),
    ("llama", "llamas", "animal", "cream", 7, "mountains", "carries packs and spits", "hay", "hum"),
    ("mouse", "mice", "small animal", "grey", 1, "fields and barns", "hides in holes", "seeds and cheese", "squeak"),
    ("deer", "deer", "wild animal", "brown", 7, "forests", "runs very fast", "leaves and grass", "bellow"),
    ("squid", "squids", "sea animal", "dark blue", 4, "the sea", "squirts ink", "small fish", "nothing"),
    ("dolphin", "dolphins", "sea animal", "grey", 7, "the sea", "jumps out of the water", "fish", "click"),
    ("spider", "spiders", "bug", "black", 1, "caves and corners", "spins webs", "flies", "nothing"),
    ("bat", "bats", "flying animal", "black", 1, "caves", "flies in the dark", "bugs", "squeak"),
    ("snake", "snakes", "reptile", "green", 3, "grass and jungles", "slithers", "mice", "hiss"),
    ("panda", "pandas", "animal", "black and white", 8, "bamboo forests", "eats all day", "bamboo", "bleat"),
    ("camel", "camels", "animal", "tan", 9, "deserts", "walks for days without water", "dry plants", "grunt"),
    ("whale", "whales", "sea animal", "blue", 9, "the ocean", "sings under the water", "tiny shrimp", "sing"),
    ("butterfly", "butterflies", "insect", "colourful", 1, "gardens", "flutters from flower to flower", "nectar", "nothing"),
    ("crow", "crows", "bird", "black", 2, "fields", "steals shiny things", "seeds and scraps", "caw"),
    ("ant", "ants", "insect", "black", 1, "the ground", "carries food home", "crumbs", "nothing"),
    ("goose", "geese", "bird", "grey", 4, "lakes", "flies south in winter", "grass", "honk"),
]

# name, kind, use, made of
THINGS = [
    ("axe", "tool", "chop wood", "iron and wood"), ("pickaxe", "tool", "mine stone and ore", "iron and wood"),
    ("shovel", "tool", "dig dirt and sand", "iron and wood"), ("hoe", "tool", "till the soil for farming", "iron and wood"),
    ("hammer", "tool", "hit nails", "iron and wood"), ("saw", "tool", "cut planks", "iron"),
    ("sword", "weapon", "fight monsters", "iron"), ("bow", "weapon", "shoot arrows", "wood and string"),
    ("shield", "piece of armour", "block attacks", "wood and iron"), ("helmet", "piece of armour", "protect your head", "iron"),
    ("bucket", "container", "carry water or milk", "iron"), ("basket", "container", "carry fruit", "straw"),
    ("barrel", "container", "store food", "wood"), ("chest", "box", "keep your things safe", "wood"),
    ("torch", "light", "light up dark places", "a stick and coal"), ("lantern", "light", "carry light safely", "iron and glass"),
    ("candle", "light", "light a room", "wax"), ("bed", "piece of furniture", "sleep", "wood and wool"),
    ("chair", "piece of furniture", "sit", "wood"), ("table", "piece of furniture", "eat and work at", "wood"),
    ("door", "part of a house", "go in and out", "wood"), ("window", "part of a house", "let light in", "glass"),
    ("roof", "part of a house", "keep the rain out", "wood or tiles"), ("wall", "part of a house", "hold up the roof", "stone or wood"),
    ("ladder", "tool", "climb up high", "wood"), ("rope", "tool", "tie things and climb", "string"),
    ("boat", "vehicle", "travel on water", "wood"), ("cart", "vehicle", "carry goods", "wood and iron"),
    ("map", "piece of paper", "find your way", "paper"), ("compass", "tool", "find north", "iron and redstone"),
    ("clock", "tool", "tell the time", "gold"), ("book", "thing", "read stories and learn", "paper and leather"),
    ("needle", "tool", "sew clothes", "iron"), ("shears", "tool", "cut wool off sheep", "iron"),
    ("fishing rod", "tool", "catch fish", "sticks and string"), ("bell", "thing", "call people together", "gold"),
    ("well", "place", "get water", "stone"), ("oven", "thing", "bake bread", "stone"), ("furnace", "thing", "melt ore", "stone"),
    ("anvil", "thing", "repair tools", "iron"), ("loom", "thing", "weave cloth", "wood"), ("key", "thing", "open locks", "iron"),
    ("coat", "piece of clothing", "keep warm", "wool"), ("boots", "piece of clothing", "protect your feet", "leather"),
    ("hat", "piece of clothing", "keep the sun off", "straw or wool"), ("saddle", "thing", "ride a horse", "leather"),
    ("knife", "tool", "cut food", "iron"), ("pot", "container", "cook soup", "iron or clay"), ("plate", "dish", "eat from", "clay"),
    ("cup", "dish", "drink from", "clay or glass"), ("spoon", "tool", "eat soup", "wood or silver"),
    ("broom", "tool", "sweep the floor", "straw and wood"), ("fence", "thing", "keep animals in", "wood"),
    ("bridge", "thing", "cross a river", "wood or stone"), ("brush", "tool", "paint or clean", "wood and hair"),
]

FOODS = [
    ("bread", "wheat", "baked"), ("cake", "wheat, sugar, eggs and milk", "baked"), ("cookie", "wheat and cocoa", "baked"),
    ("soup", "vegetables and water", "boiled"), ("pie", "pumpkin, sugar and eggs", "baked"), ("cheese", "milk", "aged"),
    ("butter", "cream", "churned"), ("jam", "fruit and sugar", "boiled"), ("stew", "meat and vegetables", "cooked slowly"),
    ("porridge", "oats and milk", "boiled"), ("honey", "nectar", "made by bees"), ("toast", "bread", "toasted over a fire"),
    ("pancake", "flour, milk and eggs", "fried"),
]

# colours of things
COLOURS = [("grass", "green"), ("the sky", "blue"), ("snow", "white"), ("coal", "black"), ("blood", "red"),
           ("the sun", "yellow"), ("a carrot", "orange"), ("an apple", "red or green"), ("milk", "white"),
           ("lava", "orange and red"), ("water", "clear or blue"), ("a pumpkin", "orange"), ("gold", "yellow"),
           ("iron", "grey"), ("an emerald", "green"), ("a diamond", "light blue"), ("dirt", "brown"),
           ("sand", "yellow"), ("a lemon", "yellow"), ("a banana", "yellow"), ("the night sky", "black"),
           ("a rose", "red"), ("a leaf", "green"), ("the sea", "blue"), ("ash", "grey"), ("a cherry", "red")]

OPPOSITES = [("hot", "cold"), ("big", "small"), ("tall", "short"), ("fast", "slow"), ("happy", "sad"),
             ("day", "night"), ("up", "down"), ("in", "out"), ("open", "closed"), ("full", "empty"),
             ("old", "young"), ("light", "dark"), ("wet", "dry"), ("hard", "soft"), ("loud", "quiet"),
             ("rich", "poor"), ("early", "late"), ("near", "far"), ("left", "right"), ("good", "bad"),
             ("strong", "weak"), ("heavy", "light"), ("clean", "dirty"), ("first", "last"), ("love", "hate"),
             ("buy", "sell"), ("win", "lose"), ("push", "pull"), ("give", "take"), ("begin", "end"),
             ("brave", "afraid"), ("thick", "thin"), ("sweet", "sour"), ("awake", "asleep"), ("question", "answer"),
             ("summer", "winter"), ("friend", "enemy"), ("top", "bottom"), ("front", "back"), ("true", "false")]

MEANINGS = [
    ("brave", "not afraid to do something hard or scary"), ("kind", "nice and caring to others"),
    ("lazy", "not wanting to work"), ("honest", "always telling the truth"), ("curious", "wanting to know things"),
    ("generous", "happy to give and share"), ("patient", "able to wait without getting upset"),
    ("lonely", "sad because you are alone"), ("jealous", "upset because someone has what you want"),
    ("proud", "happy about something you did well"), ("ashamed", "feeling bad about something you did"),
    ("harvest", "gathering the crops when they are ripe"), ("village", "a small group of houses and people"),
    ("trade", "giving one thing to get another"), ("journey", "a long trip"), ("shelter", "a place that keeps you safe"),
    ("enormous", "very very big"), ("tiny", "very very small"), ("ancient", "very old"), ("swift", "very fast"),
    ("hungry", "wanting to eat"), ("thirsty", "wanting to drink"), ("exhausted", "very very tired"),
    ("furious", "very angry"), ("delighted", "very happy"), ("terrified", "very scared"), ("neighbour", "someone who lives near you"),
    ("stranger", "someone you don't know"), ("promise", "saying you will surely do something"),
    ("secret", "something you don't tell anyone"), ("gift", "something you give to make someone happy"),
    ("festival", "a big party for the whole village"), ("recipe", "the steps to cook something"),
    ("library", "a place full of books"), ("blacksmith", "someone who makes things from iron"),
    ("shepherd", "someone who looks after sheep"), ("merchant", "someone who buys and sells things"),
    ("cave", "a big hole in a hill or mountain"), ("island", "land with water all around it"),
    ("desert", "a dry place with lots of sand"), ("forest", "a place with many trees"), ("ocean", "a very big sea"),
    ("mountain", "a very tall hill"), ("storm", "strong wind and rain"), ("drought", "a long time without rain"),
    ("flood", "too much water covering the land"), ("echo", "a sound that bounces back"),
    ("apologise", "say you are sorry"), ("forgive", "stop being angry with someone"), ("borrow", "take something and give it back later"),
    ("whisper", "speak very quietly"), ("shout", "speak very loudly"), ("wander", "walk with no plan"),
    ("explore", "go somewhere new to see what is there"), ("remember", "keep something in your mind"),
    ("forget", "lose something from your mind"), ("discover", "find something for the first time"),
    ("comfort", "help someone feel better"), ("celebrate", "do something fun for a happy event"),
    ("hope", "wanting something good to happen"), ("dream", "pictures in your head while you sleep"),
    ("friendship", "when two people like and trust each other"), ("trust", "believing someone will not hurt you"),
    ("courage", "being brave"), ("wisdom", "knowing what is right and good"), ("mistake", "something done wrong by accident"),
    ("danger", "something that could hurt you"), ("emerald", "a green gem that villagers trade with"),
    ("golem", "a big iron guard that protects villagers"), ("raid", "when pillagers attack a village"),
]

NUMBERS = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
           "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"]
DAYS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
MONTHS = ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october",
          "november", "december"]
LEGS = {"chicken": "two", "duck": "two", "owl": "two", "parrot": "two", "crow": "two", "goose": "two",
        "spider": "eight", "ant": "six", "bee": "six", "butterfly": "six", "fish": "no", "snake": "no",
        "whale": "no", "dolphin": "no", "squid": "ten arms and no"}


def art(w):
    return ("an " if w[0] in "aeiou" else "a ") + w


def animal_qa():
    a = pick(ANIMALS)
    name, plural, kind, colour, size, where, does, eats, sound = a
    qs = [
        ([f"what is {art(name)}?", f"what kind of thing is {art(name)}?"], [f"{art(name)} is {art(kind)}.", f"it's {art(kind)} that {does}."]),
        ([f"what do {plural} eat?", f"what does {art(name)} eat?"], [f"{plural} eat {eats}.", f"mostly {eats}."]),
        ([f"where do {plural} live?", f"where can i find {plural}?"], [f"{plural} live in {where}.", f"look in {where}."]),
        ([f"what colour is {art(name)}?", f"what colour are {plural}?"], [f"{plural} are usually {colour}.", f"most are {colour}."]),
        ([f"what sound does {art(name)} make?", f"what does {art(name)} say?"],
         [f"{art(name)} goes {sound}." if sound not in ("nothing", "nothing much") else f"{art(name)} doesn't make much noise."]),
        ([f"what does {art(name)} do?", f"what are {plural} good for?"], [f"{art(name)} {does}.", f"it {does}."]),
        ([f"how many legs does {art(name)} have?"], [f"{art(name)} has {LEGS.get(name, 'four')} legs."]),
    ]
    q, a_ = pick(qs)
    return [pick(q), pick(a_)]


def compare_qa():
    a, b = R.sample(ANIMALS, 2)
    if R.random() < 0.5:
        q = f"is {art(a[0])} bigger than {art(b[0])}?"
        if a[4] == b[4]:
            ans = ["they are about the same size.", "about the same."]
        elif a[4] > b[4]:
            ans = [f"yes, {art(a[0])} is bigger than {art(b[0])}.", "yes, much bigger."]
        else:
            ans = [f"no, {art(a[0])} is smaller than {art(b[0])}.", f"no, {art(b[0])} is bigger."]
    else:
        q = f"which is bigger, {art(a[0])} or {art(b[0])}?"
        big = a if a[4] >= b[4] else b
        ans = ["they are about the same size."] if a[4] == b[4] else [f"{art(big[0])} is bigger.", f"the {big[0]}."]
    return [q, pick(ans)]


def thing_qa():
    name, kind, use, made = pick(THINGS)
    qs = [
        ([f"what is {art(name)} for?", f"what do you use {art(name)} for?", f"why do i need {art(name)}?"],
         [f"you use {art(name)} to {use}.", f"to {use}.", f"{art(name)} helps you {use}."]),
        ([f"what is {art(name)} made of?", f"how do you make {art(name)}?"], [f"it's made of {made}.", f"{made}."]),
        ([f"what is {art(name)}?"], [f"{art(name)} is {art(kind)}. you use it to {use}."]),
        ([f"what should i use to {use}?", f"how do i {use}?"], [f"use {art(name)}.", f"you need {art(name)}."]),
    ]
    q, a = pick(qs)
    return [pick(q), pick(a)]


def food_qa():
    name, made, how = pick(FOODS)
    return [pick([f"how do you make {name}?", f"what is {name} made from?"]),
            pick([f"{name} is made from {made}.", f"it's {made}, {how}."])]


def colour_qa():
    thing, c = pick(COLOURS)
    return [pick([f"what colour is {thing}?", f"is {thing} {pick(['red', 'blue', 'green'])}?"]), f"{thing} is {c}."]


def opposite_qa():
    a, b = pick(OPPOSITES)
    if R.random() < 0.5:
        a, b = b, a
    return [pick([f"what is the opposite of {a}?", f"what's the opposite of {a}?"]), f"the opposite of {a} is {b}."]


def meaning_qa():
    w, m = pick(MEANINGS)
    return [pick([f"what does {w} mean?", f"what does the word {w} mean?", f"what is the meaning of {w}?"]),
            pick([f"{w} means {m}.", f"it means {m}."])]


def math_qa():
    a, b = R.randint(0, 10), R.randint(0, 10)
    if R.random() < 0.6:
        return [pick([f"what is {NUMBERS[a]} plus {NUMBERS[b]}?", f"what is {NUMBERS[a]} and {NUMBERS[b]}?",
                      f"how much is {NUMBERS[a]} plus {NUMBERS[b]}?"]), f"{NUMBERS[a]} plus {NUMBERS[b]} is {NUMBERS[a + b]}."]
    a, b = max(a, b) + R.randint(0, 10), min(a, b)
    return [f"what is {NUMBERS[a]} take away {NUMBERS[b]}?", f"{NUMBERS[a]} take away {NUMBERS[b]} is {NUMBERS[a - b]}."]


def count_qa():
    n = R.randint(1, 8)
    item = pick(["apples", "eggs", "sheep", "coins", "stones", "fish", "carrots", "books"])
    if R.random() < 0.5:
        return [f"i have {NUMBERS[n]} {item} and you give me one more. how many do i have?", f"you have {NUMBERS[n + 1]} {item}."]
    return ["can you count to " + NUMBERS[n + 2] + "?", " ".join(NUMBERS[1:n + 3]) + "."]


def sequence_qa():
    kind = R.randint(0, 2)
    if kind == 0:
        i = R.randint(0, 6)
        return [pick([f"what day comes after {DAYS[i]}?", f"what comes after {DAYS[i]}?"]), f"{DAYS[(i + 1) % 7]} comes after {DAYS[i]}."]
    if kind == 1:
        i = R.randint(0, 11)
        return [f"what month comes after {MONTHS[i]}?", f"{MONTHS[(i + 1) % 12]} comes after {MONTHS[i]}."]
    i = R.randint(0, 18)
    return [f"what number comes after {NUMBERS[i]}?", f"{NUMBERS[i + 1]} comes after {NUMBERS[i]}."]


def can_qa():
    abilities = {"swim": {"fish", "duck", "frog", "turtle", "dolphin", "whale", "squid", "dog", "goose"},
                 "fly": {"bee", "owl", "duck", "parrot", "bat", "butterfly", "crow", "goose"},
                 "climb": {"cat", "goat", "spider", "squirrel", "bear", "snake"},
                 "talk": {"parrot"}, "run fast": {"horse", "deer", "wolf", "dog", "fox", "rabbit"}}
    ab = pick(list(abilities))
    a = pick(ANIMALS)
    yes = a[0] in abilities[ab]
    return [f"can {art(a[0])} {ab}?", pick(["yes", "yes, it can"] if yes else ["no", "no, it can't"]) + "."]


GENERATORS = [(animal_qa, 6), (compare_qa, 2), (thing_qa, 5), (food_qa, 1), (colour_qa, 2), (opposite_qa, 2),
              (meaning_qa, 4), (math_qa, 2), (count_qa, 1), (sequence_qa, 2), (can_qa, 2)]
_FUNCS = [f for f, w in GENERATORS for _ in range(w)]


def fact_dialogue(rng):
    global R
    R = rng
    d = pick(_FUNCS)()
    r = R.random()
    if r < 0.3:
        d.append(pick(["oh, i see.", "i didn't know that.", "thanks.", "really?", "of course.", "that makes sense.", "huh, interesting."]))
        if d[-1] == "really?":
            d.append(pick(["yes, really.", "it's true.", "ask anyone."]))
    elif r < 0.45:
        # A second question in the same talk.
        d += pick(_FUNCS)()
    return d


# --- more of everything, for a wider vocabulary ---
ANIMALS += [
    ("badger", "badgers", "wild animal", "black and white", 4, "woods", "digs tunnels", "worms and roots", "growl"),
    ("hedgehog", "hedgehogs", "small animal", "brown", 2, "hedges and gardens", "rolls into a spiky ball", "beetles", "snuffle"),
    ("otter", "otters", "water animal", "brown", 4, "rivers", "floats on its back", "fish", "chirp"),
    ("beaver", "beavers", "water animal", "brown", 4, "rivers", "builds dams", "tree bark", "slap"),
    ("eagle", "eagles", "bird", "brown and white", 4, "mountains", "soars high in the sky", "fish and rabbits", "screech"),
    ("swan", "swans", "bird", "white", 5, "lakes", "glides on the water", "water plants", "hiss"),
    ("hawk", "hawks", "bird", "brown", 3, "open fields", "hunts from the sky", "mice", "screech"),
    ("ox", "oxen", "farm animal", "brown", 8, "farms", "pulls the plough", "hay", "low"),
    ("mule", "mules", "animal", "brown", 7, "farms", "carries loads up mountains", "hay", "bray"),
    ("lizard", "lizards", "reptile", "green", 1, "deserts and walls", "warms itself in the sun", "bugs", "nothing"),
    ("crab", "crabs", "sea animal", "red", 1, "beaches", "walks sideways", "tiny sea creatures", "click"),
    ("seal", "seals", "sea animal", "grey", 6, "cold coasts", "lies on rocks", "fish", "bark"),
    ("penguin", "penguins", "bird", "black and white", 3, "icy places", "swims but cannot fly", "fish", "squawk"),
    ("moth", "moths", "insect", "grey", 1, "anywhere with light at night", "flies around lamps", "nectar", "nothing"),
    ("worm", "worms", "small animal", "pink", 1, "the soil", "makes the soil rich", "dead leaves", "nothing"),
    ("snail", "snails", "small animal", "brown", 1, "gardens", "carries its shell slowly", "leaves", "nothing"),
]
THINGS += [
    ("plough", "tool", "turn the soil", "wood and iron"), ("pitchfork", "tool", "lift hay", "wood and iron"),
    ("sickle", "tool", "cut wheat", "iron"), ("chisel", "tool", "carve stone and wood", "iron"),
    ("wheelbarrow", "tool", "move heavy things", "wood and iron"), ("spindle", "tool", "spin wool into thread", "wood"),
    ("telescope", "tool", "see things far away", "brass and glass"), ("hourglass", "thing", "measure time", "glass and sand"),
    ("quill", "tool", "write with ink", "a feather"), ("ink", "thing", "write and draw", "soot and water"),
    ("blanket", "thing", "keep warm in bed", "wool"), ("pillow", "thing", "rest your head", "cloth and feathers"),
    ("mirror", "thing", "see yourself", "glass and silver"), ("comb", "tool", "tidy your hair", "wood or bone"),
    ("soap", "thing", "wash yourself", "fat and ash"), ("towel", "thing", "dry yourself", "cloth"),
    ("kettle", "container", "boil water", "iron"), ("jar", "container", "keep jam and honey", "glass"),
    ("bottle", "container", "carry drinks", "glass"), ("sack", "container", "carry grain", "cloth"),
    ("net", "tool", "catch fish", "string"), ("anchor", "thing", "keep a boat in place", "iron"),
    ("sail", "part of a boat", "catch the wind", "cloth"), ("oar", "tool", "row a boat", "wood"),
    ("wagon", "vehicle", "carry people and goods", "wood"), ("wheel", "part of a cart", "roll along", "wood and iron"),
    ("drum", "instrument", "make music", "wood and leather"), ("flute", "instrument", "play tunes", "wood"),
    ("harp", "instrument", "play soft music", "wood and string"), ("whistle", "thing", "make a loud sound", "wood or tin"),
    ("crown", "thing", "show who is king", "gold"), ("ring", "jewel", "wear on your finger", "gold or silver"),
    ("necklace", "jewel", "wear around your neck", "beads or gold"), ("purse", "bag", "carry coins", "leather"),
    ("lock", "thing", "keep doors shut", "iron"), ("chain", "thing", "tie things strongly", "iron"),
    ("brick", "block", "build strong walls", "baked clay"), ("glass", "material", "make windows", "melted sand"),
    ("paper", "material", "write on", "sugar cane or wood"), ("candle wick", "part of a candle", "hold the flame", "string"),
]
MEANINGS += [
    ("polite", "speaking and acting nicely to others"), ("rude", "not polite"), ("greedy", "wanting more than you need"),
    ("humble", "not thinking you are better than others"), ("stubborn", "not willing to change your mind"),
    ("clumsy", "often dropping or bumping into things"), ("graceful", "moving in a smooth and lovely way"),
    ("nervous", "a little scared about what will happen"), ("grateful", "thankful"), ("cozy", "warm and comfortable"),
    ("gloomy", "dark and sad"), ("cheerful", "happy and bright"), ("fragile", "easy to break"),
    ("sturdy", "strong and hard to break"), ("rare", "not found often"), ("common", "found often"),
    ("vast", "very very big, like the sea"), ("narrow", "not wide"), ("steep", "going up very sharply"),
    ("shallow", "not deep"), ("ripe", "ready to eat"), ("rotten", "gone bad"), ("fresh", "new and good"),
    ("stale", "old and dry, like old bread"), ("damp", "a little wet"), ("frozen", "turned to ice"),
    ("blizzard", "a storm with lots of snow"), ("thunder", "the loud sound after lightning"),
    ("lightning", "a bright flash in a storm"), ("rainbow", "colours in the sky after rain"),
    ("fog", "a cloud close to the ground"), ("dawn", "when the sun comes up"), ("dusk", "when the sun goes down"),
    ("season", "a part of the year, like summer"), ("century", "one hundred years"), ("ancestor", "someone in your family long ago"),
    ("guest", "someone who visits your home"), ("host", "someone who has guests"), ("feast", "a big special meal"),
    ("bargain", "something bought cheaply"), ("debt", "money you owe"), ("profit", "money you gain from selling"),
    ("tax", "money everyone pays to the town"), ("law", "a rule everyone must follow"), ("crime", "breaking the law"),
    ("judge", "someone who decides what is fair"), ("vote", "choosing by saying what you want"),
    ("hero", "someone brave who helps others"), ("coward", "someone who runs away from danger"),
    ("rumour", "a story that may not be true"), ("gossip", "talking about other people's business"),
    ("lullaby", "a soft song to help babies sleep"), ("riddle", "a puzzle in words"), ("legend", "an old story people tell"),
    ("adventure", "an exciting journey"), ("treasure", "something very valuable"), ("map", "a picture of where places are"),
    ("compass", "a tool that points north"), ("horizon", "where the land meets the sky"), ("valley", "low land between hills"),
    ("harbour", "a safe place for boats"), ("orchard", "a field of fruit trees"), ("meadow", "a field of grass and flowers"),
    ("hive", "a home for bees"), ("nest", "a home birds make"), ("burrow", "a hole an animal lives in"),
    ("herd", "a big group of animals"), ("flock", "a group of birds or sheep"), ("litter", "babies born together"),
    ("seedling", "a very young plant"), ("blossom", "the flowers on a tree"), ("root", "the part of a plant under the ground"),
    ("medicine", "something that helps you get better"), ("bandage", "cloth to cover a cut"), ("fever", "being too hot when sick"),
    ("patience", "waiting calmly"), ("kindness", "being kind"), ("loneliness", "feeling alone"), ("joy", "great happiness"),
    ("grief", "deep sadness when someone dies"), ("anger", "a hot feeling when something is unfair"),
    ("fear", "the feeling of being scared"), ("surprise", "something you did not expect"), ("silence", "no sound at all"),
    ("habit", "something you do often without thinking"), ("chore", "a small job you must do at home"),
    ("tradition", "something people have done for a long time"), ("custom", "the usual way people do something"),
]
LEGS.update({"eagle": "two", "swan": "two", "hawk": "two", "penguin": "two", "moth": "six", "crab": "ten",
             "worm": "no", "snail": "no", "seal": "no", "lizard": "four"})
