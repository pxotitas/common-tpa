package ctp.px;

import com.mojang.brigadier.arguments.StringArgumentType;
import ctp.px.listeners.CombatListener;
import ctp.px.listeners.PlayerListener;
import ctp.px.managers.CombatManager;
import ctp.px.managers.TpaManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class CtpPlugin extends JavaPlugin {
    private TpaManager tpaManager;
    private CombatManager combatManager;

    @Override
    public void onEnable() {
        tpaManager = new TpaManager(this);
        combatManager = new CombatManager(this, tpaManager);
        tpaManager.setCombatManager(combatManager);
        combatManager.start();
        getServer().getPluginManager().registerEvents(new CombatListener(combatManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(tpaManager), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("tpa")
                    .then(playerArgument(TpaManager.RequestType.TPA))
                    .then(Commands.literal("here").then(playerArgument(TpaManager.RequestType.TPA_HERE)))
                    .then(Commands.literal("cancel").executes(context -> runForPlayer(context.getSource(),
                            player -> tpaManager.cancel(player))))
                    .build());
            event.registrar().register(Commands.literal("tpahere")
                    .then(playerArgument(TpaManager.RequestType.TPA_HERE))
                    .then(Commands.literal("cancel").executes(context -> runForPlayer(context.getSource(),
                            player -> tpaManager.cancel(player))))
                    .build());
        });
    }

    @Override
    public void onDisable() {
        if (combatManager != null) {
            combatManager.stop();
        }
        if (tpaManager != null) {
            tpaManager.clear();
        }
    }

    private int runForPlayer(CommandSourceStack source, PlayerCommand action) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(TpaManager.message("&cOnly players can use this command."));
            return 0;
        }
        action.run(player);
        return 1;
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> playerArgument(
            TpaManager.RequestType requestType) {
        return Commands.argument("player", StringArgumentType.word())
                .suggests((context, builder) -> {
                    String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                    getServer().getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(remaining))
                            .forEach(builder::suggest);
                    return CompletableFuture.completedFuture(builder.build());
                })
                .executes(context -> runForPlayer(context.getSource(),
                        player -> tpaManager.request(player, StringArgumentType.getString(context, "player"),
                                requestType)))
                .then(Commands.literal("accept")
                        .executes(context -> runForPlayer(context.getSource(),
                                player -> tpaManager.accept(player, StringArgumentType.getString(context, "player"),
                                        requestType))))
                .then(Commands.literal("deny")
                        .executes(context -> runForPlayer(context.getSource(), player -> tpaManager.deny(player,
                                StringArgumentType.getString(context, "player"), requestType))));
    }

    @FunctionalInterface
    private interface PlayerCommand {
        void run(Player player);
    }
}
