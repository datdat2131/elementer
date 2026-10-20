package com.astrax.elementer;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /elementeradmin boss list | spawn <id> | kill <id|all> | setspawn | clearspawn | spawnnow [id] | next */
final class BossAdmin {
    private static final List<String> SUBS = List.of("list", "spawn", "kill", "setspawn", "clearspawn", "spawnnow", "next");

    private final ElementConfig cfg;
    private final BossConfig bossCfg;
    private final BossService bosses;
    private final BossSpawner spawner;

    BossAdmin(ElementConfig cfg, BossConfig bossCfg, BossService bosses, BossSpawner spawner) {
        this.cfg = cfg;
        this.bossCfg = bossCfg;
        this.bosses = bosses;
        this.spawner = spawner;
    }

    private String ids() {
        return String.join(", ", bossCfg.bosses().keySet());
    }

    void handle(CommandSender sender, String[] args, String label) {
        Messages m = cfg.messages();
        String sub = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                m.send(sender, "admin.boss-header", "ids", ids());
                for (String line : bosses.describe()) sender.sendMessage(Text.c("&7- &f" + line));
            }
            case "spawn" -> {
                if (!(sender instanceof Player p)) {
                    m.send(sender, "player-only");
                    return;
                }
                BossDef def = args.length < 3 ? null : bossCfg.boss(args[2]);
                if (def == null) {
                    m.send(sender, "admin.boss-unknown", "ids", ids());
                    return;
                }
                if (bosses.spawn(def, p.getLocation(), -1) == null) m.send(sender, "admin.boss-limit");
            }
            case "kill" -> m.send(sender, "admin.boss-killed", "count", bosses.kill(args.length < 3 ? "all" : args[2]));
            case "setspawn" -> {
                if (!(sender instanceof Player p)) {
                    m.send(sender, "player-only");
                    return;
                }
                spawner.setLocation(p.getLocation());
                Location l = p.getLocation();
                m.send(sender, "admin.spawner-set", "world", l.getWorld().getName(), "x", l.getBlockX(), "y", l.getBlockY(),
                        "z", l.getBlockZ(), "minutes", spawner.intervalMs() / 60_000L);
            }
            case "clearspawn" -> {
                spawner.clearLocation();
                m.send(sender, "admin.spawner-cleared");
            }
            case "spawnnow" -> {
                if (!spawner.hasLocation() || spawner.location() == null) {
                    m.send(sender, "admin.spawner-none");
                    return;
                }
                BossDef forced = args.length < 3 ? null : bossCfg.boss(args[2]);
                if (args.length >= 3 && forced == null) {
                    m.send(sender, "admin.boss-unknown", "ids", ids());
                    return;
                }
                if (spawner.spawnNow(forced) == null) m.send(sender, "admin.boss-limit");
            }
            case "next" -> {
                Location l = spawner.location();
                if (l == null) {
                    m.send(sender, "admin.spawner-none");
                    return;
                }
                m.send(sender, "admin.spawner-next", "minutes", (long) Math.ceil(spawner.remainingMs() / 60_000.0),
                        "world", l.getWorld().getName(), "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ(),
                        "state", spawner.enabled() ? "bật" : "tắt");
            }
            default -> m.send(sender, "admin.usage", "usage", "/" + label + " boss <" + String.join("|", SUBS) + ">");
        }
    }

    List<String> complete(String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 2) out.addAll(SUBS);
        else if (args.length == 3 && (args[1].equalsIgnoreCase("spawn") || args[1].equalsIgnoreCase("spawnnow")
                || args[1].equalsIgnoreCase("kill"))) {
            out.addAll(bossCfg.bosses().keySet());
            if (args[1].equalsIgnoreCase("kill")) out.add("all");
        }
        return out;
    }
}
