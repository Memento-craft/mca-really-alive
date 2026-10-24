"""Conversations written as small trees: each line has a few ways of saying it, and each line
lists the replies that fit it, so a walk from the root is always a sensible exchange.

T(text, *replies): text is a string or a list of phrasings (each may hold {slots}); replies are
further T nodes, one of which is picked. Slots are filled from the word lists in generate.py,
and the same slot keeps its value for the whole conversation."""
import random
import re

R = random.Random()
WORDS = {}


def pick(xs):
    return R.choice(xs)


class T:
    def __init__(self, text, *replies, stop=0.0):
        self.text = text if isinstance(text, list) else [text]
        self.replies = replies
        self.stop = stop


def walk(root, rng, words):
    global R, WORDS
    R, WORDS = rng, words
    slots = {}

    def fill(m):
        key = m.group(1)
        if key not in slots:
            base = key.rstrip("0123456789")
            slots[key] = pick(WORDS[base])
        return slots[key]

    lines, node = [], root
    while node is not None:
        lines.append(re.sub(r"\{(\w+)\}", fill, pick(node.text)))
        if not node.replies or R.random() < node.stop:
            break
        node = pick(node.replies)
    return lines


THANKS = T(["thank you.", "thanks.", "thanks a lot.", "that's kind of you."],
           T(["you're welcome.", "no problem.", "any time.", "my pleasure."]))
BYE = T(["well, i should go.", "i have to go now.", "see you later.", "bye for now."],
        T(["bye!", "see you.", "take care.", "goodbye."]))

TREES = []


def tree(weight):
    def add(node):
        TREES.append((node, weight))
        return node
    return add


tree(3)(T(["did you do your homework?", "have you finished your homework?"],
          T(["yes, all of it.", "yes, i finished it after lunch."],
            T(["good. then you can go and play.", "well done. show me later."], T(["okay!", "thanks!"]))),
          T(["not yet.", "almost.", "i forgot."],
            T(["do it now, before dinner.", "no playing until it's done."], T(["okay, okay.", "fine.", "but it's so hard!"],
                                                                              T(["i'll help you with it.", "you can do it."]))))))
tree(3)(T(["how was school today?", "what did you learn at school?"],
          T(["we learned about {subject}.", "the teacher told us about {subject}."],
            T(["that sounds interesting. tell me more.", "did you like it?"],
              T(["it was fun.", "a little boring, but okay.", "yes! i want to learn more."]))),
          T(["boring.", "it was okay."],
            T(["why boring?", "just okay?"], T(["we sat still all day.", "we just read the same page."],
                                               T(["tomorrow will be better.", "school is like that sometimes."]))))))
tree(3)(T(["do you like your teacher?"],
          T(["yes, she is very kind.", "yes, he tells funny stories."], T(["i'm glad.", "that's lucky."])),
          T(["not really, she is very strict.", "no, he shouts a lot."], T(["maybe you can talk to them.", "teachers mean well, mostly."]))))
tree(3)(T(["do you have any children?", "do you have kids?"],
          T(["yes, {smallnumber} of them.", "yes, a boy and a girl.", "one daughter. she is {number}."],
            T(["how lovely.", "they must keep you busy."], T(["they do!", "every minute of the day.", "i wouldn't change a thing."]))),
          T(["no, not yet.", "no, it's just me and my {pet}."], T(["maybe one day.", "a {pet} is good company too."]))))
tree(3)(T(["do you have brothers or sisters?"],
          T(["two brothers and a sister.", "one sister. she lives in {town}.", "a little brother. he's very annoying."],
            T(["do you get along?", "do you see them often?"], T(["mostly.", "not as often as i'd like.", "we fight, but we love each other."]))),
          T(["no, i'm an only child."], T(["was that lonely?", "lucky, you got all the attention."], T(["sometimes.", "ha, yes."])))))
tree(3)(T(["how is your {family}?", "how is your {family} doing?"],
          T(["much better, thanks.", "doing well.", "busy as always."], T(["glad to hear it.", "give them my best."])),
          T(["not so good, they're sick.", "they had a fall last week."], T(["oh no, i'm sorry.", "i hope they get well soon."],
                                                                            T(["thank you.", "me too."])))))
