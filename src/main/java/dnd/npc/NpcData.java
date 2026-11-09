package dnd.npc;

import dnd.names.NameData.Race;

import java.util.ArrayList;
import java.util.List;

/** The tables behind the NPC generator. All wording is original; nothing is copied from a published table. */
public final class NpcData {
    private NpcData() { }

    /**
     * A job a person can have.
     *
     * @param template id of the card template whose stats suit them (see CardTemplates), e.g. "commoner", "guard"
     * @param knows    things this kind of person is likely to know or be able to offer, for improvising conversation
     */
    public record Profession(String name, String category, String template, List<String> knows) { }

    public enum AgeGroup {
        YOUNG("Young", 16, 24), ADULT("Adult", 25, 44), MIDDLE_AGED("Middle-aged", 45, 64), ELDERLY("Elderly", 65, 90);

        public final String label;
        final int humanMin, humanMax;

        AgeGroup(String label, int humanMin, int humanMax) {
            this.label = label;
            this.humanMin = humanMin;
            this.humanMax = humanMax;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Attitude {
        FRIENDLY("Friendly"), NEUTRAL("Neutral"), WARY("Wary"), HOSTILE("Hostile");

        public final String label;

        Attitude(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** How long this race lives compared with a human; scales the age ranges. */
    static double lifespan(Race race) {
        return switch (race) {
            case ELF -> 6.0;
            case DWARF -> 3.0;
            case GNOME -> 3.5;
            case HALF_ELF -> 1.8;
            case HALFLING -> 1.3;
            case AASIMAR -> 1.3;
            case GOLIATH -> 0.9;
            case DRAGONBORN, HALF_ORC -> 0.85;
            case ORC -> 0.8;
            case KOBOLD -> 0.7;
            case GOBLIN, TORTLE -> 0.6;
            default -> 1.0;
        };
    }

    /** The age range (inclusive) for a race and age group. */
    public static int[] ageRange(Race race, AgeGroup group) {
        double k = lifespan(race);
        int lo = Math.max(group == AgeGroup.YOUNG ? 12 : 1, (int) Math.round(group.humanMin * (group == AgeGroup.YOUNG ? Math.min(k, 1.6) : k)));
        int hi = Math.max(lo + 1, (int) Math.round(group.humanMax * k));
        return new int[]{lo, hi};
    }

    // ================================================================ professions

    private static Profession p(String name, String category, String template, String... knows) {
        return new Profession(name, category, template, List.of(knows));
    }

    public static final String COMMONERS = "Commoners and labourers";
    public static final String CRAFTERS = "Merchants and crafters";
    public static final String SERVICE = "Innkeepers and servants";
    public static final String LAW = "Law and military";
    public static final String LEARNING = "Faith and learning";
    public static final String NOBLE = "Nobility and officials";
    public static final String WILD = "Wilderness and the road";
    public static final String SHADY = "Shady trades";

    public static final List<Profession> PROFESSIONS = List.of(
            p("Farmer", COMMONERS, "commoner", "the weather, the harvest and which fields have gone bad", "who has been buying or selling land", "strange tracks or lights near the farms"),
            p("Fisher", COMMONERS, "commoner", "what has been seen on the water", "which boats came in late or did not come back", "where the fish have stopped biting, and why they say so"),
            p("Miller", COMMONERS, "commoner", "who is short of grain and who is hoarding it", "the gossip of every farmer who passes through", "an old wheel or cellar nobody likes to go near"),
            p("Shepherd", COMMONERS, "commoner", "the lonely hill paths and what lurks on them", "missing sheep and what took them", "the weather, days ahead of anyone else"),
            p("Woodcutter", COMMONERS, "commoner", "the trails through the forest", "where the trees have been marked or felled by someone else", "sounds heard in the woods after dark"),
            p("Miner", COMMONERS, "commoner", "which tunnels are safe and which have been sealed", "the foreman's temper and the mine owner's debts", "noises from deep in the rock"),
            p("Labourer", COMMONERS, "commoner", "who is hiring and who is not paying", "the state of the roads, walls and bridges", "what the work crews have found digging"),
            p("Dockhand", COMMONERS, "commoner", "which ships have come and gone", "cargo that was loaded at night", "who pays well and asks no questions"),
            p("Gravedigger", COMMONERS, "commoner", "who has died lately and how", "graves that were disturbed", "which families have secrets worth burying"),
            p("Beggar", COMMONERS, "commoner", "who walks the streets at night", "which doors are watched and which guards take bribes", "faces that do not belong in this part of town"),
            p("Chimney sweep", COMMONERS, "commoner", "the layout of rooftops and the grand houses", "who burns what in their hearths", "which servants are unhappy"),
            p("Blacksmith", CRAFTERS, "commoner", "who has ordered weapons and armour lately", "the quality of any blade the party carries", "which patrols and mercenaries are in the area"),
            p("Baker", CRAFTERS, "commoner", "who is up before dawn and why", "the morning gossip of the whole street", "who has suddenly been buying far more bread than usual"),
            p("Butcher", CRAFTERS, "commoner", "which farms are selling and which are struggling", "strange animals found dead or butchered", "what folk say over the block"),
            p("Tailor", CRAFTERS, "commoner", "what the wealthy are wearing and who is commissioning disguises", "measurements and secrets of half the town", "who has suddenly come into money"),
            p("Cobbler", CRAFTERS, "commoner", "whose boots carry mud from odd places", "who walks a lot and who has stopped walking", "the state of the roads, by the wear on soles"),
            p("Carpenter", CRAFTERS, "commoner", "which buildings have hidden rooms or weak beams", "who has ordered coffins, doors or barricades", "the town's building plans"),
            p("Potter", CRAFTERS, "commoner", "where the good clay is and who else digs there", "marks and seals on jars that pass through town", "old pieces dug up in nearby fields"),
            p("Jeweller", CRAFTERS, "commoner", "the true value of any gem or trinket", "who has been selling jewellery they should not own", "which noble houses are quietly in debt"),
            p("Apothecary", CRAFTERS, "commoner", "who has been buying poisons or strange herbs", "the cure for what ails the party, for a price", "the sicknesses spreading through town"),
            p("Shopkeeper", CRAFTERS, "commoner", "what everyone in town has been buying", "rumours, prices and who owes money", "which goods have become hard to find, and why"),
            p("Peddler", CRAFTERS, "commoner", "news from three towns in either direction", "odd goods and where they came from", "which roads are safe this week"),
            p("Brewer", CRAFTERS, "commoner", "which taverns are thriving and which are failing", "who drinks too much and what they let slip", "an old recipe or a rival's sabotage"),
            p("Innkeeper", SERVICE, "commoner", "who is staying in town and where they came from", "the latest rumours, true and false", "rooms, meals and who might be listening at the walls"),
            p("Barkeep", SERVICE, "commoner", "what the regulars have been muttering", "who has been flashing coin", "which customers are trouble"),
            p("Cook", SERVICE, "commoner", "who eats what, and who is suddenly off their food", "the kitchen gossip of the whole house", "supplies that have gone missing"),
            p("Servant", SERVICE, "commoner", "the private habits of a rich household", "who visits at night", "where the keys and the spare doors are"),
            p("Stable master", SERVICE, "commoner", "whose horses came in tired and muddy", "the best mounts and the worst-tempered", "who left town in a hurry"),
            p("Messenger", SERVICE, "commoner", "what was carried and to whom", "the roads, the weather and the delays", "the locations of every important door in town"),
            p("Bard", SERVICE, "commoner", "songs, tales and legends of the region", "what the audience reacts to and who got quiet", "the scandals of the great houses"),
            p("Town guard", LAW, "guard", "patrol routes and shift changes", "recent crimes and open investigations", "which of their officers can be trusted"),
            p("Watch captain", LAW, "guard", "the whole town's trouble, in order of importance", "who has been arrested and who walked free", "orders from above that they dislike"),
            p("Soldier", LAW, "guard", "troop movements and local garrisons", "the mood in the barracks", "old campaigns and who fought where"),
            p("Jailer", LAW, "guard", "who is held and why", "which prisoners talk in their sleep", "the weak points of the cells"),
            p("Customs officer", LAW, "guard", "what has been coming through the gates", "who pays bribes and who does not", "inspected cargo that vanished from the records"),
            p("Tax collector", LAW, "guard", "who is behind on their dues", "the true wealth of the town", "the people who hate them, and a few who might hurt them"),
            p("Militia volunteer", LAW, "guard", "the defences and how thin they really are", "who volunteered and who was pushed", "what the locals are afraid of"),
            p("Priest", LEARNING, "commoner", "confessions they will not repeat and hints they cannot help giving", "the faith's calendar, rites and local shrines", "sick, dying or troubled people in the parish"),
            p("Acolyte", LEARNING, "commoner", "the temple's routines and who comes and goes", "what the senior clergy argue about", "old texts kept in the vault"),
            p("Healer", LEARNING, "commoner", "who has been injured, and in what way", "diseases moving through the area", "herbs, remedies and where to find them"),
            p("Scribe", LEARNING, "commoner", "contracts, letters and who sent them", "forged or altered documents", "the handwriting of half the town"),
            p("Sage", LEARNING, "commoner", "history, lore and the meaning of old symbols", "a rival scholar and what they are after", "books worth finding and where they are kept"),
            p("Teacher", LEARNING, "commoner", "the children, and through them every household", "who in town can read, and who pretends to", "old stories parents no longer tell"),
            p("Librarian", LEARNING, "commoner", "which books were borrowed and not returned", "rare volumes and who wants them", "the layout of the archive, including the locked part"),
            p("Alchemist", LEARNING, "commoner", "strange reagents and who has been buying them", "potions, poisons and their antidotes", "an experiment gone wrong not long ago"),
            p("Fortune teller", LEARNING, "commoner", "what the whole town hopes and fears", "a sharp guess about what the party wants", "omens they invented and omens they did not"),
            p("Noble", NOBLE, "commoner", "politics, rivalries and who owes whom", "a great house's reputation and its stains", "invitations, introductions and favours"),
            p("Steward", NOBLE, "commoner", "the accounts of a great house", "who is hired, fired or quietly paid off", "the household's visitors and their business"),
            p("Mayor", NOBLE, "commoner", "the town's affairs, budgets and troubles", "who they are afraid of", "what they will offer to have a problem solved quietly"),
            p("Magistrate", NOBLE, "commoner", "the cases before the court", "bribes offered and refused", "who has been quietly let off"),
            p("Courtier", NOBLE, "commoner", "the gossip of the court", "who is rising and who is about to fall", "a secret that could be traded"),
            p("Guild master", NOBLE, "commoner", "the trade's prices, members and disputes", "which workshops are failing or being bought up", "strikes, sabotage and rivals"),
            p("Town crier", NOBLE, "commoner", "every official announcement, early", "who pays to have things announced", "news that was never cried"),
            p("Hunter", WILD, "commoner", "game trails and what has been driving the game away", "tracks of things that should not be here", "safe camps and dangerous ground"),
            p("Guide", WILD, "commoner", "the shortest and safest routes", "weather, rivers and passes", "which landmarks have changed"),
            p("Hermit", WILD, "commoner", "something old and odd about the nearby wilds", "who has come by and what they wanted", "a warning nobody has listened to"),
            p("Herbalist", WILD, "commoner", "where rare plants grow", "what is poisonous and what is a cure", "the places the plants have started growing wrong"),
            p("Trapper", WILD, "commoner", "what is stealing from the traps", "fur prices and who is buying", "the dens and lairs nearby"),
            p("Ferryman", WILD, "commoner", "who crossed and at what hour", "the river's moods and hidden dangers", "passengers who would not give their names"),
            p("Caravan driver", WILD, "commoner", "news from distant places", "bandits on the road and how they work", "goods that are in short supply ahead"),
            p("Thief", SHADY, "bandit", "which houses are poorly guarded", "who fences what", "the guild's rules and its grudges"),
            p("Smuggler", SHADY, "bandit", "hidden routes and the people who watch them", "what is moving that should not be", "which officials look the other way"),
            p("Fence", SHADY, "bandit", "who has stolen goods to sell", "the true price of anything", "a particular item everyone is looking for"),
            p("Informant", SHADY, "bandit", "who is talking to whom", "secrets for sale, at a price", "who else has been asking the same questions"),
            p("Gambler", SHADY, "bandit", "who is in debt and to whom", "rigged games and who runs them", "rumours from every table in town"),
            p("Con artist", SHADY, "bandit", "who has been taken in lately", "which names and titles are borrowed", "the easiest mark in town, and why"),
            p("Bandit", SHADY, "bandit", "the road, its travellers and its tolls", "the gang's camps, leaders and rivals", "which merchants pay for safe passage"));

    public static List<String> categories() {
        List<String> out = new ArrayList<>();
        for (Profession pr : PROFESSIONS) if (!out.contains(pr.category())) out.add(pr.category());
        return out;
    }

    // ================================================================ descriptions

    public static final String[] APPEARANCE = {
            "broad-shouldered with ink-stained fingers", "thin as a rake and always shivering", "round-faced and rosy-cheeked", "tall and stooped, ducking under every doorway",
            "short and wiry with quick eyes", "a heavy scar across one cheek", "missing two fingers on the left hand", "a thick, carefully oiled moustache",
            "long grey hair tied back with a leather cord", "a shaved head covered in faded tattoos", "freckled, with a gap-toothed grin", "one eye clouded white",
            "a crooked, often-broken nose", "hands rough and cracked from work", "immaculately clean, down to the fingernails", "mud on the boots and straw in the hair",
            "bright, mismatched clothes in patches", "a faded uniform kept spotless", "a heavy cloak far too warm for the weather", "jangling with small charms and trinkets",
            "a wide-brimmed hat pulled low", "a stained apron they never seem to take off", "an old limp, worse in the cold", "a magnificent beard with beads braided in",
            "pale and tired, with dark rings under the eyes", "sun-darkened skin and deep laugh lines", "a nervous habit of glancing over one shoulder", "a loud, bright waistcoat",
            "ears pierced with a dozen small rings", "a burn scar creeping up one forearm", "spectacles held together with thread", "a jaw that is always clenched",
            "gentle, dark eyes that miss nothing", "unusually good boots for someone of their station", "a missing front tooth and a whistle when they speak", "hair dyed an improbable colour",
            "a visible family resemblance to someone important", "a faint smell of smoke and herbs", "tiny, precise movements", "a wolfish, easy smile",
            "an arm in a sling, freshly bandaged", "a lazy eye that wanders when they are tired", "stooped under a heavy pack", "a necklace of carved bone beads"};

    public static final String[] PERSONALITY_WARM = {
            "generous to a fault", "endlessly curious", "quick to laugh", "patient and kind", "fiercely loyal", "an incurable gossip",
            "easily moved to tears", "protective of the young", "hospitable to strangers", "optimistic against all evidence", "a natural storyteller", "humble and soft-spoken",
            "enthusiastic about everything", "forgiving", "romantic", "a born peacemaker", "brave when it counts", "always has a kind word"};

    public static final String[] PERSONALITY_NEUTRAL = {
            "practical and no-nonsense", "careful with money", "slow to make up their mind", "dry-humoured", "deeply superstitious", "a stickler for the rules",
            "talks too much when nervous", "tired and overworked", "proud of their work", "chatty about the weather", "easily distracted", "plain-spoken to the point of rude",
            "forgetful", "obsessed with tidiness", "a lover of routine", "nostalgic for how things used to be", "quietly ambitious", "always hungry"};

    public static final String[] PERSONALITY_COLD = {
            "suspicious of strangers", "bitter about the past", "short-tempered", "sneering and superior", "greedy", "sullen and silent",
            "quick to take offence", "holds a grudge forever", "cruel when they think no one is looking", "obsessed with status", "afraid of everything", "sarcastic",
            "lies without noticing", "bullying towards those below them", "cowardly", "ruthlessly self-interested", "resentful of the party's weapons", "condescending"};

    public static final String[] MANNERISMS = {
            "taps a finger when thinking", "never looks anyone in the eye", "cracks their knuckles constantly", "hums the same tune without noticing", "wipes their hands on whatever is nearby",
            "answers a question with a question", "touches a lucky charm when nervous", "stands too close", "keeps a hand near a hidden knife", "clears their throat before every sentence",
            "laughs at their own jokes", "counts coins that are not there", "chews on a straw or a pipe stem", "bows far too deeply", "whispers even when there is nobody to overhear",
            "uses grand, long words slightly wrong", "peers over the party's shoulders at the door", "fidgets with a ring", "sniffs loudly", "repeats the last words of a sentence",
            "grins at the wrong moments", "shrugs at everything", "always seems to be eating", "keeps glancing at the sky", "tugs on an ear when lying",
            "calls everyone 'friend' or 'dear'", "sits only with their back to the wall", "speaks in short, clipped sentences", "scratches at old scars", "laughs behind a hand"};

    public static final String[] VOICES = {
            "a low, gravelly rumble", "high and reedy", "warm and slow, like honey", "fast and breathless", "a soft whisper you have to lean in for",
            "booming, even in a quiet room", "a sing-song lilt", "flat and bored", "rough from years of shouting", "clipped and precise",
            "a thick, hard-to-place accent", "gentle and a little hoarse", "nasal and complaining", "sweet, almost musical", "dry and unhurried",
            "cracked with age", "cheerful and loud", "slurred from drink", "polished, like an actor's", "tense and strained"};

    public static final String[] WANTS = {
            "to pay off a debt before the deadline", "to see their child leave the village and do better", "revenge on a rival who ruined them", "to find a lost sibling",
            "to be taken seriously by their neighbours", "a safe way out of town", "to finish a long-promised masterpiece", "to buy back a family heirloom",
            "to keep the peace at any cost", "to prove an old rumour true", "a patron who will pay them properly", "to hide a past mistake",
            "to win the heart of someone far above their station", "to avoid a visit from their landlord", "to be left alone", "to see justice for a wrong never answered",
            "to get rich quickly, by any means", "to protect someone they should not be protecting", "to deliver a message nobody wants", "a quiet place to retire",
            "to see an old enemy fall", "to learn a skill they were once refused", "to make amends to someone they wronged", "to be remembered after they die",
            "to rid the area of a menace they dare not name", "to stay out of the war", "to gather enough coin for a journey", "to get their hands on a rare item",
            "to uncover who has been watching them", "to get a job for a cousin who needs one"};

    public static final String[] SECRETS = {
            "owes a dangerous person far more money than they admit", "once deserted from the army", "is quietly working for someone else", "forged a document that is still in use",
            "is not who they claim to be", "witnessed a crime and said nothing", "is slowly going broke and hiding it", "is sick and has told no one",
            "has a hidden stash of coin in the cellar", "was once a thief, and still has the old tools", "stole something from the wrong person", "is in love with someone forbidden",
            "has a child nobody knows about", "poisoned an enemy years ago", "is secretly afraid of the dark", "worships a god the town would not approve of",
            "has been selling information to both sides", "is being blackmailed", "found something in the ruins and kept it", "cannot actually read or write",
            "knows where a missing person is buried", "has been skimming from their employer", "is a retired adventurer and hates being recognised", "has a famous relative they never mention",
            "carries a cursed trinket they cannot get rid of", "has been seeing strange visions", "once broke an oath to a powerful family", "is terrified of someone who has just arrived",
            "is hiding a fugitive in the attic", "made a deal with something they should not have"};

    /** What an NPC with this attitude does when the party approaches, with how much they will share. */
    public static final String[][] OPENINGS = {
            /* FRIENDLY */ {
            "Greets the party warmly and will happily share what they know.",
            "Delighted to have company; talks freely, and a little too much.",
            "Welcomes the party and offers help or a drink before they have asked.",
            "Eager to please; will share almost anything for a bit of kindness."},
            /* NEUTRAL */ {
            "Goes about their business; helpful if it costs them nothing, and for a coin if it does.",
            "Polite but busy; answers what is asked and no more.",
            "Neither warm nor cold; treats the party like any other customer.",
            "Willing to talk if the party is quick and to the point."},
            /* WARY */ {
            "Keeps their distance and answers only what is asked; trust has to be earned.",
            "Watches the party closely and checks who else is listening before speaking.",
            "Polite on the surface, but gives little and asks a lot of questions back.",
            "Will talk only if the party proves they are not a threat, or pays well."},
            /* HOSTILE */ {
            "Wants the party gone and gives nothing without pressure, payment or a threat.",
            "Openly rude; will only talk if frightened, flattered or paid.",
            "Blocks the way and demands to know what the party is up to.",
            "Hates outsiders, and will lie, stall or call for help before sharing the truth."}};
}
