"""Everyday chatter about anything: likes, experiences, wishes, plans, descriptions and favours,
over a large lexicon of common English nouns, verbs and adjectives sorted into kinds, so the corpus
covers the words people actually use while every verb only meets things it makes sense with."""
import random

R = random.Random()


def pick(xs):
    return R.choice(xs)


def nouns(text):
    """'leaf/leaves' gives its own plural; other words just add s."""
    return [tuple(p.split("/")) if "/" in p else (p, p + "s") for p in text.split()]


KINDS = {
    "food": nouns("""apple banana cherry/cherries lemon orange peach/peaches pear plum grape strawberry/strawberries
        carrot potato/potatoes tomato/tomatoes onion cabbage pumpkin mushroom bean pea nut walnut cookie cake pie
        biscuit sandwich/sandwiches pancake muffin sausage egg cheese loaf/loaves melon berry/berries pickle
        dumpling tart apricot fig cucumber radish/radishes turnip lettuce leek beetroot olive almond hazelnut
        pastry/pastries bun scone waffle omelette porridge stew soup salad custard jelly/jellies toffee/toffees
        raspberry/raspberries blueberry/blueberries gooseberry/gooseberries coconut pineapple mango/mangoes"""),
    "object": nouns("""cup plate bowl spoon fork knife/knives pot pan kettle jar bottle basket bucket box/boxes
        lamp candle clock mirror blanket pillow book letter map pen pencil notebook ball doll toy kite puzzle
        drum flute guitar violin bell whistle hammer saw axe shovel rake broom brush/brushes comb towel key
        rope needle bag purse wallet ring necklace bracelet button lantern torch chest/chests stool
        teapot jug ladle sieve scissors/scissors thimble spindle quill inkpot ruler compass telescope hourglass
        umbrella fan paintbrush/paintbrushes chisel trowel sickle pitchfork lockbox/lockboxes wheelbarrow
        bucket shield helmet sword bow arrow quiver rod net cage/cages saddlebag horseshoe"""),
    "clothing": nouns("""hat cap coat jacket shirt dress/dresses skirt scarf/scarves glove sock shoe boot belt cloak
        apron sweater vest/vests tunic robe trousers/trousers sandal slipper mitten earmuff bonnet hood veil
        uniform waistcoat nightgown"""),
    "animal": nouns("""dog cat horse cow pig sheep/sheep goat chicken duck goose/geese rabbit fox/foxes wolf/wolves
        bear deer/deer mouse/mice bird owl crow eagle fish/fish frog turtle bee butterfly/butterflies squirrel
        hedgehog otter beaver parrot donkey llama lamb calf/calves puppy/puppies kitten pony/ponies
        badger mole weasel ferret hamster robin sparrow pigeon swan heron peacock ostrich camel zebra giraffe
        lion tiger elephant monkey panda kangaroo penguin seal whale dolphin octopus/octopuses crab lobster
        snail beetle ladybird ant moth dragonfly/dragonflies grasshopper"""),
    "place": nouns("""house/houses cottage barn shed stable tower castle church/churches school shop market inn
        tavern bridge garden library bakery farm mill/mills harbour lighthouse village town city/cities
        museum theatre hospital prison palace temple monastery/monasteries fortress/fortresses windmill
        greenhouse orchard vineyard workshop forge kitchen cellar attic courtyard square fountain well"""),
    "nature": nouns("""river lake pond stream sea/seas beach/beaches island hill mountain valley cave forest field
        meadow cliff waterfall flower tree bush/bushes oak willow rose daisy/daisies sunflower
        volcano/volcanoes glacier canyon dune marsh/marshes swamp jungle desert lagoon reef bay creek
        spring tulip lily/lilies poppy/poppies violet fern maple pine birch cactus/cacti mushroom"""),
    "vehicle": nouns("cart wagon boat ship raft sled carriage/carriages canoe sailboat barge rowboat"),
    "game": nouns("game puzzle riddle race card"),
    "song": nouns("song tune lullaby/lullabies hymn ballad"),
}
COLOURS = ["red", "blue", "green", "yellow", "white", "black", "brown", "orange", "purple", "pink", "grey"]
FEEL = ["happy", "sad", "tired", "excited", "bored", "scared", "angry", "proud", "nervous", "calm",
        "hungry", "thirsty", "cold", "warm", "sleepy", "lonely", "silly", "grateful", "curious", "surprised"]