tree(3)(T(["look at that {animal}!", "do you see that {animal} over there?"],
          T(["where?", "i can't see it."], T(["over there, by the {place}.", "next to the tree."], T(["oh yes! so cute.", "i see it now."]))),
          T(["oh, how cute!", "wow, it's so big."], T(["can i pet it?", "should we feed it?"], T(["be gentle.", "careful, it might bite.", "sure, give it some {food}."])))))
tree(3)(T(["the cows got out again!", "the sheep broke the fence!", "the pigs are in the garden!"],
          T(["oh no, not again.", "quick, help me catch them."], T(["i'll go around the back.", "get some wheat, they'll follow it."],
                                                                    T(["got them!", "that's the last one."], T(["we need a better fence.", "phew."]))))))
tree(3)(T(["have you ever been to {town}?"],
          T(["yes, once. it was beautiful.", "yes, i was born there."], T(["what's it like?"], T(["busy and loud.", "quiet and green.", "the food is amazing."]))),
          T(["no, never.", "not yet."], T(["you should go one day.", "me neither."], T(["maybe next summer.", "i'd like that."])))))
tree(3)(T(["i'm going to {town} {future}.", "i'm leaving for {town} {future}."],
          T(["really? why?", "what for?"], T(["to visit my {family}.", "to sell my {item}.", "i want to see the sea."],
                                             T(["have a safe trip.", "bring me something back!", "sounds lovely."]))),
          T(["how long will you be gone?"], T(["about a week.", "just a few days.", "i don't know yet."], T(["we'll miss you.", "safe travels."])))))
tree(3)(T(["how was your trip?", "you're back! how was the journey?"],
          T(["long, but worth it.", "wonderful.", "terrible, it rained the whole way."],
            T(["what did you see?", "did anything happen?"], T(["mountains, rivers, and lots of sheep.", "i met a trader with llamas.", "i got lost twice."],
                                                               T(["sounds like an adventure.", "ha, of course you did."]))))))
tree(3)(T(["it's getting dark.", "the sun is going down."],
          T(["we should go inside.", "let's head home."], T(["good idea, monsters come out at night.", "yes, i'm tired anyway."])),
          T(["i'll light the torches.", "already? the day went so fast."], T(["thanks.", "time flies."]))))
tree(3)(T(["i can't sleep.", "are you awake?"],
          T(["me neither.", "yes. what's wrong?"], T(["i keep thinking about tomorrow.", "i heard a noise outside.", "i had a bad dream."],
                                                     T(["it's okay, try to rest.", "it was probably just the wind.", "do you want to talk about it?"],
                                                       T(["okay. good night.", "thanks.", "maybe tomorrow."]))))))
tree(3)(T(["what are you building?", "is that a new {structure}?"],
          T(["a {structure} for my family.", "yes, a {structure} out of {block}."],
            T(["it looks great.", "do you need help?", "how long will it take?"],
              T(["thanks!", "yes, hold this {tool}.", "maybe a week, if the weather holds."]))),
          T(["i'm not sure yet.", "a surprise."], T(["a surprise? now i'm curious.", "you'll figure it out."]))))
tree(3)(T(["i need more {block}.", "i ran out of {block}."],
          T(["i have some you can use.", "there's lots by the {place}."], T(["thank you!", "great, i'll go get some."])),
          T(["you could dig some up.", "the trader sells it."], T(["good idea.", "how much does he want?"], T(["a few emeralds.", "ask him yourself."])))))
tree(3)(T(["how are your {crop} growing?", "how is the farm?"],
          T(["very well this year.", "almost ready to harvest."], T(["that's great news.", "you'll need help with the harvest."], T(["yes, please come!", "i'll pay you in {food}."]))),
          T(["badly. the soil is too dry.", "the rabbits are eating everything."],
            T(["dig a ditch from the river.", "build a fence.", "plant some flowers to keep pests away."], T(["good idea.", "i'll try that tomorrow."])))))

