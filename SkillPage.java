package com.astrax.elementer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Trang kỹ năng: xem thông số, nâng cấp, chọn kỹ năng cho phím F. */
final class SkillPage {
    private final ElementConfig cfg;
    private final SkillService skills;
    private final GuiItems ui;

    SkillPage(ElementConfig cfg, SkillService skills, GuiItems ui) {
        this.cfg = cfg;
        this.skills = skills;
        this.ui = ui;
    }

    /** @return false nếu người chơi chưa có nguyên tố (không mở gì) */
    boolean open(Player p, PlayerData d) {
        if (d == null || d.getElement() == null) return false;
        ElementDef def = cfg.element(d.getElement());
        if (def == null) return false;
        Inventory inv = ui.create(GuiHolder.Page.SKILLS, 27, "gui.title-skills");
        inv.setItem(4, ui.item(Material.EXPERIENCE_BOTTLE,
                Messages.format(ui.raw("gui.points-button"), "points", d.getSkillPoints()), null, false));
        List<SkillDef> list = cfg.skillsOf(d.getElement());
        int[] slots = GuiHolder.skillSlots(list.size());
        for (SkillDef s : list) {
            int idx = Math.min(slots.length - 1, s.slot() - 1);
            inv.setItem(slots[idx], skillItem(d, def, s));
        }
        inv.setItem(22, ui.item(Material.ARROW, ui.raw("gui.back"), null, false));
        p.openInventory(inv);
        return true;
    }

    private boolean showsRange(SkillDef s) {
        return s.range() > 0 && s.type() != SkillType.AOE_SELF && s.type() != SkillType.SHIELD
                && s.type() != SkillType.HEAL_AREA && s.type() != SkillType.DASH;
    }

    private ItemStack skillItem(PlayerData d, ElementDef def, SkillDef s) {
        int sl = skills.levelOf(d, s);
        boolean locked = d.getLevel() < s.unlockLevel();
        List<String> lore = new ArrayList<>(s.description());
        lore.add(" ");
        if (s.ultimate()) lore.add(ui.raw("gui.lore.skill-ultimate"));
        if (s.damage() > 0) {
            double dmg = DamageCalculator.skillDamage(s.damage(), sl, s.perLevelBonus(), d.getLevel(), def.damagePerLevel(),
                    1.0, 1.0, cfg.main().getDouble("combat.max-skill-damage", 40.0));
            lore.add(Messages.format(ui.raw("gui.lore.skill-damage"), "value", Text.num(dmg)));
        }
        if (s.power() > 0) {
            lore.add(Messages.format(ui.raw("gui.lore.skill-power"), "value",
                    Text.num(s.power() * (1.0 + s.perLevelBonus() * (sl - 1)))));
        }
        lore.add(Messages.format(ui.raw("gui.lore.skill-cooldown"), "value", Text.num(skills.cooldownMs(s, sl) / 1000.0)));
        if (showsRange(s)) lore.add(Messages.format(ui.raw("gui.lore.skill-range"), "value", Text.num(s.range())));
        if (s.radius() > 0) lore.add(Messages.format(ui.raw("gui.lore.skill-radius"), "value", Text.num(s.radius())));
        lore.add(Messages.format(ui.raw("gui.lore.skill-level"), "level", sl, "max", s.maxLevel()));
        if (locked) {
            lore.add(Messages.format(ui.raw("gui.lore.skill-unlock"), "unlock", s.unlockLevel()));
        } else {
            if (sl >= s.maxLevel()) lore.add(ui.raw("gui.lore.skill-maxed"));
            else lore.add(Messages.format(ui.raw("gui.lore.skill-upgrade"), "cost", skills.upgradeCost(d, s)));
            lore.add(" ");
            lore.add(ui.raw("gui.lore.hint-upgrade"));
            lore.add(ui.raw("gui.lore.hint-select"));
        }
        boolean selected = d.getSelectedSlot() == s.slot();
        if (selected) lore.add(ui.raw("gui.lore.selected"));
        return ui.item(locked ? Material.GRAY_DYE : s.icon(), def.color() + s.name(), lore, selected);
    }
}
