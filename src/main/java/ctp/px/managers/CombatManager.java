package ctp.px.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks players who are in combat. Chat messages are sent exactly once when
 * combat starts and once when it ends; the countdown lives on the action bar.
 */
public final class CombatManager {
    private static final int COMBAT_DURATION_SECONDS = 15;

    private final JavaPlugin plugin;
    private final TpaManager tpaManager;
    private final Map<UUID, Long> combatExpiry = new HashMap<>();
    private BukkitTask task;

    public CombatManager(JavaPlugin plugin, TpaManager tpaManager) {
        this.plugin = plugin;
        this.tpaManager = tpaManager;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        combatExpiry.clear();
    }

    public boolean isInCombat(Player player) {
        Long expiry = combatExpiry.get(player.getUniqueId());
        return expiry != null && expiry > System.currentTimeMillis();
    }

    public int getRemainingSeconds(Player player) {
        Long expiry = combatExpiry.get(player.getUniqueId());
        if (expiry == null) {
            return 0;
        }
        long remainingMillis = expiry - System.currentTimeMillis();
        return remainingMillis <= 0 ? 0 : (int) Math.ceil(remainingMillis / 1000.0);
    }

    /** Called when two different players hurt each other. */
    public void tag(Player attacker, Player victim) {
        if (attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        tag(attacker);
        tag(victim);
    }

    private void tag(Player player) {
        long newExpiry = System.currentTimeMillis() + COMBAT_DURATION_SECONDS * 1000L;
        Long previous = combatExpiry.put(player.getUniqueId(), newExpiry);
        boolean alreadyInCombat = previous != null && previous > System.currentTimeMillis();
        if (alreadyInCombat) {
            return; // timer refreshed silently, no chat spam
        }
        player.sendMessage(TpaManager.message("&c&lCOMBAT &cYou are now in combat! Do not log out for "
                + COMBAT_DURATION_SECONDS + " seconds or you will be killed."));
        tpaManager.cancelWarmup(player, TpaManager.CancelReason.COMBAT);
    }

    /** Removes the combat tag without any message (death, shutdown, etc). */
    public void clear(Player player) {
        combatExpiry.remove(player.getUniqueId());
    }

    /** Called on quit. Kills the player if they logged out while in combat. */
    public void handleQuit(Player player) {
        boolean inCombat = isInCombat(player);
        combatExpiry.remove(player.getUniqueId());
        if (!inCombat) {
            return;
        }
        Bukkit.broadcast(TpaManager.message("&c&lCOMBAT &e" + player.getName()
                + " &clogged out during combat and was killed."));
        player.setHealth(0.0);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = combatExpiry.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (entry.getValue() <= now) {
                iterator.remove();
                player.sendMessage(TpaManager.message("&a&lCOMBAT &aYou are no longer in combat. You can log out safely."));
                player.sendActionBar(TpaManager.message("&aYou are no longer in combat."));
                continue;
            }
            int remaining = (int) Math.ceil((entry.getValue() - now) / 1000.0);
            player.sendActionBar(TpaManager.message("&cIn combat: &e" + remaining + "s"));
        }
    }
}