# --- everyday life beyond the village ---
tree(3)(T(["a table for two, please.", "can we sit by the window?"],
          T(["of course, follow me.", "right this way."], T(["thank you.", "lovely."], T(["here are your menus.", "what would you like to drink?"],
                                                                                        T(["just water, please.", "two cups of tea.", "what do you recommend?"],
                                                                                          T(["the {food} is very good today.", "coming right up."]))))),
          T(["sorry, we're full tonight.", "you'll have to wait a few minutes."], T(["that's okay, we'll wait.", "oh, maybe another time."]))))
tree(2)(T(["excuse me, this soup is cold.", "i think there's a hair in my food."],
          T(["i'm so sorry, i'll bring you a new one.", "oh dear, let me fix that."], T(["thank you.", "no problem, it happens."]))))
tree(3)(T(["could i have the bill, please?", "how much do we owe you?"],
          T(["that's {smallnumber} {coin}.", "{smallnumber} {coin}, please."], T(["here you go. keep the change.", "here you are."], T(["thank you, come again!", "have a lovely evening."])))))
tree(2)(T(["i'd like a room for the night.", "do you have any rooms free?"],
          T(["yes, one left. {smallnumber} {coin} a night.", "for how many nights?"],
            T(["i'll take it.", "just one night.", "two nights, please."], T(["here is your key. breakfast is at dawn.", "room three, up the stairs."], THANKS))),
          T(["sorry, we're full.", "no, the festival filled every room."], T(["do you know anywhere else?"], T(["try the inn by the bridge.", "the farmer sometimes rents his barn."])))))
tree(3)(T(["i'd like to borrow this book.", "can i take this book home?"],
          T(["of course. bring it back in two weeks.", "yes, just write your name here."], T(["thank you.", "will do."]))))
tree(2)(T(["do you have any books about {subject}?"],
          T(["yes, on the top shelf.", "we have a few, in the back."], THANKS),
          T(["sorry, someone borrowed the last one."], T(["oh well.", "when will it be back?"], T(["next week, i think."])))))
tree(3)(T(["just a little off the top, please.", "can you cut my hair short?"],
          T(["of course. sit down.", "sure, how short?"], T(["not too short.", "very short, it's hot outside."], T(["there, what do you think?"], T(["perfect, thank you!", "hmm, a bit too short.", "i love it."]))))))
tree(2)(T(["i'd like to send this letter to {town}.", "how much to send a package to {town}?"],
          T(["that's {smallnumber} coins.", "it will take about {smallnumber} days."], T(["okay, here you go.", "fine, thank you."]))))
tree(3)(T(["when does the next cart to {town} leave?", "is this the boat to {town}?"],
          T(["in about an hour.", "at noon.", "yes, we leave soon."], T(["how long is the trip?"], T(["about half a day.", "two days, if the weather is good."], T(["thank you.", "okay, i'll wait here."])))),
          T(["you just missed it.", "no, that one is over there."], T(["oh no.", "thanks!"]))))
tree(2)(T(["i'm here for the job.", "i heard you need a new {job}."],
          T(["yes. tell me about yourself.", "do you have any experience?"],
            T(["i worked as {a_job} for {smallnumber} years.", "not much, but i learn fast."],
              T(["good. you can start tomorrow.", "we'll give you a try.", "i'll think about it and let you know."], T(["thank you so much!", "i won't let you down.", "okay, thank you."]))))))
tree(2)(T(["my {tool} broke. can you fix it?", "can you repair this {tool}?"],
          T(["let me see. yes, come back tomorrow.", "it will cost {smallnumber} {coin}."], T(["thank you.", "that's fine."])),
          T(["no, you need a new one.", "it's too old to fix."], T(["oh, how much is a new one?"], T(["{smallnumber} {coin}.", "i can make one for you."])))))
tree(3)(T(["happy birthday!", "happy birthday! how old are you now?"],
          T(["thank you!", "{number} today!"], T(["we made you a cake.", "here's a little present."], T(["oh, you shouldn't have!", "thank you so much!"])))))
