#!/usr/bin/env python3
"""Generates a large, varied English corpus of everyday conversations and scenes.

Each dialogue is a list of utterances, one per line, and dialogues are separated by a blank line.
Speakers alternate, so the line after a question is its answer. The corpus mixes:
  * scripted scenarios (greetings, markets, doctors, weather, danger, cooking, school, work,
    travel, family, feelings, apologies, arguments, jokes, directions, building, farming...),
    each turn picked from many paraphrases and filled from word lists, so almost no two
    dialogues are the same;
  * short scenes told in the first or third person ("this morning i fed the chickens"), and
    sentences from a small grammar (grammar.py) where verbs only take objects that fit them;
  * questions and answers about the world (facts.py): animals, tools, food, colours, opposites,
    numbers, days, months and the meanings of words;
  * everyday chatter (chatter.py) about likes, experiences, wishes and favours over a large
    lexicon of common nouns, verbs and adjectives;
  * talk about the here and now (herenow.py): what the speaker sees, smells, feels, does and
    wants, the lines a villager's concept links fill in;
  * the hand-written conversations in dialogues.txt.

Usage: generate.py OUT_FILE [DIALOGUES] [SEED] [--no-hand-written]
"""
import os
import random
import sys

import chatter
import facts
import grammar
import herenow
import trees

R = random.Random()


def pick(xs):
    return R.choice(xs)


def maybe(p=0.5):
    return R.random() < p


# ---------------------------------------------------------------------------------------------
# Word lists
# ---------------------------------------------------------------------------------------------

NAMES = """anna ben clara david emma finn grace henry isla jack kate leo mia noah olivia peter
quinn rosa sam tara uma victor wendy xavier yara zack alice bob carla dan ella frank gina hugo
iris james lena max nina oscar paula ray sofia tom vera will amelia arthur bella charlie daisy
edward freya george hannah isaac julia kai lucy martin nora owen poppy ruby simon theo violet
jonah elise marco priya ahmed mei kofi lars ingrid pablo yuki omar fatima ivan dmitri chloe
lily ethan zoe adam ruth joshua""".split()

FAMILY = """mother father mom dad sister brother grandma grandpa aunt uncle cousin son daughter
wife husband baby niece nephew""".split()

FRIENDS = ["friend", "best friend", "neighbour", "neighbor", "teacher", "boss", "doctor",
           "the baker", "the farmer", "the blacksmith", "the guard", "the priest", "the librarian",
           "the fisherman", "the shepherd", "the cleric", "the butcher", "the mayor", "my partner"]

FOODS = """bread apples carrots potatoes soup cake cookies pie fish beef chicken pork mutton
eggs milk cheese berries melon pumpkin pie stew rice noodles salad sandwiches honey porridge
beetroot soup baked potatoes mushroom stew rabbit stew pancakes toast butter oranges grapes
pears plums tomatoes beans corn onions garlic jam""".split()
FOODS = [f.replace("_", " ") for f in FOODS] + ["mushroom stew", "rabbit stew", "baked potatoes",
                                                "pumpkin pie", "apple pie", "fresh bread",
                                                "roast chicken", "fried eggs", "hot soup"]
DRINKS = ["water", "milk", "tea", "coffee", "juice", "apple juice", "hot chocolate", "soup",
          "lemonade", "cider"]

ITEMS = """bread wheat apples carrots potatoes fish wool leather paper books arrows bows swords
shovels pickaxes axes hoes shears buckets torches candles lanterns rope string cloth boots
hats coats shirts emeralds iron gold coal copper glass bricks planks logs stone sand clay
flowers seeds eggs milk honey sugar salt baskets pots chairs tables beds maps compasses
clocks keys nails saddles""".split()
ONE_ITEMS = ["a sword", "a bow", "a shovel", "a pickaxe", "an axe", "a hoe", "a bucket", "a torch",
             "a lantern", "a book", "a map", "a compass", "a clock", "a key", "a saddle", "a hat",
             "a coat", "a basket", "a chair", "a table", "a bed", "a cake", "a pie", "a boat",
             "a fishing rod", "a shield", "a helmet", "a pair of boots", "a loaf of bread",
             "a bottle of milk", "a jar of honey", "a bag of seeds", "a pot", "a candle"]

ANIMALS = """cow cows pig pigs sheep chicken chickens horse horses dog dogs cat cats rabbit
rabbits fox foxes wolf wolves bee bees fish goat goats llama llamas parrot parrots frog frogs
turtle turtles bear bears duck ducks owl owls deer mouse mice bird birds squirrel donkey
donkeys""".split()
PETS = ["dog", "cat", "parrot", "horse", "rabbit", "wolf", "fox", "goat", "llama", "donkey"]
MONSTERS = ["zombie", "zombies", "skeleton", "skeletons", "creeper", "creepers", "spider",
            "spiders", "witch", "pillagers", "raiders", "a wolf pack", "slimes", "phantoms",
            "drowned", "an enderman", "bandits", "a bear", "thieves"]

PLACES = """market farm field forest river lake beach mountain cave mine village town city
church library school bakery smithy tavern inn well bridge square harbor dock garden orchard
meadow hill valley desert swamp jungle castle tower house barn stable mill workshop shop
kitchen bedroom attic cellar hospital station park road path camp""".split()
THE_PLACES = ["the " + p for p in PLACES]
TOWNS = ["the village", "the next village", "the city", "the coast", "the mountains",
         "the old town", "the capital", "the desert town", "the river village", "the north",
         "the south", "the island", "the valley", "the forest village", "my hometown"]

TIMES = ["this morning", "this afternoon", "this evening", "tonight", "yesterday", "last night",
         "today", "tomorrow", "earlier", "an hour ago", "at noon", "at dawn", "at sunset",
         "last week", "next week", "on monday", "on friday", "on the weekend", "soon", "later"]
PAST_TIMES = ["this morning", "yesterday", "last night", "earlier", "an hour ago", "at dawn",
              "last week", "a while ago", "just now", "the other day", "last summer",
              "when i was young", "years ago", "on monday"]
FUTURE_TIMES = ["tomorrow", "tonight", "later", "next week", "soon", "after lunch",
                "in the morning", "this evening", "on the weekend", "next summer", "one day"]
DAYS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
SEASONS = ["spring", "summer", "autumn", "fall", "winter"]

WEATHER_GOOD = ["sunny", "warm", "nice", "lovely", "bright", "clear", "beautiful", "pleasant",
                "mild", "calm"]
WEATHER_BAD = ["rainy", "cold", "wet", "windy", "stormy", "freezing", "grey", "foggy", "hot",
               "cloudy", "snowy", "miserable"]

GOOD_FEEL = ["happy", "good", "great", "fine", "glad", "excited", "proud", "calm", "relaxed",
             "cheerful", "well", "wonderful", "thankful", "hopeful", "rested", "content"]
BAD_FEEL = ["sad", "tired", "sick", "worried", "angry", "scared", "lonely", "bored", "upset",
            "hungry", "cold", "sore", "nervous", "grumpy", "exhausted", "homesick", "stressed",
            "confused", "hurt", "afraid"]

JOBS = ["farmer", "baker", "fisherman", "blacksmith", "librarian", "shepherd", "butcher",
        "cleric", "builder", "miner", "teacher", "doctor", "guard", "cook", "carpenter",
        "weaver", "trader", "merchant", "gardener", "hunter", "nurse", "tailor", "cartographer",
        "mason", "fletcher", "armorer", "toolsmith", "leatherworker", "potter", "brewer"]
WORK_ACTS = ["fixing the fence", "planting wheat", "feeding the animals", "baking bread",
             "chopping wood", "mining stone", "building a wall", "fishing", "cleaning the house",
             "mending clothes", "sorting books", "harvesting carrots", "milking the cows",
             "shearing the sheep", "digging a well", "painting the door", "repairing the roof",
             "cooking dinner", "washing the dishes", "selling apples", "making tools",
             "writing letters", "looking after the kids", "picking berries", "collecting eggs",
             "hauling water", "watering the garden", "sweeping the floor", "hunting rabbits",
             "drawing a map", "smelting iron", "making candles", "weaving baskets"]
HOBBIES = ["reading", "fishing", "singing", "dancing", "painting", "walking", "swimming",
           "climbing", "gardening", "cooking", "playing games", "telling stories", "riding horses",
           "exploring caves", "watching the stars", "drawing", "playing music", "building things",
           "running", "knitting", "collecting shells", "playing cards", "sailing", "hiking"]
TOOLS = ["hammer", "saw", "shovel", "pickaxe", "axe", "hoe", "bucket", "ladder", "rope",
         "knife", "needle", "broom", "torch", "lantern", "shears", "fishing rod", "map", "key"]