ADJ = {
    "food": ["sweet", "sour", "fresh", "ripe", "juicy", "crunchy", "soft", "tasty", "spicy", "salty", "warm", "delicious"],
    "object": ["old", "new", "shiny", "heavy", "light", "broken", "wooden", "small", "big", "useful", "pretty", "dusty"],
    "clothing": ["warm", "soft", "woollen", "leather", "old", "new", "colourful", "torn", "fancy", "comfortable"],
    "animal": ["fluffy", "fast", "lazy", "friendly", "wild", "tiny", "huge", "noisy", "gentle", "clever", "grumpy"],
    "place": ["old", "new", "big", "quiet", "busy", "cozy", "cold", "beautiful", "crowded", "lonely", "famous"],
    "nature": ["beautiful", "wild", "quiet", "deep", "tall", "green", "peaceful", "dangerous", "huge", "hidden"],
    "vehicle": ["fast", "slow", "old", "new", "creaky", "sturdy", "leaky", "painted"],
    "game": ["fun", "silly", "long", "hard", "easy", "old", "new", "exciting", "boring"],
    "song": ["sad", "happy", "old", "new", "beautiful", "silly", "long", "short", "catchy"],
}

# base, past, participle, -ing, kinds of object it takes
VERBS = [(v.split("/")[0], v.split("/")[1], v.split("/")[2], v.split("/")[3], ks.split(","))
         for v, ks in (line.split() for line in """
eat/ate/eaten/eating food
cook/cooked/cooked/cooking food
bake/baked/baked/baking food
pick/picked/picked/picking food,nature
grow/grew/grown/growing food
buy/bought/bought/buying food,object,clothing,animal,vehicle
sell/sold/sold/selling food,object,clothing,animal,vehicle
find/found/found/finding object,clothing,animal
lose/lost/lost/losing object,clothing
fix/fixed/fixed/fixing object,clothing,vehicle
break/broke/broken/breaking object
carry/carried/carried/carrying object,food
paint/painted/painted/painting object,place,vehicle,nature
draw/drew/drawn/drawing animal,place,nature,object
make/made/made/making food,object,clothing,game
write/wrote/written/writing song
learn/learned/learned/learning song,game
hum/hummed/hummed/humming song
mend/mended/mended/mending clothing,object
wash/washed/washed/washing clothing,object,animal
feed/fed/fed/feeding animal
chase/chased/chased/chasing animal
ride/rode/ridden/riding vehicle
visit/visited/visited/visiting place,nature
build/built/built/building place,vehicle
clean/cleaned/cleaned/cleaning place,object,vehicle
explore/explored/explored/exploring place,nature
sing/sang/sung/singing song
play/played/played/playing game
win/won/won/winning game
borrow/borrowed/borrowed/borrowing object,clothing,vehicle
lend/lent/lent/lending object,clothing,vehicle
hide/hid/hidden/hiding object,food
see/saw/seen/seeing animal,nature,place
""".strip().splitlines())]
PEOPLE = ["friend", "brother", "sister", "mother", "father", "cousin", "neighbour", "grandma", "grandpa", "uncle", "aunt"]


def art(w):
    vowel = w[0] in "aeiou" and not w.startswith(("use", "uni", "eu"))
    return ("an " if vowel else "a ") + w


def thing(kinds):
    k = pick(kinds)
    return k, pick(KINDS[k])


def ex_like():
    k, n = thing(["food", "animal", "nature", "game", "clothing"])
    q = pick([f"do you like {n[1]}?", f"what do you think of {n[1]}?", f"are you fond of {n[1]}?"])
    a = pick([f"yes, i love {n[1]}.", f"not really, {n[1]} are not for me.", f"i like {n[1]}, but not too much.",
              f"{n[1]}? they're the best.", f"i used to hate {n[1]}, but now i like them."])
    return [q, a] + ([pick(["me too.", "really?", "fair enough.", f"i prefer {pick(KINDS[k])[1]}."])] if R.random() < 0.5 else [])


def verb_object():
    v = pick(VERBS)
    k, n = thing(v[4])
    return v, k, n


def ex_experience():
    v, k, n = verb_object()
    q = pick([f"have you ever {v[2]} {art(n[0])}?", f"have you {v[2]} {art(n[0])} before?"])
    a = pick([f"yes, once, {pick(['last year', 'when i was little', 'a long time ago', 'just yesterday'])}.",
              "no, never.", "many times!", f"no, but i'd like to try.", f"i {v[1]} one last week."])
    if a.startswith("no") or R.random() < 0.5:
        return [q, a]
    return [q, a, pick(["what was it like?", "how was it?"]),
            pick(["it was fun.", f"it was {pick(ADJ[k])}.", "i'll tell you about it later.", "i'd do it again."])]