tree(2)(T(["congratulations on the wedding!", "you two make a lovely couple."],
          T(["thank you!", "thank you, we're so happy."], T(["when is the big day?", "where will you live?"], T(["next {season}.", "in a little house by the {place}."])))))
tree(2)(T(["the baby is finally asleep.", "the baby was up all night again."],
          T(["you must be exhausted.", "go and rest, i'll watch her."], T(["thank you, i really need it.", "i'll just close my eyes for a bit."]))))
tree(3)(T(["i don't feel well.", "i think i'm getting sick."],
          T(["you should rest.", "do you have a fever?"], T(["maybe a little.", "i'm so cold."], T(["i'll make you some hot tea.", "go to bed, i'll call the doctor."], THANKS)))))
tree(2)(T(["how much does this cost?", "is this on sale?"],
          T(["{smallnumber} {coin}.", "today it's half price."], T(["i'll take it.", "that's too expensive.", "great, i'll take two."]))))
tree(2)(T(["can i pay you tomorrow?", "i forgot my coins at home."],
          T(["sure, i trust you.", "just this once."], T(["thank you, i'll bring it first thing.", "you're very kind."])),
          T(["sorry, no.", "i can keep it for you until tomorrow."], T(["okay, i understand.", "thank you."]))))
tree(3)(T(["what time do you close?", "are you open tomorrow?"],
          T(["at sunset.", "yes, from morning until noon.", "not tomorrow, it's a holiday."], T(["thanks.", "okay, i'll come back then."]))))
tree(2)(T(["you're standing on my foot.", "ow, you bumped into me."],
          T(["oh! i'm so sorry.", "sorry, i didn't see you."], T(["it's alright.", "no harm done."]))))
tree(2)(T(["someone stole my {item}!", "a thief took my bag!"],
          T(["what? when?", "did you see who it was?"], T(["just now, by the market.", "a man in a dark cloak."], T(["let's tell the guards.", "which way did he go?"], T(["that way!", "i didn't see."]))))))
tree(2)(T(["the guards caught the thief.", "they found the stolen sheep."],
          T(["good, i hope he gives it all back.", "finally, justice."], T(["he says he was hungry.", "they're taking him to the mayor."], T(["then give him work, not jail.", "let's see what the mayor says."])))))
tree(2)(T(["who are you voting for?", "do you like the new mayor?"],
          T(["the baker. she listens to people.", "i haven't decided."], T(["fair enough.", "the baker would be good."])),
          T(["not really, taxes went up again.", "yes, the roads are better now."], T(["true.", "i hadn't noticed."]))))
tree(2)(T(["i owe you money, don't i?", "i haven't forgotten the coins i owe you."],
          T(["yes, {smallnumber} {coin}.", "don't worry about it."], T(["here, take them now.", "i'll pay you on market day."], T(["thank you.", "that's fine."])))))
tree(2)(T(["i had the strangest dream last night.", "i dreamed i could fly."],
          T(["what happened in it?", "really? tell me."], T(["i was flying over the village.", "i was a {animal} talking to the king."], T(["ha, that's funny.", "dreams are so strange.", "what do you think it means?"],
                                                                                                                                  T(["probably nothing.", "maybe i ate too much cheese."]))))))
tree(2)(T(["what do you think happens when we die?", "do you think there's something after this life?"],
          T(["i don't know. nobody does.", "i hope we see the people we loved."], T(["that's a nice thought.", "me too."]))))
tree(2)(T(["what makes you happy?", "what's the best day you can imagine?"],
          T(["good food and good friends.", "a warm fire on a cold night.", "watching the sun rise over the fields."], T(["that sounds perfect.", "same for me."]))))
tree(3)(T(["what are you doing this weekend?", "any plans for the holiday?"],
          T(["nothing much, just resting.", "i'm going {hobby}.", "visiting my {family}."], T(["sounds nice.", "can i come?", "have fun!"], T(["sure, the more the merrier.", "thanks!", "maybe next time."])))))
