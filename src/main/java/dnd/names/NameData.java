package dnd.names;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Raw name lists for each playable/common race. All lists are comma separated for compactness. */
public final class NameData {
    private NameData() { }

    /** Splits a comma separated list, trimming and dropping duplicates (case-insensitive order kept). */
    static List<String> list(String csv) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return Collections.unmodifiableList(new ArrayList<>(out));
    }

    static List<String> union(List<String> a, List<String> b) {
        LinkedHashSet<String> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return Collections.unmodifiableList(new ArrayList<>(out));
    }

    // ---- shared pools (used by several races)
    private static final List<String> HUMAN_M = list("Aldric, Bram, Cedric, Dorian, Edmund, Falk, Gareth, Hadrian, Ivor, Jasper, Kendrick, Leofric, Marcus, Nolan, Osric, Percival, Quentin, Roland, Stefan, Tobias, Ulric, Victor, Warren, Alaric, Berengar, Conrad, Darius, Everett, Garrick, Lucan");
    private static final List<String> HUMAN_F = list("Adela, Brianna, Cordelia, Daphne, Elowen, Freya, Gwendolyn, Helena, Isolde, Janelle, Katarina, Lyra, Marian, Nerys, Ophelia, Petra, Rosalind, Seraphina, Theodora, Una, Vivienne, Wynne, Alessa, Briar, Clarisse, Delphine, Eleanor, Ingrid, Odette, Sybil");
    private static final List<String> HUMAN_L = list("Ashford, Blackwood, Carver, Dunmere, Evenwood, Fairweather, Greymantle, Hollis, Ironside, Kettleby, Longbow, Marsh, Northcott, Oakheart, Pennington, Quill, Ravenscroft, Stormwind, Thatcher, Underhill, Vane, Whitlock, Yarrow, Aldermoor, Brightwater, Cobbler, Dawnstrider, Fletcher, Harrowgate, Winterbourne");
    private static final List<String> ELF_M = list("Aelar, Aramil, Berrian, Carric, Erevan, Galinndan, Hadarai, Immeral, Laucian, Mindartis, Paelias, Quarion, Riardon, Soveliss, Thamior, Varis, Adran, Aust, Enialis, Faelar, Ivellios, Lamlis, Rolen, Theren, Elthion");
    private static final List<String> ELF_F = list("Adrie, Birel, Caelynn, Enna, Felosial, Ilanis, Keyleth, Leshanna, Mialee, Naivara, Quelenna, Sariel, Shava, Thia, Valanthe, Xanaphia, Althaea, Drusilia, Ielenia, Jelenneth, Lia, Nyssa, Silaqui, Yrlissa, Amara");
    private static final List<String> ELF_L = list("Amakiir, Galanodel, Holimion, Ilphelkiir, Liadon, Meliamne, Naïlo, Siannodel, Xiloscient, Moonwhisper, Silverfrond, Starflower, Dawnbringer, Evenstar, Nightbreeze, Oakenshade, Brightleaf, Silvershade, Greenmantle, Moonbow, Sunwhisper, Windrunner, Aerendyl, Luthien, Faerondel");
    private static final List<String> ORC_M = list("Dench, Feng, Gell, Henk, Holg, Imsh, Keth, Krusk, Mhurren, Ront, Shump, Thokk, Brug, Gorak, Hruk, Urzul, Varg, Zug, Bolg, Drog, Grumsh, Narzug, Ulgar, Skarn, Rekk");
    private static final List<String> ORC_F = list("Baggi, Emen, Engong, Kansif, Myev, Neega, Ovak, Ownka, Shautha, Sutha, Vola, Volen, Yevelda, Grisha, Hazka, Murgul, Ushra, Zurga, Baghra, Lagzi, Nurka, Rozha, Sagra, Thurka, Yagra");
    private static final List<String> ORC_L = list("Bonebreaker, Skullcrusher, Bloodtusk, Ironhide, Blackfang, Redhand, Grimgaze, Stonefist, Warbringer, Doomhammer, Ashmaw, Gorefang, Hardhide, Ragefist, Steeljaw, Thundermaw, Wolfbane, Axebiter, Dreadfist, Gutripper, Ironskull, Mudtooth, Nightmaw, Ravager, Shadowhowl");

    public enum Race {
        HUMAN("Human", HUMAN_M, HUMAN_F, HUMAN_L),
        ELF("Elf", ELF_M, ELF_F, ELF_L),
        DWARF("Dwarf",
                list("Adrik, Alberich, Baern, Barendd, Brottor, Bruenor, Dain, Darrak, Delg, Eberk, Einkil, Fargrim, Flint, Gardain, Harbek, Kildrak, Morgran, Orsik, Oskar, Rangrim, Rurik, Taklinn, Thoradin, Thorin, Tordek, Traubon, Travok, Ulfgar, Veit, Vondal"),
                list("Amber, Artin, Audhild, Bardryn, Dagnal, Diesa, Eldeth, Falkrunn, Finellen, Gunnloda, Gurdis, Helja, Hlin, Kathra, Kristryd, Ilde, Liftrasa, Mardred, Riswynn, Sannl, Torbera, Torgga, Vistra, Brynja, Hildur"),
                list("Balderk, Battlehammer, Brawnanvil, Dankil, Fireforge, Frostbeard, Gorunn, Holderhek, Ironfist, Loderr, Lutgehr, Rumnaheim, Strakeln, Torunn, Ungart, Deepdelver, Stonehelm, Goldbeard, Axebreaker, Copperkettle, Granitebrow, Mountainheart, Oreseeker, Runecarver, Steelbraid")),
        HALFLING("Halfling",
                list("Alton, Ander, Cade, Corrin, Eldon, Errich, Finnan, Garret, Lindal, Lyle, Merric, Milo, Osborn, Perrin, Reed, Roscoe, Wellby, Pip, Bilbert, Tobin, Hamfast, Dodo, Fredegar, Sam, Wilcome"),
                list("Andry, Bree, Callie, Cora, Euphemia, Jillian, Kithri, Lavinia, Lidda, Merla, Nedda, Paela, Portia, Seraphina, Shaena, Trym, Vani, Verna, Rosie, Marigold, Primula, Poppy, Daisy, Amaryllis, Tansy"),
                list("Brushgather, Goodbarrel, Greenbottle, High-hill, Hilltopple, Leagallow, Tealeaf, Thorngage, Tosscobble, Underbough, Applebaum, Bramblefoot, Burrows, Cobblepot, Dandelion, Fernwhistle, Hollowbrook, Merrybrook, Puddlefoot, Quickstep, Rumblebelly, Smallburrow, Tunnelmeade, Warmhearth, Willowbend")),
        GNOME("Gnome",
                list("Alston, Alvyn, Boddynock, Brocc, Burgell, Dimble, Eldon, Erky, Fonkin, Frug, Gerbo, Gimble, Glim, Jebeddo, Kellen, Namfoodle, Orryn, Roondar, Seebo, Sindri, Warryn, Wrenn, Zook, Fizzlebang, Nackle"),
                list("Bimpnottin, Breena, Caramip, Carlin, Donella, Duvamil, Ella, Ellyjobell, Ellywick, Lilli, Loopmottin, Lorilla, Mardnab, Nissa, Nyx, Oda, Orla, Roywyn, Shamil, Tana, Waywocket, Zanna, Pib, Dabbledob, Twink"),
                list("Beren, Daergel, Folkor, Garrick, Nackle, Murnig, Ningel, Raulnor, Scheppen, Timbers, Turen, Cogsworth, Fizzlewick, Gearspindle, Sparklegem, Tinkertop, Whirligig, Copperwhistle, Boltbrace, Ratchetsprocket, Springloaded, Clankwhistle, Dinglepot, Gadgetson, Sprocketwhirr")),
        HALF_ELF("Half-Elf", union(HUMAN_M, ELF_M), union(HUMAN_F, ELF_F), union(HUMAN_L, ELF_L)),
        HALF_ORC("Half-Orc", ORC_M, ORC_F, union(ORC_L, HUMAN_L)),
        TIEFLING("Tiefling",
                union(HUMAN_M, list("Akmenos, Amnon, Barakas, Damakos, Ekemon, Iados, Kairon, Leucis, Melech, Mordai, Morthos, Pelaios, Skamos, Therai")),
                union(HUMAN_F, list("Akta, Anakis, Bryseis, Criella, Damaia, Ea, Kallista, Lerissa, Makaria, Nemeia, Orianna, Phelaia, Rieta")),
                union(HUMAN_L, list("Ashenveil, Cinderborn, Dreadmoor, Hellspire, Nightfall, Sorrowmere, Duskborn, Emberclaw, Gloomwhisper, Hollowthorn, Nethershade, Pyrewalker, Ravenscar, Soulbrand, Umbermourn"))),
        DRAGONBORN("Dragonborn",
                list("Arjhan, Balasar, Bharash, Donaar, Ghesh, Heskan, Kriv, Medrash, Mehen, Nadarr, Pandjed, Patrin, Rhogar, Shamash, Shedinn, Tarhun, Torinn, Akra, Daar, Jheri"),
                list("Akra, Biri, Daar, Farideh, Harann, Havilar, Jheri, Kava, Korinn, Mishann, Nala, Perra, Raiann, Sora, Surina, Thava, Uadjit, Ilvara, Tazyra, Vesha"),
                list("Clethtinthiallor, Daardendrian, Delmirev, Drachedandion, Fenkenkabradon, Kepeshkmolik, Kerrhylon, Kimbatuul, Linxakasendalor, Myastan, Nemmonis, Norixius, Ophinshtalajiir, Prexijandilin, Shestendeliath, Turnuroth, Verthisathurgiesh, Yarjerit")),
        ORC("Orc", ORC_M, ORC_F, ORC_L),
        GOBLIN("Goblin",
                list("Snaga, Nikk, Gribble, Skrit, Wartnose, Griznak, Zik, Mogg, Pogg, Snikt, Gnash, Krik, Nib, Rattle, Skab, Sneek, Trig, Vex, Yik, Zog"),
                list("Snaga, Nikk, Gribble, Skrit, Wartnose, Griznak, Zik, Mogg, Pogg, Snikt, Gnash, Krik, Nib, Rattle, Skab, Sneek, Trig, Vex, Yik, Zog"),
                list("Sharptooth, Rustknife, Mudfoot, Ratchewer, Bogwallow, Stinkgrin, Cutpurse, Dirtnail, Gutsnatch, Slimeback, Snotfinger, Pitchfork, Rotbelly, Gloomcrawl, Crookback, Sootface, Backstabber, Candlestub, Fleabite, Greasepaw")),
        TABAXI("Tabaxi",
                list("Cloud, Jade, Ember, Whisper, Shadow, Ripple, Thunder, Flint, Dusk, Dawn, Moss, Frost, Sparrow, Ash, Reed, Storm, Willow, Sun, Raven, Quartz"),
                list("Cloud, Jade, Ember, Whisper, Shadow, Ripple, Thunder, Flint, Dusk, Dawn, Moss, Frost, Sparrow, Ash, Reed, Storm, Willow, Sun, Raven, Quartz"),
                list("on the Mountain, on Velvet Paws, of the Burning Sky, in the Tall Grass, at Moonrise, Chases the Wind, Who Walks Alone, of Seven Streams, Beneath the Roots, of the Silent Tree, on Quiet Feet, Dances in Rain, of Crimson Dawn, by the River, Under Falling Stars")),
        AASIMAR("Aasimar",
                union(HUMAN_M, list("Azriel, Cassiel, Gabriel, Raphael, Uriel, Zadkiel, Haniel, Raguel, Jophiel, Samael")),
                union(HUMAN_F, list("Aurelia, Celestine, Elara, Lumina, Seraphine, Solara, Aurora, Halcyon, Verity, Zephyrine")),
                list("Dawnbringer, Lightbearer, Sunforged, Brightwing, Goldenhalo, Radiantgaze, Starborn, Heavensong, Dayspring, Silverwing, Morningstar, Truehart, Valorheart, Sunwalker, Gracewind, Dawnmantle, Everlight, Skyward, Haloforge, Sunstrider")),
        GENASI("Genasi",
                union(HUMAN_M, list("Zephyr, Cinder, Tempest, Basalt, Brook, Gale, Ignis, Terran, Aquor, Pyrrhus")),
                union(HUMAN_F, list("Zephyra, Ember, Marina, Pyra, Terra, Brisa, Cascade, Ignatia, Coral, Opal")),
                list("Ashwind, Emberheart, Stormcaller, Tidecaller, Stonebreath, Flameborn, Galewhisper, Wavecrest, Earthshaker, Cloudstrider, Lavaflow, Mistwalker, Rockhide, Skysinger, Cindersoul, Deeptide, Boulderborn, Thunderlung, Magmaheart, Zephyrwing")),
        GOLIATH("Goliath",
                list("Aukan, Eglath, Gae-Al, Gauthak, Ilikan, Keothi, Kuori, Lo-Kag, Manneo, Maveith, Nalla, Orilo, Paavu, Pethani, Thalai, Thotham, Uthal, Vaunea, Vimak"),
                list("Gauthak, Ilikan, Keothi, Kuori, Lo-Kag, Manneo, Maveith, Nalla, Orilo, Paavu, Pethani, Thalai, Thotham, Uthal, Vaunea, Vimak, Aukan, Eglath, Gae-Al"),
                list("Anakalathai, Elanithino, Gathakanathi, Kalagiano, Katho-Olavi, Kolae-Gileana, Ogolakanu, Thuliaga, Thunukalathi, Vaimei-Laga")),
        KOBOLD("Kobold",
                list("Skree, Krik, Zix, Torg, Hiss, Nix, Dak, Rik, Vex, Scratch, Yip, Snik, Clik, Bix, Zek, Fizz, Tik, Skar, Pok, Snap"),
                list("Skree, Krik, Zix, Torg, Hiss, Nix, Dak, Rik, Vex, Scratch, Yip, Snik, Clik, Bix, Zek, Fizz, Tik, Skar, Pok, Snap"),
                list("Trapmaker, Dragonsworn, Scalefoot, Tunnelrat, Claw-Licker, Ashscale, Rubblehide, Spikeback, Candlewick, Pebbleclaw, Dustscale, Emberclaw, Shinyfinder, Tailtwitch, Wyrmlicker, Dentgrin, Rockgnaw, Sootsnout, Slyscale, Tinytooth")),
        TORTLE("Tortle",
                list("Ayla, Brine, Cove, Dune, Eddy, Fathom, Gull, Harbor, Isle, Kelp, Lagoon, Mari, Nori, Pebble, Quay, Reef, Shoal, Tide, Wade, Marlin"),
                list("Ayla, Brine, Cove, Dune, Eddy, Fathom, Gull, Harbor, Isle, Kelp, Lagoon, Mari, Nori, Pebble, Quay, Reef, Shoal, Tide, Wade, Marlin"),
                list("Slow-Current, Deepshell, Driftwood, Mossback, Sandwalker, Tidewatcher, Stillwater, Barnacle, Longjourney, Hardshell, Seaglass, Thoughtful-Wave, Coralstone, Quietshore, Saltwind, Patientstep, Wavebreaker, Greenback, Shellsong, Sunbasker"));

        public final String display;
        public final List<String> masculine;
        public final List<String> feminine;
        public final List<String> last;

        Race(String display, List<String> masculine, List<String> feminine, List<String> last) {
            this.display = display;
            this.masculine = masculine;
            this.feminine = feminine;
            this.last = last;
        }

        /** True when the masculine and feminine pools are the same (gender makes no difference). */
        public boolean unisex() {
            return masculine.equals(feminine);
        }

        @Override
        public String toString() {
            return display;
        }
    }

    public static List<Race> races() {
        return Arrays.asList(Race.values());
    }
}