COLORS = ["red", "blue", "green", "yellow", "white", "black", "brown", "orange", "purple",
          "pink", "grey", "gold", "silver"]
BODY = ["head", "arm", "leg", "foot", "hand", "back", "knee", "stomach", "tooth", "throat",
        "ear", "eye", "shoulder", "neck", "finger", "ankle", "wrist", "chest"]
ILLNESS = ["a cold", "a fever", "a cough", "a headache", "a sore throat", "a stomach ache",
           "the flu", "a rash", "a toothache", "a bad back", "a sprained ankle", "a cut",
           "a burn", "a bee sting", "a spider bite"]
NUMBERS = ["one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
           "eleven", "twelve", "fifteen", "twenty", "thirty", "fifty", "a hundred"]
SMALL_NUMBERS = ["two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                 "twelve", "twenty"]
COINS = ["emeralds", "coins", "silver", "gold coins", "copper coins"]
BLOCKS = ["dirt", "stone", "wood", "planks", "cobblestone", "bricks", "sand", "glass", "clay",
          "logs", "gravel", "wool", "snow"]
DIRECTIONS = ["left", "right", "straight ahead", "north", "south", "east", "west",
              "up the hill", "down the hill", "across the bridge", "past the well",
              "behind the church", "next to the bakery", "along the river", "through the forest",
              "past the market", "around the corner", "over the stream", "by the big oak tree"]
SUBJECTS = ["reading", "writing", "numbers", "history", "the stars", "plants", "animals",
            "maps", "music", "cooking", "building", "medicine", "farming", "languages", "art"]
GAMES = ["tag", "hide and seek", "cards", "chess", "dice", "catch", "football", "marbles",
         "a guessing game", "tic tac toe"]
EVENTS = ["the harvest festival", "a wedding", "a birthday party", "the market day",
          "a funeral", "the spring fair", "a feast", "a dance", "the new year", "a baby's naming",
          "the midsummer fire", "a picnic", "a concert", "a meeting at the town hall"]
GIFTS = ["flowers", "a cake", "a book", "a necklace", "a ring", "a scarf", "a poem", "a painting",
         "a new hat", "some apples", "a wooden toy", "a song", "a letter", "a puppy", "a kitten"]


def name():
    return pick(NAMES).capitalize()


def a_or_an(word):
    return ("an " if word[0] in "aeiou" else "a ") + word


# ---------------------------------------------------------------------------------------------
# Speech acts: many ways to say the same thing
# ---------------------------------------------------------------------------------------------

def greet(to=None):
    to = to or name()
    return pick([
        "hello.", "hi.", "hey.", "hello there.", "hi there.", "good morning.", "good afternoon.",
        "good evening.", "morning.", f"hello {to}.", f"hi {to}.", f"hey {to}.",
        f"good morning {to}.", f"morning {to}!", "hey, how is it going?", "well, hello!",
        f"oh, hi {to}!", "howdy.", "greetings.", f"hey {to}, long time no see!", "hiya.",
    ])


def greet_back(to=None):
    to = to or name()
    return pick([
        "hello!", "hi!", "hey.", "oh, hello.", f"hi {to}.", f"hello {to}!", "good morning to you too.",
        "morning!", "hey there.", "hi, nice to see you.", "oh hi, i did not see you there.",
        f"hey {to}, good to see you.", "hello, hello!", "well hi!", "afternoon.",
    ])


def how_are_you():
    return pick([
        "how are you?", "how are you doing?", "how is it going?", "how have you been?",
        "are you well?", "how are things?", "what's new?", "how is life?", "how do you feel today?",
        "you doing okay?", "how was your day?", "how are you today?", "all good?",
        "how is everything?", "how is your family?", "what have you been up to?",
    ])


def fine():
    return pick([
        "i am fine, thank you.", "i'm good, thanks.", "pretty good.", "not bad.", "very well, thanks.",
        f"i feel {pick(GOOD_FEEL)} today.", "great, thank you for asking.", "can't complain.",
        "i am doing well.", "all good here.", "good, and you?", "fine thanks, and you?",
        f"i'm {pick(GOOD_FEEL)}, the weather is {pick(WEATHER_GOOD)}.", "never better!",
        "much better than yesterday.", "busy, but good.", "same as always.",
    ])


def not_fine():
    return pick([
        f"not great, i feel {pick(BAD_FEEL)}.", f"i'm a bit {pick(BAD_FEEL)} today.",
        "i have been better.", f"honestly, i'm {pick(BAD_FEEL)}.", "not so good.",
        f"i didn't sleep well, so i'm {pick(BAD_FEEL)}.", f"i have {pick(ILLNESS)}.",
        f"my {pick(BODY)} hurts.", "it has been a long day.", "could be better.",
        f"i'm {pick(BAD_FEEL)} because my {pick(FAMILY)} is sick.",
        f"i lost my {pick(TOOLS)} and i am {pick(BAD_FEEL)}.",
    ])


def thanks():
    return pick([
        "thank you.", "thanks.", "thanks a lot.", "thank you so much.", "many thanks.",
        "i really appreciate it.", "that is very kind of you.", "cheers.", "thanks, you're the best.",
        "oh, thank you!", "how kind, thank you.", "thanks for your help.", "i owe you one.",
    ])


def welcome():
    return pick([
        "you're welcome.", "no problem.", "any time.", "my pleasure.", "don't mention it.",
        "happy to help.", "it was nothing.", "of course.", "glad i could help.", "sure thing.",
        "no worries.",
    ])


def bye():
    return pick([
        "goodbye.", "bye.", "see you later.", "see you.", "bye for now.", "take care.",
        "have a nice day.", "good night.", "see you tomorrow.", "i have to go now, bye.",
        "catch you later.", "farewell.", "until next time.", "well, i should get going.",
        "see you around.", "have a good evening.", "i'll see you soon.", "i must be off.",
    ])


def bye_back():
    return pick([
        "bye!", "goodbye.", "see you.", "take care.", "you too.", "bye, take care.",
        "see you later.", "good night.", "have a good one.", "safe travels.", "until then.",
        "okay, bye.", "nice talking to you.", "see you soon.",
    ])


def yes():
    return pick(["yes.", "yeah.", "sure.", "of course.", "okay.", "yes, please.", "alright.",
                 "certainly.", "why not.", "yep.", "absolutely.", "sounds good.", "i'd love to.",
                 "definitely.", "fine by me.", "i think so."])


def no():
    return pick(["no.", "no, thank you.", "nope.", "not really.", "i don't think so.",
                 "no, sorry.", "maybe another time.", "i'd rather not.", "not today.",
                 "no way.", "i can't, sorry.", "not now."])


def sorry():
    return pick(["i'm sorry.", "sorry.", "i am so sorry.", "my apologies.", "forgive me.",
                 "oh no, sorry about that.", "i didn't mean to.", "that was my fault, sorry.",
                 "i apologize.", "sorry, my mistake."])


def forgive():
    return pick(["it's okay.", "that's alright.", "don't worry about it.", "no harm done.",
                 "i forgive you.", "it happens.", "never mind.", "it's fine, really.",
                 "apology accepted.", "we all make mistakes."])


def dont_understand():
    return pick(["sorry, what?", "what do you mean?", "i don't understand.", "can you say that again?",
                 "pardon?", "what did you say?", "huh?", "i'm not sure what you mean.",
                 "could you repeat that?", "say again?"])


# ---------------------------------------------------------------------------------------------
# Scenarios
# ---------------------------------------------------------------------------------------------

def sc_small_talk():
    a, b = name(), name()
    d = [greet(b), greet_back(a), how_are_you()]
    if maybe(0.75):
        d.append(fine())
        if maybe():
            d.append(pick(["glad to hear it.", "good to hear.", "that's nice.", "wonderful."]))
            d.append(how_are_you() if maybe(0.3) else pick(["and you?", "what about you?", "you?"]))
            d.append(fine() if maybe(0.6) else not_fine())
    else:
        d.append(not_fine())
        d.append(pick(["oh no, i'm sorry to hear that.", "that's too bad.", "oh dear.",
                       "i hope you feel better soon.", "what happened?", "is there anything i can do?"]))
        d.append(pick(["thanks, i'll be okay.", "it's nothing, really.", "i just need some rest.",
                       "maybe a warm meal would help.", "you're kind to ask."]))
    if maybe(0.6):
        d += sc_weather_lines()
    d += [bye(), bye_back()]
    return d


def sc_weather_lines():
    good = maybe()
    w = pick(WEATHER_GOOD if good else WEATHER_BAD)
    lines = [pick([f"it is {w} today, isn't it?", f"what {w} weather!", f"lovely day, huh?" if good else "awful weather today.",
                   f"it's so {w} out.", f"looks {w} out there.", f"do you think it will stay {w}?"])]
    if good:
        lines.append(pick(["yes, perfect for working outside.", "i love days like this.",
                           "the crops will like the sun.", "let's enjoy it while it lasts.",
                           "it feels like summer.", f"nice for a walk to the {pick(PLACES)}."]))
    else:
        lines.append(pick(["i hope it clears up soon.", "i'm staying inside today.",
                           "at least the fields get water.", "bring a coat if you go out.",
                           "the roads will be muddy.", "it might storm later.",
                           "good day for a fire and a book."]))
    if maybe(0.3):
        lines.append(pick([f"they say it will be {pick(WEATHER_GOOD + WEATHER_BAD)} {pick(FUTURE_TIMES)}.",
                           f"i miss {pick(SEASONS)}.", f"{pick(SEASONS)} is my favourite season.",
                           "the sky looks strange.", "i can smell rain coming."]))
        lines.append(pick(["really?", "we will see.", "me too.", "i hope so.", "let's hope not.", "same here."]))
    return lines


def sc_weather():
    return [greet()] + sc_weather_lines() + ([bye(), bye_back()] if maybe() else [])


def sc_market():
    item = pick(ITEMS)
    price = pick(SMALL_NUMBERS)
    coin = pick(COINS)
    d = []
    if maybe(0.7):
        d += [pick(["hello, what can i get for you?", "welcome! looking for anything special?",
                    "good day, what do you need?", "fresh goods today, come take a look!",
                    "can i help you?"]),
              pick([f"i need some {item}.", f"do you have any {item}?", f"i'm looking for {item}.",
                    f"how much are the {item}?", f"i would like {pick(SMALL_NUMBERS)} {item}, please.",
                    "just looking, thanks."])]
    else:
        d += [pick([f"excuse me, do you sell {item}?", f"how much for the {item}?",
                    f"are these {item} fresh?", f"what do you want for your {item}?"])]
    if d[-1] == "just looking, thanks.":
        d.append(pick(["take your time.", "let me know if you need anything.", "no problem."]))
        return d
    if maybe(0.85):
        d.append(pick([f"they are {price} {coin} each.", f"{price} {coin} for a bundle.",
                       f"for you, {price} {coin}.", f"those cost {price} {coin}.",
                       f"i can do {price} {coin}.", f"{price} {coin}, best in the village."]))
        if maybe(0.5):
            d.append(pick(["that's too much.", "that's expensive.", f"would you take {pick(SMALL_NUMBERS)}?",
                           "can you lower the price?", "hmm, that is a lot.", "i only have a few coins."]))
            if maybe():
                d.append(pick(["alright, you have a deal.", "fine, for you, a discount.",
                               "okay, but only today.", f"how about {pick(SMALL_NUMBERS)} then?"]))
                d.append(pick(["deal.", "sounds fair.", "okay, i'll take them.", "thank you, here you go."]))
            else:
                d.append(pick(["sorry, that's my final price.", "i can't go lower.", "the price is fair."]))
                d.append(pick(["then i'll leave it.", "okay, i'll pay.", "maybe next time.", "fine."]))
        else:
            d.append(pick(["i'll take them.", "great, here you go.", "okay, here are the coins.",
                           "perfect, wrap them up please.", f"i'll take {pick(SMALL_NUMBERS)}."]))
        d.append(pick(["thank you, come again!", "enjoy them.", "pleasure doing business.",
                       "have a good day.", "here is your change.", "thanks for buying."]))
    else:
        d.append(pick([f"sorry, i ran out of {item}.", f"no, i don't sell {item}.",
                       f"the {item} come in {pick(FUTURE_TIMES)}.", f"try the {pick(JOBS)}, he has some.",
                       f"try the {pick(JOBS)}, she has some."]))
        d.append(pick(["oh, okay.", "thanks anyway.", "i'll come back later.", "that's a shame."]))
    return d


def sc_directions():
    place = pick(THE_PLACES)
    d = [pick([f"excuse me, how do i get to {place}?", f"where is {place}?",
               f"could you tell me the way to {place}?", f"i'm lost, where is {place}?",
               f"is {place} far from here?", f"do you know where {place} is?"])]
    if maybe(0.85):
        steps = [f"go {pick(DIRECTIONS)}" for _ in range(R.randint(1, 3))]
        d.append(pick(["sure. ", "of course. ", "yes. ", "", "it's easy. "]) + ", then ".join(steps) + ".")
        if maybe(0.5):
            d.append(pick(["is it far?", "how long does it take?", "can i walk there?"]))
            d.append(pick(["no, just a few minutes.", "about ten minutes walking.", "it's quite far, take a horse.",
                           "not far at all.", "half a day on foot.", "you can see it from the hill."]))
        d.append(thanks())
        d.append(welcome())
    else:
        d.append(pick(["sorry, i'm not from here.", "i don't know, ask the guard.", "no idea, sorry.",
                       "i'm new here too."]))
        d.append(pick(["okay, thanks anyway.", "no problem.", "i'll ask someone else."]))
    return d


def sc_doctor():
    part = pick(BODY)
    ill = pick(ILLNESS)
    d = [pick(["hello, what seems to be the problem?", "come in. what brings you here?",
               "good morning, how can i help you?", "please sit down. how are you feeling?"]),
         pick([f"my {part} hurts.", f"i think i have {ill}.", f"i've had {ill} since {pick(PAST_TIMES)}.",
               f"i hurt my {part} {pick(PAST_TIMES)}.", "i feel dizzy and weak.", "i can't sleep at night.",
               f"i fell and my {part} is swollen.", "i keep coughing."]),
         pick(["how long has it been like this?", "does it hurt when i press here?", "do you have a fever?",
               "when did it start?", "have you eaten anything strange?", "let me take a look."])]
    d.append(pick(["about two days.", "yes, it hurts a lot.", "a little.", "since yesterday.", "for a week now.",
                   "no, i don't think so.", "i'm not sure."]))
    d.append(pick(["it's nothing serious.", "you need rest and lots of water.", "take this medicine twice a day.",
                   "keep it warm and don't use it for a few days.", "drink hot tea with honey.",
                   "eat something light and sleep.", "i'll put a bandage on it.",
                   "come back if it gets worse."]))
    d.append(pick(["thank you, doctor.", "how much do i owe you?", "will i be okay?", "thanks, i feel better already."]))
    if d[-1] == "will i be okay?":
        d.append(pick(["yes, you will be fine.", "of course, give it a few days.", "don't worry, you'll heal."]))
    elif d[-1] == "how much do i owe you?":
        d.append(pick([f"{pick(SMALL_NUMBERS)} {pick(COINS)}.", "nothing, just get well.", "pay me when you can."]))
    return d


def sc_meal():
    food = pick(FOODS)
    d = [pick(["what's for dinner?", "i'm hungry, what are we eating?", "is lunch ready?",
               "what are you cooking?", "something smells good!", "what should we eat tonight?"]),
         pick([f"{food}.", f"i'm making {food}.", f"we have {food} and {pick(FOODS)}.",
               f"{food}, your favourite.", f"how about {food}?", "nothing yet, i was waiting for you."])]
    if maybe(0.7):
        d.append(pick(["yum, i love that.", "again?", "sounds delicious.", "great, i'm starving.",
                       "can i help?", f"can we have {pick(FOODS)} instead?", "i don't really like that."]))
        if d[-1] == "can i help?":
            d.append(pick([f"yes, cut the {pick(FOODS)}.", "set the table, please.", "fetch some water from the well.",
                           "stir the pot.", "wash your hands first."]))
        elif "instead" in d[-1] or "don't" in d[-1]:
            d.append(pick(["eat what you're given.", "maybe tomorrow.", "fine, i'll make both.",
                           "you're too picky.", "okay, okay."]))
        else:
            d.append(pick(["it's almost ready.", "sit down, it will be ten minutes.", "come and eat.",
                           "it's hot, be careful.", "there is plenty for everyone."]))
    if maybe(0.5):
        d += [pick(["this is delicious.", "mmm, very good.", "it needs more salt.", "can i have more?",
                    "you're a great cook."]),
              pick(["thank you.", "there's more in the pot.", "glad you like it.", "help yourself.",
                    "i used my grandma's recipe."])]
    return d


def sc_cooking_instructions():
    food = pick(FOODS)
    d = [pick([f"how do you make {food}?", f"can you teach me to make {food}?", f"what do i need for {food}?"])]
    steps = R.sample([f"first you need {pick(FOODS)} and {pick(FOODS)}.", "wash everything.",
                      "cut it into small pieces.", "put it in the pot with water.", "boil it for a while.",
                      "add a little salt.", "stir it slowly.", "let it cool down.", "bake it in the oven.",
                      "mix it with milk and eggs.", "wait until it turns golden.", "then you serve it hot."],
                     R.randint(2, 4))
    d.append(" ".join(steps))
    d.append(pick(["that sounds easy.", "how long does it take?", "is that all?", "i'll try it tonight."]))
    d.append(pick(["about an hour.", "yes, that's all.", "practice makes perfect.", "good luck!",
                   "tell me how it turns out."]))
    return d


def sc_danger():
    m = pick(MONSTERS)
    d = [pick([f"watch out! {m}!", f"run! there are {m} coming!", f"help! {m} near the {pick(PLACES)}!",
               f"did you see that? i think it was {m}.", f"there are {m} outside the walls!",
               "something is moving in the dark.", "i heard a noise behind the house."])]
    d.append(pick(["where?", "quick, get inside!", "stay behind me.", "don't panic.", "close the door!",
                   "ring the bell!", "get the guards!", "i'll get my sword."]))
    d.append(pick([f"over by the {pick(PLACES)}.", "behind the trees!", "it's getting closer!", "i don't know, i can't see.",
                   "they are everywhere!", "i'm scared."]))
    d.append(pick(["stay calm, we will be safe inside.", "light the torches.", "run to the church!",
                   "i'll protect you.", "keep quiet and stay down.", "go and warn the others."]))
    if maybe():
        d.append(pick(["i think it's gone now.", "are you hurt?", "that was close.", "is everyone okay?"]))
        d.append(pick(["yes, i'm fine.", "just a scratch.", "i'm still shaking.", "thank goodness.",
                       "we should build a better wall."]))
    return d


def sc_injury():
    part = pick(BODY)
    d = [pick(["ouch!", "ow, that hurts!", "ahh!", "oh no!"]),
         pick(["what happened?", "are you okay?", "did you hurt yourself?", "oh dear, let me see."]),
         pick([f"i hit my {part}.", f"i fell and hurt my {part}.", "i cut my finger.",
               f"a {pick(['bee', 'spider', 'dog', 'wolf'])} bit me.", "i burned my hand on the stove.",
               "i tripped over a rock.", "i fell off the ladder."])]
    d.append(pick(["let me help you up.", "sit down, i'll get a bandage.", "put some cold water on it.",
                   "can you move it?", "we should see the doctor.", "it's bleeding, hold this cloth on it."]))
    d.append(pick(["thank you.", "it's not that bad.", "i think i'm okay.", "it really hurts.", "yes, i can move it."]))
    return d


def sc_help_request():
    act = pick(WORK_ACTS)
    d = [pick([f"could you help me with {act}?", f"can you give me a hand {act}?", "i need some help.",
               f"would you mind helping me? i'm {act}.", "are you busy? i could use a hand.",
               f"i can't do this alone, will you help with {act}?"])]
    if maybe(0.7):
        d.append(pick(["sure, what do you need?", "of course.", "happy to help.", "yes, show me what to do.",
                       "give me a minute and i'll be there.", "okay, but only for an hour."]))
        d.append(pick([f"hold the {pick(TOOLS)} for me.", f"bring me the {pick(TOOLS)}.",
                       f"carry these {pick(ITEMS)} to the {pick(PLACES)}.", "lift this end.",
                       "just watch and pass me things.", "keep the ladder steady."]))
        d.append(pick(["like this?", "okay, got it.", "here you go.", "this is heavy!", "done."]))
        d.append(pick(["perfect.", "yes, exactly.", "careful!", "great, we're almost finished.", "thanks, that helps."]))
        d += [thanks(), welcome()]
    else:
        d.append(pick(["sorry, i'm busy right now.", "i can't, i have to work.", "maybe later?",
                       f"ask {name()}, he is free.", f"ask {name()}, she is free.", "not today, i'm too tired."]))
        d.append(pick(["okay, no problem.", "alright.", "later is fine.", "i understand."]))
    return d


def sc_work_talk():
    job = pick(JOBS)
    d = [pick(["what do you do?", "what is your job?", "what do you do for work?", "where do you work?",
               "how is work?", "are you busy at work these days?"])]
    if d[0] in ("how is work?", "are you busy at work these days?"):
        d.append(pick(["very busy.", "it's okay.", "quiet this week.", f"tiring, i spent all day {pick(WORK_ACTS)}.",
                       "i love it.", "my boss is too strict.", "the harvest keeps us busy."]))
    else:
        d.append(pick([f"i'm {a_or_an(job)}.", f"i work as {a_or_an(job)}.", f"i'm {a_or_an(job)}, like my father.",
                       f"i'm {a_or_an(job)}, like my mother.", f"i used to be {a_or_an(pick(JOBS))}, now i'm {a_or_an(job)}."]))
        d.append(pick(["do you like it?", "that sounds interesting.", "is it hard?", "how long have you done that?",
                       "what does that involve?"]))
        d.append(pick(["yes, very much.", "it's hard work but i like it.", "about ten years.", "since i was a kid.",
                       f"mostly {pick(WORK_ACTS)}.", "some days are better than others."]))
    if maybe():
        d.append(pick(["and you?", "what about you?", "what do you do?"]))
        d.append(pick([f"i'm {a_or_an(pick(JOBS))}.", "i'm looking for work.", "i help my family on the farm.",
                       "i'm still learning a trade.", "i don't work, i'm retired.", "i'm a student."]))
    return d


def sc_family():
    fam = pick(FAMILY)
    d = [pick([f"how is your {fam}?", "do you have any children?", "how big is your family?",
               "do you have brothers or sisters?", f"is your {fam} still living in {pick(TOWNS)}?",
               "how is your family doing?"])]
    d.append(pick([f"my {fam} is doing well.", f"i have {pick(SMALL_NUMBERS)} children.", "yes, two brothers and a sister.",
                   "it's just me and my mother.", f"my {fam} is sick, sadly.", f"my {fam} moved to {pick(TOWNS)}.",
                   "we are a big family.", "no, i'm an only child.", f"my {fam} just had a baby!"]))
    d.append(pick(["that's nice.", "you must miss them.", "oh, congratulations!", "i hope they get better.",
                   "do you see them often?", "how lovely."]))
    if maybe():
        d.append(pick(["thank you.", "yes, very much.", "every week.", "not as often as i'd like.",
                       f"i'll visit them {pick(FUTURE_TIMES)}."]))
    return d


def sc_feelings():
    bad = pick(BAD_FEEL)
    d = [pick(["you look sad. what's wrong?", "is something bothering you?", "you seem quiet today.",
               "are you okay? you look upset.", "what's the matter?", "why the long face?"])]
    reason = pick([f"my {pick(PETS)} ran away.", f"i had a fight with my {pick(FAMILY)}.",
                   f"i lost my {pick(TOOLS)}.", "i miss my home.", "nobody came to my party.",
                   "the storm destroyed my garden.", "i failed my test.", "i feel so alone.",
                   "i'm worried about the harvest.", f"my friend {name()} moved away.",
                   "i made a big mistake at work.", "i can't stop thinking about it."])
    d.append(pick([f"i'm just {bad}.", reason, f"i'm {bad}. {reason}", "it's nothing.", "i don't want to talk about it."]))
    if d[-1] in ("it's nothing.", "i don't want to talk about it."):
        d.append(pick(["okay, but i'm here if you need me.", "are you sure?", "you can tell me anything."]))
        d.append(pick(["thanks.", "i know. thank you."]) if maybe() else reason)
        if d[-1] in ("thanks.", "i know. thank you."):
            return d
    d.append(pick(["i'm so sorry.", "that must be hard.", "come here, give me a hug.", "it will be okay.",
                   "do you want to talk about it?", "things will get better, i promise.",
                   "let's go for a walk, it might help.", "i understand how you feel."]))
    d.append(pick(["thank you.", "you're a good friend.", "i feel a little better.", "maybe you're right.",
                   "thanks for listening.", "i hope so."]))
    return d


def sc_good_news():
    d = [pick(["guess what!", "i have great news!", "you won't believe this!", "i have something to tell you."]),
         pick(["what?", "tell me!", "what is it?", "what happened?"]),
         pick([f"i'm getting married to {name()}!", "we're having a baby!", "i got the job!",
               f"my {pick(FAMILY)} is coming to visit!", "i found diamonds in the mine!", "the harvest was the best ever!",
               f"i finally built my own {pick(['house', 'boat', 'farm', 'shop', 'tower'])}!", "i won the race!",
               f"my {pick(PETS)} had puppies!" if maybe() else "i learned to read!"])]
    d.append(pick(["that's wonderful!", "congratulations!", "no way, really?", "i'm so happy for you!",
                   "that's amazing news!", "wow, well done!"]))
    d.append(pick(["thank you!", "i can't believe it either.", "yes, really!", "we should celebrate.", "thanks, i'm so excited."]))
    return d


def sc_apology():
    d = [pick(["i'm sorry i was late.", "sorry i broke your " + pick(TOOLS) + ".", "i'm sorry about what i said yesterday.",
               "i forgot your birthday, i'm so sorry.", "sorry i ate your " + pick(FOODS) + ".",
               "i'm sorry i didn't come to help.", "i lost the " + pick(TOOLS) + " you lent me, sorry."])]
    if maybe(0.75):
        d.append(forgive())
        d.append(pick(["thank you for understanding.", "i'll make it up to you.", "it won't happen again.",
                       "i'll buy you a new one."]))
        if maybe():
            d.append(pick(["don't worry about it.", "really, it's fine.", "okay, deal.", "you'd better!"]))
    else:
        d.append(pick(["you always say that.", "that really hurt me.", "i'm still angry.", "why did you do it?"]))
        d.append(pick(["i know, and i'm truly sorry.", "i wasn't thinking.", "i was tired and angry.", "please forgive me."]))
        d.append(pick(["okay. i forgive you.", "i need some time.", "fine, but don't do it again.", "alright."]))
    return d


def sc_argument():
    thing = pick(TOOLS + ITEMS)
    d = [pick([f"hey, that's my {thing}!", f"who took my {thing}?", "you're standing on my crops!",
               "why didn't you tell me?", "you said you would help me!", "this is my spot."]),
         pick([f"no, it's mine.", "i only borrowed it.", "i didn't know.", "i forgot, okay?", "calm down.",
               "i was here first."])]
    d.append(pick(["you should ask first.", "that's not fair.", "you always do this.", "give it back.",
                   "don't tell me to calm down!", "i don't believe you."]))
    d.append(pick(["fine, here.", "okay, you're right, sorry.", "let's not fight about this.", "you're overreacting.",
                   "can we talk about this calmly?"]))
    if maybe():
        d.append(pick(["okay. sorry i shouted.", "alright, let's start again.", "fine.", "thank you."]))
    return d


def sc_invitation():
    ev = pick(EVENTS)
    t = pick(FUTURE_TIMES)
    d = [pick([f"would you like to come to {ev} {t}?", f"are you going to {ev}?", f"do you want to go {pick(HOBBIES)} {t}?",
               f"let's have dinner together {t}.", f"we are having {ev} {t}, you should come.",
               f"want to play {pick(GAMES)}?"])]
    if maybe(0.7):
        d.append(pick(["i'd love to!", "yes, what time?", "sure, sounds fun.", "of course, should i bring anything?",
                       "count me in."]))
        if "time" in d[-1]:
            d.append(pick(["at sunset.", "around noon.", "early in the morning.", "after work.", "whenever you're ready."]))
        elif "bring" in d[-1]:
            d.append(pick([f"maybe some {pick(FOODS)}.", "just yourself.", "bring a friend if you like.", "a warm coat."]))
        else:
            d.append(pick(["great!", "wonderful, see you there.", "i'll come and get you.", "it will be fun."]))
    else:
        d.append(pick(["sorry, i can't.", "i'm busy then.", "maybe next time.", "i'm not feeling well.",
                       f"i promised to help my {pick(FAMILY)}."]))
        d.append(pick(["that's okay.", "oh, too bad.", "next time then.", "feel better soon."]))
    return d


def sc_school():
    subj = pick(SUBJECTS)
    d = [pick(["what did you learn today?", "how was school?", "did you do your homework?",
               f"what are you studying?", "do you like your teacher?"])]
    d.append(pick([f"we learned about {subj}.", "it was boring.", "it was fun!", "not yet.",
                   f"i'm studying {subj}.", "she is very kind.", "he is strict but fair.",
                   f"i learned that {pick(ANIMALS)} can {pick(['swim', 'climb', 'see in the dark', 'live for years', 'jump high'])}."]))
    d.append(pick(["tell me more.", "that's interesting.", "you should finish it before dinner.",
                   "why was it boring?", "what was the best part?", "i'm proud of you."]))
    d.append(pick(["okay.", "we played a game at the end.", f"i want to learn more about {pick(SUBJECTS)}.",
                   "because we just sat there.", "thanks!", "i'll do it now."]))
    return d


def sc_teach_word():
    thing = pick(ANIMALS + ITEMS + BLOCKS + FOODS)
    d = [pick([f"what is that called?", "what's this?", f"what do you call this?"]),
         pick([f"that's {thing}.", f"it's called {thing}.", f"we call it {thing}.", f"{thing}."]),
         pick([f"{thing}?", f"{thing}. okay.", f"can you say {thing} again?", "how do you spell it?"]),
         pick(["yes, that's right.", "exactly.", f"{thing}.", "good, you learn fast."])]
    return d


def sc_question_answer():
    q = pick([
        ("what time is it?", ["it's almost noon.", "about three o'clock.", "it's getting late.", "early morning still.", "i don't know, look at the sun."]),
        ("what day is it today?", [f"it's {pick(DAYS)}.", f"today is {pick(DAYS)}.", "i lost track of the days."]),
        ("where are you going?", [f"to the {pick(PLACES)}.", "home.", f"to see my {pick(FAMILY)}.", "nowhere special.", "to work."]),
        ("where have you been?", [f"at the {pick(PLACES)}.", f"i was {pick(WORK_ACTS)}.", "out for a walk.", f"visiting {pick(TOWNS)}."]),
        ("what are you doing?", [f"i'm {pick(WORK_ACTS)}.", "nothing much.", "just resting.", "waiting for a friend.", f"{pick(HOBBIES)}."]),
        ("who is that?", [f"that's {name()}.", f"that's my {pick(FAMILY)}.", "i don't know.", f"the new {pick(JOBS)}."]),
        ("why are you so tired?", ["i didn't sleep.", "i worked all day.", "the baby kept me up.", "i walked from the next village."]),
        ("what is your name?", [f"my name is {name()}.", f"i'm {name()}.", f"{name()}. and yours?", f"people call me {name()}."]),
        ("how old are you?", [f"i'm {pick(NUMBERS)}.", "older than i look.", "that's a secret.", f"{pick(NUMBERS)} this {pick(SEASONS)}."]),
        ("where do you live?", [f"in the house by the {pick(PLACES)}.", f"in {pick(TOWNS)}.", "just over there.", "near the church."]),
        ("what's your favourite food?", [f"{pick(FOODS)}.", f"definitely {pick(FOODS)}.", f"i love {pick(FOODS)}."]),
        ("what do you like to do?", [f"i like {pick(HOBBIES)}.", f"{pick(HOBBIES)} and {pick(HOBBIES)}.", f"mostly {pick(HOBBIES)}."]),
        ("can you swim?", ["yes, very well.", "a little.", "no, i'm afraid of water.", "i'm learning."]),
        ("do you have a pet?", [f"yes, a {pick(PETS)}.", "no, but i want one.", f"i have two {pick(ANIMALS)}."]),
        ("are you hungry?", ["yes, starving.", "not really.", "a little.", "i just ate."]),
        ("is it far?", ["not far.", "quite far.", "about an hour.", "just around the corner."]),
        ("what happened here?", ["a creeper blew up.", "there was a storm.", "the kids were playing.", "i have no idea."]),
        ("do you need anything?", ["no, i'm fine.", "some water, please.", "just a bit of help.", f"some {pick(ITEMS)}."]),
        ("have you seen my " + pick(TOOLS) + "?", [f"it's by the {pick(PLACES)}.", "no, sorry.", "i think your brother took it.", "check the shed."]),
        ("what's wrong?", ["nothing.", "i'm just tired.", f"my {pick(BODY)} hurts.", "i lost something."]),
        ("can i borrow your " + pick(TOOLS) + "?", ["sure, here.", "yes, but bring it back.", "sorry, i need it.", "of course."]),
        ("do you know " + name() + "?", ["yes, we are old friends.", "not really.", "i've heard of them.", "everyone knows them."]),
    ])
    return [q[0], pick(q[1])] + ([pick(["okay.", "thanks.", "i see.", "oh.", "really?", "nice."])] if maybe() else [])


def sc_farming():
    crop = pick(["wheat", "carrots", "potatoes", "beetroots", "pumpkins", "melons", "corn", "beans", "cabbages"])
    d = [pick([f"how are your {crop} growing?", "how is the farm?", f"when will the {crop} be ready?",
               "did the rain help your crops?", "what are you planting this year?"])]
    d.append(pick([f"the {crop} are almost ready.", f"i planted {crop} {pick(PAST_TIMES)}.", "the soil is too dry.",
                   "the rabbits are eating everything.", "it's been a good year.", f"in {pick(SEASONS)}.",
                   f"i'm trying {crop} and {pick(['onions', 'garlic', 'peas', 'squash'])}."]))
    d.append(pick(["that's good news.", "you should build a fence.", "water them every morning.",
                   "do you need help with the harvest?", "my grandfather had the same problem.", "use bone meal."]))
    d.append(pick(["good idea.", "yes, please.", "i'll try that.", "thanks for the tip.", "i'll do that tomorrow."]))
    return d


def sc_building():
    thing = pick(["house", "wall", "bridge", "tower", "barn", "fence", "well", "shop", "chicken coop", "boat", "staircase"])
    blk = pick(BLOCKS)
    d = [pick([f"what are you building?", f"are you building a {thing}?", "need help building?",
               f"that's a nice {thing}.", "how is the building going?"])]
    d.append(pick([f"a {thing} for my family.", f"yes, a {thing} out of {blk}.", "slowly.", f"thanks, i used {blk}.",
                   f"i need more {blk}.", "the roof is the hard part."]))
    d.append(pick([f"i have some {blk} you can use.", "do you want help?", "it looks strong.", "how long will it take?",
                   "put a window here.", "you should use stone, it's stronger."]))
    d.append(pick(["thank you!", "yes, hold this.", "maybe a week.", "good idea.", "i'll think about it.", "that would be great."]))
    return d


def sc_trade():
    a, b = pick(ITEMS), pick(ITEMS)
    n1, n2 = pick(SMALL_NUMBERS), pick(SMALL_NUMBERS)
    d = [pick([f"i'll trade you {n1} {a} for {n2} {b}.", f"want to trade? i have {a}.", f"do you have any {b} to trade?",
               f"i need {b}. what do you want for them?"])]
    d.append(pick(["deal.", "that's not enough.", f"how about {pick(SMALL_NUMBERS)} {a}?", f"i don't need {a}.",
                   f"i'd take {pick(ITEMS)} instead.", "let me think."]))
    d.append(pick(["okay, fine.", "that's my best offer.", "alright, it's a deal.", "forget it then.", "here you go."]))
    if maybe():
        d.append(pick(["pleasure trading with you.", "thanks.", "come back anytime.", "fair trade."]))
    return d


def sc_animals():
    an = pick(ANIMALS)
    d = [pick([f"look at the {an}!", f"did you see the {an} by the {pick(PLACES)}?", f"is that your {pick(PETS)}?",
               f"what does a {pick(PETS)} eat?", "the cows got out again!", f"i'm scared of {pick(ANIMALS)}."])]
    d.append(pick(["so cute!", "where?", "yes, isn't it lovely?", f"mostly {pick(FOODS)}.", "oh no, not again.",
                   "don't worry, they won't hurt you.", "careful, it might bite.", "let's catch it."]))
    d.append(pick([f"over there by the {pick(PLACES)}.", "can i pet it?", "help me get them back.",
                   "i want one too.", "really?", "it's running away!"]))
    d.append(pick(["sure, be gentle.", "follow me.", "maybe for your birthday.", "yes, really.", "quick, after it!"]))
    return d


def sc_travel():
    town = pick(TOWNS)
    d = [pick([f"i'm going to {town} {pick(FUTURE_TIMES)}.", f"have you ever been to {town}?",
               f"how was your trip to {town}?", "are you going on a journey?", "where are you travelling?"])]
    d.append(pick(["really? why?", "yes, once, it was beautiful.", "no, never.", "it was long but worth it.",
                   f"to {town}, to visit family.", "be careful on the road.", f"it rained the whole time."]))
    d.append(pick([f"to see my {pick(FAMILY)}.", "to sell my goods.", "i want to see the sea.", "what's it like?",
                   "i'll take the horse.", "i met a lot of people.", "i got lost twice."]))
    d.append(pick(["have a safe trip.", "bring me something back.", "it's busy and loud.", "the food there is great.",
                   "sounds like an adventure.", "that's funny."]))
    return d


def sc_night():
    d = [pick(["it's getting dark.", "the sun is going down.", "it's late, we should go home.",
               "time for bed.", "i can't sleep.", "did you hear that noise outside?"])]
    d.append(pick(["yes, let's go inside.", "i'll light a torch.", "already? the day went fast.", "me neither.",
                   "it's just the wind.", "lock the door, monsters come out at night.", "good night then."]))
    d.append(pick(["good idea.", "sleep well.", "are you sure?", "see you in the morning.", "sweet dreams.",
                   "want me to tell you a story?"]))
    if maybe(0.4):
        d.append(pick(["yes, please.", "good night.", "okay, i'll try.", "you too."]))
    return d


def sc_morning():
    d = [pick(["good morning! did you sleep well?", "rise and shine!", "you're up early.", "morning, sleepyhead."]),
         pick(["yes, like a baby.", "not really, i had bad dreams.", "five more minutes.", "the rooster woke me up.",
               "i couldn't sleep, so i got up."]),
         pick(["breakfast is ready.", "what do you want for breakfast?", "we have a lot to do today.",
               "the sun is already up.", f"there are {pick(FOODS)} on the table."]),
         pick(["great, i'm hungry.", f"{pick(FOODS)}, please.", "what do we need to do?", "okay, i'm coming.", "thanks."])]
    if "need to do" in d[-1]:
        d.append(f"first {pick(WORK_ACTS)}, then {pick(WORK_ACTS)}.")
    return d


def sc_shop_clothes():
    c = pick(COLORS)
    item = pick(["shirt", "coat", "hat", "dress", "scarf", "pair of boots", "cloak", "belt"])
    d = [pick([f"do you have this {item} in {c}?", f"how much is the {c} {item}?", f"can i try on this {item}?",
               f"i'm looking for a warm {item}."])]
    d.append(pick([f"yes, here is a {c} one.", f"that's {pick(SMALL_NUMBERS)} {pick(COINS)}.", "of course, the mirror is there.",
                   f"sorry, only in {pick(COLORS)}.", "this one is very warm."]))
    d.append(pick(["it fits perfectly.", "it's too big.", "it's too small.", "i'll take it.", "do you have a cheaper one?",
                   "it suits me, doesn't it?"]))
    d.append(pick(["it looks great on you.", "let me get a smaller size.", "here is a bigger one.", "this one is cheaper.",
                   "excellent choice."]))
    return d


def sc_lost_item():
    t = pick(TOOLS + ["ring", "necklace", "book", "hat", "bag", "coins", "letter"])
    d = [pick([f"have you seen my {t}?", f"i can't find my {t} anywhere.", f"i lost my {t}.", f"someone stole my {t}!"])]
    d.append(pick(["where did you see it last?", "did you check your bag?", "no, sorry.", "when did you lose it?",
                   f"i saw one by the {pick(PLACES)}.", "let me help you look."]))
    d.append(pick([f"at the {pick(PLACES)}.", "yes, it's not there.", f"{pick(PAST_TIMES)}.", "really? let's go.",
                   "thank you."]))
    d.append(pick(["here it is!", "found it, it was under the table.", "we'll keep looking.", "maybe the kids took it.",
                   "i'm sure it will turn up."]))
    return d


def sc_joke():
    jokes = [
        ("why did the chicken cross the road?", "to get to the other side."),
        ("what do you call a sleeping cow?", "a bulldozer."),
        ("why don't skeletons fight?", "they don't have the guts."),
        ("what did the farmer say when he lost his tractor?", "where's my tractor?"),
        ("why did the creeper cross the road?", "to get to the other ssssside."),
        ("what has to be broken before you can use it?", "an egg."),
        ("what has hands but can't clap?", "a clock."),
        ("what gets wetter the more it dries?", "a towel."),
        ("why was the sheep so calm?", "it was feeling sheepish."),
        ("what do you call a fish with no eyes?", "a fsh."),
        ("why did the scarecrow win a prize?", "because he was outstanding in his field."),
        ("what is a pig's favourite colour?", "mud brown."),
    ]
    q, a = pick(jokes)
    d = [pick(["want to hear a joke?", "i know a funny one.", "tell me a joke.", "i have a riddle for you."])]
    d.append(pick(["sure.", "go on.", "okay, what?", "you always tell bad jokes.", "fine."]) if not d[0].startswith("tell") else
             pick(["okay, here is one.", "let me think.", "alright."]))
    d += [q, pick(["i don't know, why?", "tell me.", "no idea.", "hmm, i give up.", "what?"]), a]
    d.append(pick(["haha!", "that's terrible.", "ha, good one.", "i don't get it.", "that's so silly.", "ugh, really?"]))
    return d


def sc_compliment():
    d = [pick(["you look nice today.", "i like your hat.", f"your garden is beautiful.", "you did a great job.",
               "your bread is the best in the village.", "you sing very well.", "that was very brave of you.",
               "you're really good at this."]),
         pick(["thank you!", "oh, stop it.", "really? thanks.", "that's sweet of you.", "you're too kind.",
               "i worked hard on it."])]
    if maybe():
        d.append(pick(["i mean it.", "you should be proud.", "can you teach me?", "really, it's wonderful."]))
        d.append(pick(["sure, any time.", "thanks, that means a lot.", "i'd be happy to.", "you're making me blush."]))
    return d


def sc_romance():
    n = name()
    d = [pick(["can i tell you something?", "i really like spending time with you.", "would you like to go for a walk with me?",
               "i made this for you.", f"i think i'm in love with {n}.", "will you marry me?"])]
    if d[0] == "will you marry me?":
        d.append(pick(["yes! yes, of course!", "oh my, i need time to think.", "i'm sorry, i can't.", "i thought you'd never ask!"]))
    elif d[0].startswith("i think"):
        d.append(pick(["really? you should tell them.", "i knew it!", "does she know?", "does he know?", "that's sweet."]))
        d.append(pick(["i'm too nervous.", "not yet.", "i'll tell them tomorrow.", "maybe i'll bring flowers."]))
    else:
        d.append(pick(["of course.", "i like spending time with you too.", "oh, it's beautiful, thank you.",
                       "i'd love that.", "what is it?"]))
        d.append(pick(["i'm so glad.", "you make me happy.", "let's go to the lake.", "you mean a lot to me."]))
        d.append(pick(["you make me happy too.", "me too.", "i feel the same.", "that's sweet."]))
    return d


def sc_child_parent():
    p = pick(["mom", "dad", "mother", "father", "grandma", "grandpa"])
    asks = [
        ("can i go out and play?", ["only until dinner.", "did you finish your chores?", "yes, but stay close to the house."],
         ["okay!", "yes, i finished them.", "i will, i promise."], ["good, then go on.", "be back before dark.", "have fun."]),
        ("i'm hungry.", ["dinner is almost ready.", f"have some {pick(FOODS)}.", "you just ate!"],
         ["but i'm really hungry.", "okay, thanks.", "can i have a snack?"], ["just wait a little.", "here, take an apple.", "fine, one snack."]),
        ("why is the sky blue?", ["that's a good question.", "because the air scatters the sunlight.", "nobody really knows, sweetie."],
         ["but why?", "really?", "oh."], ["you ask too many questions.", "ask the librarian, she knows.", "you'll learn when you're older."]),
        ("i'm bored.", ["go and help your sister.", f"why don't you go {pick(HOBBIES)}?", "then help me clean."],
         ["that's boring too.", "okay, fine.", "do i have to?"], ["yes, you do.", "then find something to do.", "good."]),
        (f"can we get a {pick(PETS)}?", ["maybe when you're older.", "who would look after it?", "we'll see."],
         ["i would, i promise!", "please?", "that's not fair."], ["we'll talk about it later.", "alright, we'll think about it.", "no means no."]),
        ("i'm scared of the dark.", ["there's nothing to be scared of.", "i'll leave a candle on.", "do you want me to stay for a bit?"],
         ["what if there are monsters?", "thank you.", "yes, please."], ["the torches keep them away.", "i'm right here.", "sleep tight."]),
        ("can i have a cookie?", ["only one.", "not before dinner.", "did you say please?"],
         ["thank you!", "please?", "please can i have a cookie?"], ["you're welcome, sweetie.", "alright, just one.", "good manners. here you go."]),
    ]
    q, a1, a2, a3 = pick(asks)
    return [f"{p}, {q}", pick(a1), pick(a2), pick(a3)]


def sc_planning():
    t = pick(FUTURE_TIMES)
    d = [pick([f"what should we do {t}?", "what's the plan?", f"are you free {t}?", "we need to get ready for winter."])]
    d.append(pick([f"let's go {pick(HOBBIES)}.", f"we could visit {pick(TOWNS)}.", f"i need to finish {pick(WORK_ACTS)}.",
                   "we should store food and wood.", f"let's meet at the {pick(PLACES)}.", "i don't know, any ideas?"]))
    d.append(pick(["good idea.", "what time?", "i'd rather stay home.", f"how about {pick(HOBBIES)} instead?",
                   "okay, i'll bring the food.", "sounds like a plan."]))
    d.append(pick(["after lunch.", "early, before it gets hot.", "fine, we'll do that.", "great.", "see you then."]))
    return d


def sc_emergency():
    d = [pick(["fire! the barn is on fire!", "help! someone fell in the river!", "the bridge collapsed!",
               "quick, the baby is coming!", "there's a flood in the lower fields!", "a tree fell on the road!"])]
    d.append(pick(["get water, quickly!", "i'll get a rope!", "call the doctor!", "is anyone hurt?", "everyone stay back!",
                   "run and get help!"]))
    d.append(pick(["i'm going!", "i think someone is trapped!", "the doctor is coming.", "here, grab this!",
                   "nobody is hurt.", "hurry!"]))
    d.append(pick(["we did it.", "thank goodness everyone is safe.", "that was terrible.", "well done, everyone.",
                   "we'll fix it tomorrow."]))
    return d


def sc_grief():
    who = pick(["grandpa", "grandma", f"my {pick(PETS)}", "my old friend", f"my {pick(FAMILY)}", "our neighbour"])
    d = [pick([f"{who} passed away {pick(PAST_TIMES)}.", f"i miss {who} so much.", f"we lost {who}."])]
    d.append(pick(["i'm so sorry for your loss.", "oh no. i'm so sorry.", "they were a good soul.", "is there anything i can do?",
                   "come here."]))
    d.append(pick(["thank you.", "i keep thinking about them.", "just stay with me for a bit.", "it doesn't feel real.",
                   "they always made me laugh."]))
    d.append(pick(["of course, i'm here.", "they will always be with you.", "take all the time you need.",
                   "i remember that too.", "it's okay to cry."]))
    return d


def sc_hearing_check():
    return [pick(["can you hear me?", "are you listening?", "hello? anyone there?", "did you hear what i said?"]),
            pick(["yes, i can hear you.", "sorry, what?", "yes, go on.", "i'm listening.", "yes, i'm here."])]


def sc_repeat():
    s = pick(["the well is broken.", "meet me at the bridge.", f"i need {pick(SMALL_NUMBERS)} {pick(ITEMS)}.",
              "the guards are changing.", "bring the sheep home.", "it's going to rain."])
    return [s, dont_understand(), pick(["i said ", "", "listen. "]) + s, pick(["oh, okay.", "got it.", "right, thanks."])]


def sc_opinion():
    topic = pick([pick(FOODS), pick(HOBBIES), pick(SEASONS), "the new mayor", "the weather lately", "the festival",
                  "living in the city", "the new houses", pick(ANIMALS)])
    d = [pick([f"what do you think about {topic}?", f"do you like {topic}?", f"how do you feel about {topic}?"])]
    d.append(pick([f"i love {topic}.", f"i don't like {topic} much.", "it's okay, i guess.", "i've never thought about it.",
                   f"{topic}? it's the best.", "honestly, not my thing."]))
    d.append(pick(["me too!", "really? why?", "i agree.", "i disagree.", "fair enough.", "i feel the same."]))
    if "why" in d[-1]:
        d.append(pick(["it just makes me happy.", "it reminds me of home.", "it's too much trouble.", "i don't know, i just do."]))
    return d


def sc_bargain_job():
    d = [pick([f"i'm looking for work. do you need {a_or_an(pick(JOBS))}?", "are you hiring?", "do you have any work for me?"]),
         pick(["what can you do?", "maybe. what experience do you have?", "not right now, sorry.", "yes, we need help in the fields."])]
    if "sorry" in d[-1]:
        d.append(pick(["okay, thanks anyway.", "please tell me if anything opens up."]))
        return d
    d.append(pick([f"i can do {pick(WORK_ACTS)}.", f"i worked as {a_or_an(pick(JOBS))} for {pick(SMALL_NUMBERS)} years.",
                   "i learn fast and i work hard.", "i can cook and clean."]))
    d.append(pick([f"good. you can start {pick(FUTURE_TIMES)}.", f"i'll pay you {pick(SMALL_NUMBERS)} {pick(COINS)} a day.",
                   "let's try you for a week.", "come back tomorrow morning."]))
    d.append(pick(["thank you, i won't let you down.", "great, i'll be there.", "is that per day?", "deal."]))
    return d


def sc_neighbour_noise():
    d = [pick(["could you keep it down? i'm trying to sleep.", "your dog barked all night.", "your sheep are in my garden again.",
               "please stop hammering so early."]),
         pick(["oh, i'm sorry, i didn't realise.", "sorry, i'll be quieter.", "it's not that loud.", "i'll bring them back now."]),
         pick(["thank you.", "it is loud, trust me.", "i appreciate it.", "thanks, neighbour."])]
    return d


def sc_bank_of_basic_facts():
    facts = [
        ("is fire hot?", "yes, fire is very hot."), ("is ice cold?", "yes, ice is cold."),
        ("can fish breathe air?", "no, fish breathe water."), ("do cows give milk?", "yes, cows give milk."),
        ("where does bread come from?", "bread is made from wheat."), ("what do bees make?", "bees make honey."),
        ("when does the sun rise?", "the sun rises in the morning."), ("what comes after monday?", "tuesday comes after monday."),
        ("how many legs does a dog have?", "a dog has four legs."), ("what colour is grass?", "grass is green."),
        ("what colour is the sky?", "the sky is blue."), ("where do fish live?", "fish live in water."),
        ("what do sheep give us?", "sheep give us wool."), ("what do chickens lay?", "chickens lay eggs."),
        ("what do you use an axe for?", "you use an axe to chop wood."), ("what do you use a pickaxe for?", "a pickaxe is for mining stone."),
        ("why do we sleep?", "we sleep to rest our bodies."), ("what do plants need?", "plants need water and sun."),
        ("what is snow?", "snow is frozen water."), ("when is it dark?", "it's dark at night."),
        ("what is two and two?", "two and two is four."), ("what is three and four?", "three and four is seven."),
        ("is the moon out at night?", "yes, the moon comes out at night."), ("what makes zombies burn?", "sunlight makes zombies burn."),
        ("why do we light torches?", "torches keep monsters away and help us see."), ("what grows on trees?", "apples grow on trees."),
        ("what do horses eat?", "horses eat hay and apples."), ("what is a doctor for?", "a doctor helps sick people."),
        ("what does a farmer do?", "a farmer grows food."), ("what does a baker make?", "a baker makes bread and cake."),
    ]
    q, a = pick(facts)
    return [q, a] + ([pick(["oh, i see.", "i didn't know that.", "right.", "of course."])] if maybe() else [])


SCENARIOS = [
    (sc_small_talk, 10), (sc_weather, 4), (sc_market, 7), (sc_directions, 5), (sc_doctor, 4), (sc_meal, 6),
    (sc_cooking_instructions, 2), (sc_danger, 4), (sc_injury, 3), (sc_help_request, 6), (sc_work_talk, 5),
    (sc_feelings, 5), (sc_good_news, 3), (sc_apology, 4), (sc_argument, 3), (sc_invitation, 5),
    (sc_teach_word, 3), (sc_question_answer, 12), (sc_trade, 4),
    (sc_morning, 3), (sc_shop_clothes, 2), (sc_lost_item, 3),
    (sc_joke, 2), (sc_compliment, 3), (sc_romance, 2), (sc_child_parent, 4), (sc_planning, 3), (sc_emergency, 2),
    (sc_grief, 1), (sc_hearing_check, 2), (sc_repeat, 2), (sc_opinion, 4), (sc_bargain_job, 2),
    (sc_neighbour_noise, 2), (sc_bank_of_basic_facts, 6),
]


# ---------------------------------------------------------------------------------------------
# Scenes: short told stories about everyday life
# ---------------------------------------------------------------------------------------------

def scene():
    who = pick(["i", "we", "my " + pick(FAMILY), name(), "the " + pick(JOBS), "my friend"])
    t = pick(PAST_TIMES)
    starts = [
        f"{t} {who} went to the {pick(PLACES)}.",
        f"{t} {who} fed the {pick(ANIMALS)}.",
        f"{t} {who} found {pick(ONE_ITEMS)} near the {pick(PLACES)}.",
        f"{t} {who} saw {pick(MONSTERS)} by the {pick(PLACES)}.",
        f"{t} {who} spent the day {pick(WORK_ACTS)}.",
        f"{t} {who} cooked {pick(FOODS)} for everyone.",
        f"{t} {who} built a {pick(['wall', 'fence', 'house', 'bridge', 'boat', 'tower'])} out of {pick(BLOCKS)}.",
        f"{t} {who} walked {pick(DIRECTIONS)} to the {pick(PLACES)}.",
        f"{t} it {pick(['rained', 'snowed', 'stormed', 'was sunny', 'was windy'])} all day.",
        f"{t} {who} met {name()} at the {pick(PLACES)}.",
        f"{t} {who} bought {pick(SMALL_NUMBERS)} {pick(ITEMS)} at the market.",
        f"{t} {who} lost {pick(ONE_ITEMS)}.",
        f"{t} {who} caught {pick(SMALL_NUMBERS)} fish in the {pick(['river', 'lake', 'sea'])}.",
        f"{t} {who} planted {pick(['wheat', 'carrots', 'potatoes', 'flowers', 'trees'])} in the {pick(['field', 'garden'])}.",
    ]
    mids = [
        f"it was {pick(WEATHER_GOOD + WEATHER_BAD)} outside.",
        f"{'they were' if who == 'we' else 'it was'} hard work, but worth it." if maybe() else "it took a long time.",
        f"then {who} {pick(['went home', 'had lunch', 'took a nap', 'sat by the fire', 'talked with friends', 'washed up'])}.",
        f"on the way back {who} {pick(['saw a rainbow', 'heard wolves', 'got lost', 'found berries', 'met a trader', 'fell in the mud'])}.",
        f"{who} {pick(['felt', 'was'])} {pick(GOOD_FEEL + BAD_FEEL)} afterwards.",
        f"everyone said it was the best {pick(FOODS)} they ever had.",
        f"the {pick([a for a in ANIMALS if a.endswith('s')] + ['sheep', 'deer', 'fish', 'mice'])} {pick(['ran away', 'followed us home', 'were very hungry', 'made a lot of noise'])}.",
        f"{name()} helped carry the {pick(ITEMS)}.",
        f"we had to hide until the sun came up.",
        f"it got dark, so {who} lit a torch.",
    ]
    lines = [pick(starts)] + R.sample(mids, R.randint(1, 3))
    return [" ".join(lines)]


# ---------------------------------------------------------------------------------------------

def hand_written(path):
    if not os.path.exists(path):
        return []
    out, cur = [], []
    for raw in open(path, encoding="utf-8"):
        line = raw.strip()
        if line.startswith("#"):
            continue
        if not line:
            if cur:
                out.append(cur)
                cur = []
            continue
        cur.append(line.lower())
    if cur:
        out.append(cur)
    return out


SKILLS = ["read", "write", "swim", "cook", "fish", "sew", "ride a horse", "build boats", "play the flute",
          "make pottery", "carve wood", "bake bread", "speak another language", "use a bow", "draw maps"]


def slot_words():
    return {
        "subject": SUBJECTS, "smallnumber": SMALL_NUMBERS, "number": NUMBERS, "pet": PETS, "town": TOWNS,
        "family": FAMILY, "animal": [a for a in ANIMALS if not a.endswith("s") or a in ("mouse",)], "place": PLACES,
        "food": FOODS, "future": FUTURE_TIMES, "item": ITEMS, "block": BLOCKS, "tool": TOOLS, "coin": COINS,
        "structure": ["house", "barn", "fence", "wall", "tower", "bridge", "well", "shop", "boat", "chicken coop"],
        "crop": ["wheat", "carrots", "potatoes", "beetroots", "pumpkins", "melons", "beans", "cabbages"],
        "job": JOBS, "a_job": [a_or_an(j) for j in JOBS], "season": SEASONS, "hobby": HOBBIES,
        "name": [n.capitalize() for n in NAMES], "game": GAMES, "skill": SKILLS, "colour": COLORS,
        "month": ["january", "february", "march", "april", "may", "june", "july", "august", "september",
                  "october", "november", "december"],
        "material": ["willow", "reeds", "straw", "wool", "yarn", "clay", "wood", "leather"],
        "thing": ["horse", "boat", "house", "cart", "fishing rod", "new coat", "cow", "plot of land", "shop", "violin"],
    }


def main():
    out_path = sys.argv[1]
    count = int(sys.argv[2]) if len(sys.argv) > 2 else 200_000
    R.seed(int(sys.argv[3]) if len(sys.argv) > 3 else 1)
    funcs = [f for f, w in SCENARIOS for _ in range(w)]
    words = slot_words()
    written = hand_written(os.path.join(os.path.dirname(os.path.abspath(__file__)), "dialogues.txt"))
    if "--no-hand-written" in sys.argv:  # for held-out test sets
        written = []
    with open(out_path, "w", encoding="utf-8") as out:
        for i in range(count):
            r = R.random()
            if written and r < 0.03:
                d = pick(written)
            elif r < 0.10:
                d = scene()
            elif r < 0.18:
                d = grammar.told(R)
            elif r < 0.30:
                d = facts.fact_dialogue(R)
            elif r < 0.36:
                d = chatter.chatter(R)
            elif r < 0.42:
                d = herenow.here_and_now(R)
            elif r < 0.68:
                d = trees.tree_dialogue(R, words)
            else:
                d = pick(funcs)()
            out.write("\n".join(d) + "\n\n")


if __name__ == "__main__":
    main()