tree(2)(T(["do you play any music?", "can you play an instrument?"],
          T(["i play the flute a little.", "yes, the drum.", "no, but i love to sing."], T(["play something for us!", "you should play at the festival."], T(["maybe later.", "okay, just one song.", "i'm too shy."])))))
tree(2)(T(["who won the race?", "did you win?"],
          T(["{name} won again.", "i came second!", "no, i tripped at the start."], T(["well done anyway.", "next time.", "ha, poor you."]))))
tree(3)(T(["where did you put the {tool}?", "have you seen the {tool}?"],
          T(["in the shed.", "on the table.", "your brother took it."], T(["thanks.", "of course he did."])),
          T(["i haven't seen it.", "no idea."], T(["it can't just disappear.", "i'll keep looking."]))))
tree(2)(T(["it's too hot today.", "i'm melting in this heat."],
          T(["let's go swimming in the lake.", "stay in the shade.", "drink some water."], T(["good idea.", "i'll race you there!"]))))
tree(2)(T(["it's freezing!", "my hands are so cold."],
          T(["come sit by the fire.", "here, take my gloves.", "let's have some hot soup."], T(["thank you.", "that's better."]))))
tree(2)(T(["there's a spider in my room!", "there's a mouse in the kitchen!"],
          T(["it's more scared of you than you are of it.", "i'll get it out."], T(["please hurry!", "just get rid of it."], T(["there, it's gone.", "got it."])))))
tree(2)(T(["can you keep a secret?", "promise you won't tell anyone?"],
          T(["of course.", "i promise."], T(["i'm going to ask {name} to marry me.", "i found a cave full of iron."], T(["that's wonderful!", "your secret is safe with me.", "wow!"])))))
tree(2)(T(["i'm bored.", "there's nothing to do."],
          T(["let's play {game}.", "we could go {hobby}.", "help me with the chores then."], T(["okay!", "no thanks.", "fine, i'll help."]))))
tree(2)(T(["you look different today.", "did you cut your hair?"],
          T(["yes, do you like it?", "i got new clothes."], T(["it suits you.", "you look great."], THANKS))))
tree(2)(T(["i'm learning to {skill}.", "my {family} is teaching me to {skill}."],
          T(["how is it going?", "is it hard?"], T(["slowly, but i'm getting better.", "very hard, but fun."], T(["keep at it.", "you'll be great at it."])))))
tree(3)(T(["what's the news?", "anything new in the village?"],
          T(["{name} had a baby.", "a trader arrived with strange animals.", "the bridge is finally fixed.", "nothing much."],
            T(["really?", "that's good.", "about time!", "quiet times are good times."]))))
tree(2)(T(["the iron golem is walking around again.", "did you see the iron golem?"],
          T(["good, it keeps us safe.", "it gave a flower to a child."], T(["it's a gentle giant.", "it scares me a little."]))))
tree(2)(T(["the bell is ringing!", "they're ringing the bell!"],
          T(["a raid! get inside!", "everyone, into the houses!"], T(["hurry, hurry!", "close the doors!"], T(["are we safe?"], T(["stay quiet until it's over.", "the golem will protect us."]))))))
tree(2)(T(["you saved my life.", "if you hadn't come, i'd be gone."],
          T(["anyone would have done the same.", "i'm just glad you're safe."], T(["i'll never forget it.", "thank you, truly."]))))
tree(2)(T(["i miss the way things used to be.", "everything is changing so fast."],
          T(["change can be good too.", "me too, sometimes."], T(["maybe you're right.", "i suppose."]))))


def tree_dialogue(rng, words):
    nodes = [n for n, w in TREES for _ in range(w)]
    return walk(rng.choice(nodes), rng, words)


# --- more of life: health, money, seasons, crafts, feelings, stories, sky and sea ---
tree(2)(T(["i keep sneezing.", "my nose won't stop running."],
          T(["maybe it's the flowers.", "you might be catching a cold."], T(["i hope not.", "it's the hay, every summer."], T(["drink some honey tea.", "stay away from the barn for a while."])))))
