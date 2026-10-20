package com.astrax.elementer;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /elementeradmin reload | give <player> <exp|points> <n> | set <player> <element|level|exp|points> <v> | reset <player> */
public final class ElementerAdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("reload", "give", "set", "reset", "boss", "updateconfig");
    private static final long MAX_AMOUNT = 1_000_000L;

    private final ElementerCore plugin;
    private final ElementConfig cfg;
    private final ElementService elements;
    private final VoucherService vouchers;
    private final CrystalService crystals;
    private final BossService bosses;
    private final BossConfig bossCfg;

    public ElementerAdminCommand(ElementerCore plugin, ElementConfig cfg, ElementService elements, VoucherService vouchers,
                                 CrystalService crystals, BossService bosses, BossConfig bossCfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.elements = elements;
        this.vouchers = vouchers;
        this.crystals = crystals;
        this.bosses = bosses;
        this.bossCfg = bossCfg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages m = cfg.messages();
        if (args.length == 0) {
            m.send(sender, "admin.usage", "usage", "/" + label + " <reload|give|set|reset>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!perm(sender, "elementer.admin.reload")) return true;
                plugin.reloadAll();
                m.send(sender, "admin.reloaded");
            }
            case "give" -> {
                if (!perm(sender, "elementer.admin.give")) return true;
                if (args.length < 4) {
                    m.send(sender, "admin.usage", "usage", "/" + label + " give <người chơi> <exp|points|voucher|crystal> <số|nguyên tố> [số lượng]");
                    return true;
                }
                Player t = target(sender, args[1]);
                if (t != null && (args[2].equalsIgnoreCase("voucher") || args[2].equalsIgnoreCase("crystal"))) {
                    giveItem(sender, t, args);
                    return true;
                }
                Long n = number(sender, args[3]);
                if (t == null || n == null) return true;
                if (n <= 0 || n > MAX_AMOUNT) {
                    m.send(sender, "admin.invalid-number");
                    return true;
                }
                if (elements.get(t) == null) {
                    m.send(sender, "admin.no-data");
                    return true;
                }
                switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "exp" -> {
                        long got = elements.addExp(t, n, true);
                        m.send(sender, "admin.given", "amount", got, "what", "EXP", "player", t.getName());
                    }
                    case "points" -> {
                        PlayerData d = elements.get(t);
                        d.addSkillPoints((int) Math.min(n, 10_000));
                        m.send(sender, "admin.given", "amount", Math.min(n, 10_000), "what", "điểm kỹ năng", "player", t.getName());
                    }
                    default -> m.send(sender, "admin.usage", "usage", "/" + label + " give <người chơi> <exp|points> <số>");
                }
            }
            case "set" -> {
                if (!perm(sender, "elementer.admin.set")) return true;
                if (args.length < 4) {
                    m.send(sender, "admin.usage", "usage", "/" + label + " set <người chơi> <element|level|exp|points> <giá trị>");
                    return true;
                }
                Player t = target(sender, args[1]);
                if (t == null) return true;
                PlayerData d = elements.get(t);
                if (d == null) {
                    m.send(sender, "admin.no-data");
                    return true;
                }
                String what = args[2].toLowerCase(Locale.ROOT);
                if (what.equals("element")) {
                    Element e = Element.parse(args[3]);
                    if (e == null || cfg.element(e) == null) {
                        m.send(sender, "awaken.invalid");
                        return true;
                    }
                    elements.setElement(t, e);
                    m.send(sender, "admin.set-done", "what", "nguyên tố", "player", t.getName(), "value", e.name());
                    return true;
                }
                Long n = number(sender, args[3]);
                if (n == null) return true;
                if (n < 0 || n > MAX_AMOUNT) {
                    m.send(sender, "admin.invalid-number");
                    return true;
                }
                switch (what) {
                    case "level" -> elements.setLevel(t, (int) Math.min(n, Integer.MAX_VALUE));
                    case "exp" -> elements.setExp(t, n);
                    case "points" -> d.setSkillPoints((int) Math.min(n, 10_000));
                    default -> {
                        m.send(sender, "admin.usage", "usage", "/" + label + " set <người chơi> <element|level|exp|points> <giá trị>");
                        return true;
                    }
                }
                m.send(sender, "admin.set-done", "what", what, "player", t.getName(), "value", n);
            }
            case "updateconfig" -> {
                if (!perm(sender, "elementer.admin.updateconfig")) return true;
                String dir = ConfigUpdates.backupAndReplace(plugin);
                if (dir == null) {
                    m.send(sender, "admin.update-failed");
                } else {
                    plugin.reloadAll();
                    cfg.messages().send(sender, "admin.updated", "dir", dir);
                }
            }
            case "boss" -> {
                if (!perm(sender, "elementer.admin.boss")) return true;
                bossCommand(sender, args, label);
            }
            case "reset" -> {
                if (!perm(sender, "elementer.admin.reset")) return true;
                if (args.length < 2) {
                    m.send(sender, "admin.usage", "usage", "/" + label + " reset <người chơi>");
                    return true;
                }
                Player t = target(sender, args[1]);
                if (t == null) return true;
                if (elements.get(t) == null) {
                    m.send(sender, "admin.no-data");
                    return true;
                }
                elements.reset(t);
                m.send(sender, "admin.reset-done", "player", t.getName());
            }
            default -> m.send(sender, "admin.usage", "usage", "/" + label + " <reload|give|set|reset>");
        }
        return true;
    }

    /**
     * /elementeradmin give <người chơi> voucher|crystal <nguyên tố> [số lượng] - dùng được từ console (plugin nạp thẻ).
     * Vé: 1-10; tinh thể: 1-64.
     */
    private void giveItem(CommandSender sender, Player t, String[] args) {
        Messages m = cfg.messages();
        boolean voucher = args[2].equalsIgnoreCase("voucher");
        Element e = args.length < 4 ? null : Element.parse(args[3]);
        if (e == null || cfg.element(e) == null) {
            m.send(sender, "awaken.invalid");
            return;
        }
        int amount = 1;
        if (args.length >= 5) {
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException ex) {
                m.send(sender, "admin.invalid-number");
                return;
            }
        }
        if (amount < 1 || amount > (voucher ? 10 : 64)) {
            m.send(sender, "admin.invalid-number");
            return;
        }
        ElementDef def = cfg.element(e);
        String colored = def == null ? e.name() : def.colored();
        if (voucher) {
            int given = vouchers.give(t, e, amount);
            m.send(sender, "admin.given-voucher", "amount", given, "element", colored, "player", t.getName());
        } else {
            crystals.give(t, e, amount);
            m.send(sender, "admin.given-crystal", "amount", amount, "element", colored, "player", t.getName());
        }
    }

    /** /elementeradmin boss list | spawn <id> | kill <id|all> */
    private void bossCommand(CommandSender sender, String[] args, String label) {
        Messages m = cfg.messages();
        String sub = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                m.send(sender, "admin.boss-header", "ids", String.join(", ", bossCfg.bosses().keySet()));
                for (String line : bosses.describe()) sender.sendMessage(Text.c("&7- &f" + line));
            }
            case "spawn" -> {
                if (!(sender instanceof Player p)) {
                    m.send(sender, "player-only");
                    return;
                }
                BossDef def = args.length < 3 ? null : bossCfg.boss(args[2]);
                if (def == null) {
                    m.send(sender, "admin.boss-unknown", "ids", String.join(", ", bossCfg.bosses().keySet()));
                    return;
                }
                if (bosses.spawn(def, p.getLocation(), -1) == null) m.send(sender, "admin.boss-limit");
            }
            case "kill" -> m.send(sender, "admin.boss-killed", "count", bosses.kill(args.length < 3 ? "all" : args[2]));
            default -> m.send(sender, "admin.usage", "usage", "/" + label + " boss <list|spawn <id>|kill <id|all>>");
        }
    }

    private boolean perm(CommandSender s, String node) {
        if (s.hasPermission(node)) return true;
        cfg.messages().send(s, "no-permission");
        return false;
    }

    private Player target(CommandSender s, String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) cfg.messages().send(s, "admin.player-not-found", "player", name);
        return p;
    }

    private Long number(CommandSender s, String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            cfg.messages().send(s, "admin.invalid-number");
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(last)) out.add(s);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("boss")
                && !args[0].equalsIgnoreCase("updateconfig")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            out.addAll(List.of("exp", "points", "voucher", "crystal"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            out.addAll(List.of("element", "level", "exp", "points"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")
                && (args[2].equalsIgnoreCase("voucher") || args[2].equalsIgnoreCase("crystal"))) {
            for (Element e : Element.values()) out.add(e.alias());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("boss")) {
            out.addAll(List.of("list", "spawn", "kill"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("boss") && args[1].equalsIgnoreCase("spawn")) {
            out.addAll(bossCfg.bosses().keySet());
        } else if (args.length == 4 && args[0].equalsIgnoreCase("set") && args[2].equalsIgnoreCase("element")) {
            for (Element e : Element.values()) out.add(e.alias());
        }
        return out;
    }
}
