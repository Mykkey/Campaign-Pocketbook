package dnd;

import dnd.export.MarkdownExport;
import dnd.model.Attack;
import dnd.model.Card;
import dnd.model.Combatant.Side;
import dnd.model.Encounter;
import dnd.model.Feature;
import dnd.model.MagicItem;
import dnd.model.Quest;
import dnd.model.Session;
import dnd.model.Spell;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownExportTest {
    @Test
    void aStatBlockHasEverythingInOrder() {
        Card c = new Card();
        c.name = "Goblin Boss";
        c.size = "Small";
        c.creatureType = "humanoid (goblinoid)";
        c.alignment = "neutral evil";
        c.ac = 17;
        c.acNote = "chain shirt, shield";
        c.hpMax = 21;
        c.hitDice = "6d6";
        c.speed = "30 ft.";
        c.str = 10;
        c.dex = 14;
        c.skills = "Stealth +6";
        c.challenge = "1";
        c.xp = 200;
        c.traits.add(new Feature("Nimble Escape", "The goblin can Disengage or Hide as a bonus action."));
        c.attacks.add(new Attack("Scimitar", 4, "1d6+2", "slashing", "5 ft.", ""));
        c.reactions.add(new Feature("Redirect Attack", "Swaps places with a goblin."));
        c.notes = "Leads the ambush.";
        String md = MarkdownExport.card(c);
        assertTrue(md.startsWith("# Goblin Boss\n*Small humanoid (goblinoid), neutral evil*"), md);
        assertTrue(md.contains("**Armor Class** 17 (chain shirt, shield)"));
        assertTrue(md.contains("**Hit Points** 21 (6d6)"));
        assertTrue(md.contains("| STR | DEX | CON | INT | WIS | CHA |"));
        assertTrue(md.contains("| 10 (+0) | 14 (+2) | 10 (+0) |"), md);
        assertTrue(md.contains("**Skills** Stealth +6"));
        assertTrue(md.contains("**Challenge** 1 (200 XP)"));
        assertTrue(md.contains("## Traits\n\n***Nimble Escape.*** The goblin can Disengage"));
        assertTrue(md.contains("## Actions\n\n***Scimitar.***"));
        assertTrue(md.indexOf("## Traits") < md.indexOf("## Actions") && md.indexOf("## Actions") < md.indexOf("## Reactions"));
        assertTrue(md.contains("## Notes\n\nLeads the ambush."));
        assertFalse(md.contains("## Legendary"), "empty sections are left out");
        assertFalse(md.contains("**Senses**"), "empty fields are left out");
        assertTrue(md.endsWith("\n") && !md.endsWith("\n\n"));
    }

    @Test
    void negativeModifiersAndSpellsAndItemsRead() {
        Card weak = new Card();
        weak.str = 6;
        assertTrue(MarkdownExport.card(weak).contains("6 (-2)"));

        Spell s = new Spell();
        s.name = "Fireball";
        s.level = 3;
        s.school = "Evocation";
        s.castingTime = "1 action";
        s.range = "150 feet";
        s.components = "V, S, M";
        s.material = "a tiny ball of bat guano and sulfur.";
        s.duration = "Instantaneous";
        s.description = "A bright streak flashes.";
        s.higherLevel = "Damage grows by 1d6.";
        s.classes.add("Sorcerer");
        String sm = MarkdownExport.spell(s);
        assertTrue(sm.startsWith("# Fireball\n*3rd-level evocation (Sorcerer)*"), sm);
        assertTrue(sm.contains("**Components** V, S, M (a tiny ball of bat guano and sulfur)"));
        assertTrue(sm.contains("***At higher levels.*** Damage grows by 1d6."));

        s.concentration = true;
        s.duration = "1 minute";
        assertTrue(MarkdownExport.spell(s).contains("**Duration** Concentration, 1 minute"));

        MagicItem m = new MagicItem();
        m.name = "Cloak of Elvenkind";
        m.rarity = "Uncommon";
        m.attunement = true;
        m.description = "Advantage on Stealth.";
        m.charges = 3;
        m.recharge = "Dawn";
        m.curse = "Whispers at night.";
        m.value = "500";
        String mm = MarkdownExport.magicItem(m);
        assertTrue(mm.contains("# Cloak of Elvenkind\n*Wondrous item, uncommon (requires attunement)*"), mm);
        assertTrue(mm.contains("**Charges** 3 (recharges dawn)"));
        assertTrue(mm.contains("**Value** 500 gp"));
    }

    @Test
    void questsSessionsAndEncountersKeepTheirStructure() {
        Quest q = new Quest();
        q.name = "The Missing Caravan";
        q.status = Quest.Status.ACTIVE;
        q.giver = "Mira";
        q.giverId = "card-1";
        q.reward = "200 gp";
        q.steps.add(new Quest.Step("Find the tracks", true));
        q.steps.add(new Quest.Step("Reach the pass", false));
        q.related.add("card-2");
        q.related.add("gone");
        Map<String, String> names = Map.of("card-1", "Mira the Innkeeper", "card-2", "Ravenwood");
        String qm = MarkdownExport.quest(q, names::get);
        assertTrue(qm.contains("*Active*"));
        assertTrue(qm.contains("**Given by** Mira the Innkeeper"), qm);
        assertTrue(qm.contains("- [x] Find the tracks\n- [ ] Reach the pass"));
        assertTrue(qm.contains("## Related\n\n- Ravenwood"));
        assertFalse(qm.contains("gone"));

        Session se = new Session();
        se.number = 4;
        se.name = "The Lighthouse";
        se.realDate = "2026-11-10";
        se.inWorldDate = "3rd of Harvestmoon";
        se.campaignDay = 12;
        se.recap = "The party left the town.";
        se.summary = "They fought the smugglers.";
        se.nextSteps = "Follow the map.";
        String sm = MarkdownExport.session(se, id -> null);
        assertTrue(sm.startsWith("# Session 4: The Lighthouse\n*Played 2026-11-10; in the world: 3rd of Harvestmoon; campaign day 12*"), sm);
        assertTrue(sm.indexOf("## Last time on...") < sm.indexOf("## What happened") && sm.indexOf("## What happened") < sm.indexOf("## Next steps"));
        assertFalse(sm.contains("## Related"));

        Encounter e = new Encounter();
        e.name = "Bridge ambush";
        e.entries.add(new Encounter.Entry("a", 3, Side.ENEMY, true));
        e.entries.add(new Encounter.Entry("b", 1, Side.ALLY, false));
        e.entries.add(new Encounter.Entry("c", 2, Side.ENEMY, false));
        e.notes = "At dusk.";
        e.loot = "Coins: 40 gp\nA silver ring";
        String em = MarkdownExport.encounter(e, en -> en.cardId.equals("a") ? "Goblin" : en.cardId.equals("b") ? "Sildar" : null);
        assertTrue(em.contains("- 3 x Goblin (enemy, acting as a group)"), em);
        assertTrue(em.contains("- 1 x Sildar (ally)"));
        assertTrue(em.contains("- 2 x (missing creature) (enemy)"));
        assertTrue(em.contains("## Treasure\n\nCoins: 40 gp  \nA silver ring"), em);
    }

    @Test
    void fileNamesAreSafe() {
        assertEquals("Goblin Boss.md", MarkdownExport.fileName("Goblin Boss", ".md"));
        assertEquals("What- is this-.md", MarkdownExport.fileName("What? is this:", ".md"));
        assertEquals("Untitled.md", MarkdownExport.fileName("  ", ".md"));
        assertEquals("A-B-C.md", MarkdownExport.fileName("A/B\\C", ".md"));
        assertTrue(MarkdownExport.fileName("x".repeat(300), ".md").length() <= 83);
    }
}