tree(2)(T(["i twisted my ankle.", "i think i broke my finger."],
          T(["can you stand on it?", "let me look. does this hurt?"], T(["a little.", "ow, yes!", "not really."], T(["it's only a sprain. rest it.", "we should go to the healer.", "wrap it tight and keep it up."])))))
tree(2)(T(["the healer says i need to rest for a week.", "the doctor told me to stop working so hard."],
          T(["then you should listen.", "a week? who will look after the farm?"], T(["my brother will help.", "i don't know yet."], T(["good. take care of yourself.", "we'll all pitch in."])))))
tree(2)(T(["grandpa is getting very forgetful.", "grandma doesn't remember my name anymore."],
          T(["that must be hard.", "it happens when people get old."], T(["it breaks my heart.", "she still smiles when she sees me."], T(["be patient with her.", "she still loves you, even if she forgets."])))))
tree(2)(T(["how much did you pay for that?", "was it expensive?"],
          T(["only {smallnumber} {coin}.", "far too much.", "it was a gift."], T(["what a bargain!", "you got cheated.", "lucky you."]))))
tree(2)(T(["i'm saving up for a {thing}.", "i want to buy a {thing} one day."],
          T(["how much do you have so far?", "that's a good goal."], T(["almost half.", "not much yet.", "enough for the first part."], T(["keep going.", "you'll get there."])))))
tree(2)(T(["the price of bread went up again.", "everything is so expensive this year."],
          T(["the harvest was bad.", "the traders blame the roads."], T(["we'll have to tighten our belts.", "maybe we should bake our own."], T(["i'll teach you.", "good idea."])))))
tree(2)(T(["spring is finally here!", "the snow is melting at last."],
          T(["i can't wait to plant the garden.", "the birds are singing again."], T(["me too.", "the days are getting longer."]))))
tree(2)(T(["the leaves are turning red.", "autumn is coming."],
          T(["time to gather the harvest.", "i love the colours this time of year."], T(["and to store wood for winter.", "it's my favourite season."]))))
tree(2)(T(["it's the longest day of the year.", "happy midsummer!"],
          T(["let's light the bonfire tonight.", "the sun will hardly set."], T(["i'll bring the drums.", "we'll dance until morning."]))))
tree(2)(T(["the river froze over.", "the lake is frozen solid."],
          T(["let's go skating!", "don't walk on it, the ice might be thin."], T(["just near the edge.", "you're right, let's wait."], T(["be careful.", "okay."])))))
tree(2)(T(["can you teach me to knit?", "how do you weave a basket?"],
          T(["sure, it's easy once you start.", "of course. first you need good {material}."], T(["like this?", "where do i get that?"], T(["almost, hold it tighter.", "by the river.", "perfect, now keep going."])))))
tree(2)(T(["i made this pot myself.", "look at the chair i built."],
          T(["it's beautiful!", "wow, you're really talented.", "it's a bit wobbly."], T(["thank you!", "it took me three days.", "i'll fix that leg."]))))
tree(2)(T(["tell me a story.", "can you tell us a story?"],
          T(["once upon a time, a brave {job} lived by the {place}.", "long ago, a dragon slept under the mountain."],
            T(["what happened?", "and then?"], T(["one day, a stranger came to the village with a secret.", "a little girl found its egg."],
                                                  T(["and?", "go on!"], T(["and they all lived happily ever after.", "that's all for tonight. go to sleep."])))))))
tree(2)(T(["do you know any old legends?", "is it true there's treasure in the old mine?"],
          T(["they say a giant once lived in that cave.", "my grandfather swore it was true."], T(["do you believe it?", "has anyone found it?"], T(["who knows?", "not yet. maybe you'll be the first."])))))
tree(2)(T(["look at all the stars.", "is that the moon rising?"],
          T(["there are so many tonight.", "yes, it's full tonight."], T(["can you see the shooting star?", "it's so bright."], T(["make a wish!", "beautiful."])))))