def ex_want():
    k, n = thing(["object", "clothing", "animal", "food", "vehicle"])
    adj = pick(ADJ[k])
    q = pick(["what do you want for your birthday?", "if you could have anything, what would it be?",
              "what are you wishing for?", "what would make you happy today?"])
    a = pick([f"{art(adj + ' ' + n[0])}.", f"i'd love {art(n[0])}.", f"maybe {art(adj + ' ' + n[0])}."] +
             ([f"just some {n[1]}."] if k == "food" else []))
    return [q, a, pick(["i'll see what i can do.", "that's easy.", "good choice.", "i'll remember that."])]


def ex_describe():
    k, n = thing(["object", "clothing", "animal", "place", "vehicle"])
    a1, a2 = R.sample(ADJ[k], 2)
    q = pick([f"what does your {n[0]} look like?", f"tell me about your {n[0]}.", f"what's your {n[0]} like?"])
    a = pick([f"it's {a1} and {a2}.", f"it's very {a1}.", f"{a1}, a bit {a2}, and {pick(COLOURS)}."])
    return [q, a] + ([pick(["sounds nice.", "i'd like to see it.", "really?", "lovely."])] if R.random() < 0.5 else [])


def ex_ask_thing():
    k, n = thing(["object", "clothing", "food"])
    q = pick([f"can you pass me the {n[0]}?", f"could you bring me {art(n[0])}?", f"do you have {art(n[0])} i could use?",
              f"where are the {n[1]}?", f"hand me that {n[0]}, please."])
    a = pick(["here you go.", "sure, here.", "sorry, i don't have one.", f"they're by the {pick(['door', 'window', 'fire', 'table'])}.",
              "get it yourself!", "just a moment."])
    return [q, a] + ([pick(["thanks.", "thank you.", "fine, i will.", "found them."])] if R.random() < 0.6 else [])


def ex_doing():
    v, k, n = verb_object()
    q = pick(["what are you doing?", "what are you up to?", "busy?", "what's that you're doing?"])
    a = pick([f"i'm {v[3]} the {n[1]}.", f"just {v[3]} {art(n[0])}.", f"trying to {v[0]} this {n[0]}."])
    return [q, a, pick(["can i help?", "looks hard.", "have fun.", "need a hand?", "don't let me stop you."])]


def ex_yesterday():
    v, k, n = verb_object()
    q = pick(["what did you do yesterday?", "how was your weekend?", "what did you do today?"])
    a = pick([f"i {v[1]} {art(n[0])}.", f"i {v[1]} some {n[1]} with my {pick(PEOPLE)}.",
              f"nothing much, i {v[1]} the {n[1]}.", f"i was {pick(FEEL)}, so i stayed home."])
    return [q, a, pick(["sounds nice.", "that's all?", "lucky you.", "i wish i'd come.", "sounds like a good day."])]


def ex_compare():
    k = pick(list(KINDS))
    a, b = R.sample(KINDS[k], 2)
    adj = pick(["better", "nicer", "more fun", "more useful", "prettier"] if k != "food" else ["tastier", "sweeter", "better"])
    q = f"which is {adj}, {art(a[0])} or {art(b[0])}?"
    ans = pick([f"{art(a[0])}, for sure.", f"i'd say {art(b[0])}.", "they're both good.", "hard to say.", f"definitely the {b[0]}."])
    return [q, ans] + ([pick(["i agree.", "no way!", "why?", "hmm, maybe."])] if R.random() < 0.5 else [])


def ex_how_feel():
    f = pick(FEEL)
    q = pick(["how do you feel?", "how are you feeling now?", "are you alright?"])
    v, k, n = verb_object()
    reason = pick(["i don't know why.", "it's been a long day.", "the weather, maybe.", f"i just {v[1]} {art(n[0])}."])
    return [q, f"i feel {f}. {reason}", pick(["i see.", "i hope it passes.", "that's good.", "want to talk?", "same here."])]


def ex_count():
    k, n = thing(["object", "food", "animal"])
    num = pick(["two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "a dozen", "no"])
    q = pick([f"how many {n[1]} do you have?", f"how many {n[1]} are there?", f"do we have enough {n[1]}?"])
    return [q, pick([f"{num} {n[1]}.", f"about {num}.", "not enough.", "more than enough."]).replace("about no", "none")]


EXCHANGES = [ex_like, ex_experience, ex_want, ex_describe, ex_ask_thing, ex_doing, ex_yesterday, ex_compare,
             ex_how_feel, ex_count]


def chatter(rng):
    global R
    R = rng
    d = pick(EXCHANGES)()
    if R.random() < 0.35:
        d += pick(EXCHANGES)()
    return d