tree(2)(T(["the sea is rough today.", "the boats didn't go out this morning."],
          T(["too windy to fish.", "a storm is coming from the west."], T(["we'll go tomorrow then.", "i hope the sailors are safe."]))))
tree(2)(T(["we caught a huge fish!", "the nets were full today."],
          T(["wonderful! what kind?", "that's great news for the market."], T(["a big salmon.", "mostly cod and herring."], T(["let's cook it tonight.", "i'll buy some."])))))
tree(2)(T(["i'm so proud of you.", "you did really well today."],
          T(["really?", "thank you, that means a lot."], T(["really. you worked so hard.", "you deserve it."]))))
tree(2)(T(["i'm nervous about tomorrow.", "what if i fail?"],
          T(["you've practised a lot.", "then you try again. that's how we learn."], T(["i guess so.", "thanks, i needed that."]))))
tree(2)(T(["i'm so angry right now.", "i can't believe he did that!"],
          T(["take a deep breath.", "what happened?"], T(["he broke my fence and didn't even say sorry.", "he lied to me."], T(["that's not right. talk to him tomorrow, when you're calm.", "i'd be upset too."])))))
tree(2)(T(["i feel lonely since my friend left.", "i miss having someone to talk to."],
          T(["you can always talk to me.", "come to dinner with us tonight."], T(["thank you, really.", "i'd like that."]))))
tree(2)(T(["i'm scared of the dark.", "i don't like thunderstorms."],
          T(["lots of people are.", "the thunder can't hurt you."], T(["will you stay with me?", "it's so loud."], T(["of course.", "let's count the seconds between the flash and the boom."])))))
tree(2)(T(["thank you for everything.", "i couldn't have done it without you."],
          T(["that's what friends are for.", "you would do the same for me."], T(["i would.", "always."]))))
tree(2)(T(["let's race to the {place}!", "bet you can't catch me!"],
          T(["you're on!", "ready, set, go!"], T(["i win!", "no fair, you started early!"], T(["rematch!", "ha, next time."])))))
tree(2)(T(["what's your favourite colour?", "what's your favourite animal?"],
          T(["{colour}.", "i like {colour} best.", "the {animal}, of course."], T(["mine too!", "really? why?"], T(["it reminds me of home.", "i don't know, i just like it."])))))
tree(2)(T(["i've never seen the ocean.", "have you ever been on a ship?"],
          T(["it's huge, you can't see the other side.", "once, and i was sick the whole time."], T(["i'd love to see it one day.", "ha, poor you."]))))
tree(2)(T(["the well is dry.", "there's no water in the well."],
          T(["it hasn't rained in weeks.", "we'll have to carry water from the river."], T(["that's a long walk.", "i'll get the buckets."]))))
tree(2)(T(["a stranger is asking about you.", "someone was looking for you this morning."],
          T(["who?", "what did they want?"], T(["they didn't say.", "a tall man with a red hat.", "a woman from the city."], T(["strange.", "i'll go and find out."])))))
tree(2)(T(["the mayor wants to build a wall around the village.", "they want to cut down the old forest."],
          T(["good, it will keep us safe.", "that's a terrible idea."], T(["it will cost a lot.", "we should talk at the meeting."], T(["safety is worth it.", "let's speak up together."])))))
tree(2)(T(["the new baby has your eyes.", "your son looks just like you."],
          T(["everyone says that.", "really? i think he looks like his mother."], T(["he's beautiful.", "he's lucky to have you both."]))))
tree(2)(T(["we're moving to {town}.", "we've decided to leave the village."],
          T(["oh no, we'll miss you!", "really? when?"], T(["next {season}.", "after the harvest."], T(["you must visit.", "promise to write."])))))
tree(2)(T(["can i stay at your house tonight?", "i've got nowhere to sleep."],
          T(["of course, the guest bed is free.", "you can sleep by the fire."], T(["thank you so much.", "you're a lifesaver."]))))
tree(2)(T(["what's the best way to keep wolves away from the sheep?", "how do i stop foxes taking the chickens?"],
          T(["get a good dog.", "build a tall fence.", "keep them inside at night."], T(["good idea.", "i'll start tomorrow."]))))
tree(2)(T(["how do i make a fire?", "can you show me how to light a fire?"],
          T(["first gather dry sticks and leaves.", "use flint and steel, like this."], T(["then what?", "it's not catching."], T(["blow on it gently.", "add bigger logs once it burns.", "the wood is too wet, try these."])))))
tree(2)(T(["the sheep need shearing.", "it's time to shear the sheep."],
          T(["i'll get the shears.", "already? the summer went fast."], T(["hold this one still.", "start with the big one."], T(["got it.", "she doesn't like it!"])))))
tree(2)(T(["the cow is about to give birth.", "the mare is having her foal!"],
          T(["get some clean straw.", "should we call the farmer?"], T(["it's coming!", "she's doing fine."], T(["look, it's standing already!", "a healthy calf!"])))))
tree(2)(T(["i heard wolves howling last night.", "there were tracks by the barn this morning."],
          T(["we should keep the animals inside.", "what kind of tracks?"], T(["big ones, like a wolf.", "small ones, maybe a fox."], T(["let's tell the guard.", "set a trap tonight."])))))
tree(2)(T(["have you finished your chores?", "did you feed the chickens?"],
          T(["yes, all done.", "not yet, i'm going now."], T(["good.", "hurry, they're hungry."]))))
tree(2)(T(["why do we have to go to bed so early?", "can i stay up late tonight?"],
          T(["because you need to grow.", "just this once, it's a holiday."], T(["that's not fair.", "yay!"], T(["life isn't always fair.", "but only until the fire goes out."])))))
tree(2)(T(["the bread didn't rise.", "my cake is flat."],
          T(["was the yeast fresh?", "maybe the oven wasn't hot enough."], T(["i don't know.", "oh, i forgot to heat it."], T(["try again tomorrow.", "it happens to everyone."])))))
tree(2)(T(["i want to become a {job}.", "i'd like to learn to be a {job}."],
          T(["that's a fine trade.", "you'll need to find a master to teach you."], T(["do you know one?", "i've already asked."], T(["the old one by the {place} takes apprentices.", "good luck!"])))))
tree(2)(T(["what's the date today?", "how many days until the festival?"],
          T(["the third of {month}.", "about {smallnumber} days."], T(["thank you.", "it's so soon!"]))))
tree(2)(T(["i lost my way in the forest.", "i got lost coming home."],
          T(["how did you find your way back?", "you must have been scared."], T(["i followed the river.", "i saw the church tower.", "a dog led me home."], T(["clever.", "lucky!"])))))
tree(2)(T(["are you coming to the wedding?", "who's getting married?"],
          T(["of course i'm coming.", "{name} and the baker's son."], T(["what will you wear?", "finally!"], T(["my blue coat.", "i know, it took them years."])))))
tree(2)(T(["the old {job} died last night.", "did you hear the sad news?"],
          T(["oh no. he was so kind.", "what happened?"], T(["he was very old.", "he fell ill last week."], T(["we'll all miss him.", "the funeral is tomorrow."])))))
tree(2)(T(["you look worried.", "is everything alright at home?"],
          T(["my son has a fever.", "we can't pay the rent this month."], T(["i'll fetch the healer.", "how can i help?"], T(["thank you.", "just being here helps."])))))
tree(2)(T(["shh, the baby's sleeping.", "can you be quiet? i'm reading."],
          T(["sorry!", "oops, sorry."], T(["it's okay.", "thanks."]))))
tree(2)(T(["i can't decide what to do.", "should i take the job in {town}?"],
          T(["what does your heart say?", "what are the good and bad sides?"], T(["i want to go, but i'm scared.", "more money, but far from family."], T(["sometimes you have to take a chance.", "family matters most."])))))
tree(2)(T(["i finally finished the {structure}!", "the {structure} is done!"],
          T(["congratulations!", "let me see it!"], T(["come on, i'll show you.", "it took all summer."], T(["it's amazing.", "you should be proud."])))))
