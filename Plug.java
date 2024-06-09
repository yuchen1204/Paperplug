package com.example.hunterprey;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HunterPreyPlugin extends JavaPlugin implements CommandExecutor {
    private Economy econ;
    private Map<UUID, Long> preyPlayers = new HashMap<>();
    private Map<UUID, Long> hunterPlayers = new HashMap<>();
    private Scoreboard scoreboard;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault plugin not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.getCommand("identity_prey").setExecutor(this);
        this.getCommand("identity_hunter").setExecutor(this);
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::hunterMonthlyFee, 0L, 72000L); // 1 hour interval
        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::checkStatusExpiry, 0L, 1200L); // 1 minute interval
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("hunterprey.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("identity_prey")) {
            if (args.length != 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /identity_prey <player> <delay> <duration> <bounty>");
                return true;
            }
            handlePreyCommand(sender, args);
        } else if (cmd.getName().equalsIgnoreCase("identity_hunter")) {
            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /identity_hunter <player> <duration>");
                return true;
            }
            handleHunterCommand(sender, args);
        }
        return true;
    }

    private void handlePreyCommand(CommandSender sender, String[] args) {
        Player player = Bukkit.getPlayer(args[0]);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }

        long delay = parseTime(args[1]);
        long duration = parseTime(args[2]);
        double bounty = Double.parseDouble(args[3].replace("c", ""));

        if (delay < 0 || duration < 18000 || duration > 604800) { // 5 hours to 7 days in seconds
            sender.sendMessage(ChatColor.RED + "Invalid delay or duration.");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                preyPlayers.put(player.getUniqueId(), System.currentTimeMillis() + duration * 1000);
                setPlayerTeam(player, "Prey", ChatColor.GREEN);
                sender.sendMessage(ChatColor.GREEN + player.getName() + " is now a prey with a bounty of " + bounty + " coins for " + duration + " seconds.");
            }
        }.runTaskLater(this, delay * 20); // Convert to ticks
    }

    private void handleHunterCommand(CommandSender sender, String[] args) {
        Player player = Bukkit.getPlayer(args[0]);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }

        long duration = parseTime(args[1]);

        if (duration < 2592000) { // 1 month in seconds
            sender.sendMessage(ChatColor.RED + "Invalid duration.");
            return;
        }

        hunterPlayers.put(player.getUniqueId(), System.currentTimeMillis() + duration * 1000);
        setPlayerTeam(player, "Hunter", ChatColor.RED);
        sender.sendMessage(ChatColor.GREEN + player.getName() + " is now a hunter for " + duration + " seconds.");
    }

    private long parseTime(String time) {
        if (time.endsWith("m")) {
            return Integer.parseInt(time.replace("m", "")) * 60;
        } else if (time.endsWith("h")) {
            return Integer.parseInt(time.replace("h", "")) * 3600;
        } else if (time.endsWith("d")) {
            return Integer.parseInt(time.replace("d", "")) * 86400;
        } else if (time.endsWith("month")) {
            return Integer.parseInt(time.replace("month", "")) * 2592000;
        }
        return -1;
    }

    private void hunterMonthlyFee() {
        for (UUID hunter : hunterPlayers.keySet()) {
            if (System.currentTimeMillis() > hunterPlayers.get(hunter)) {
                removePlayerFromTeam(Bukkit.getPlayer(hunter), "Hunter");
                hunterPlayers.remove(hunter);
                continue;
            }
            if (!econ.has(Bukkit.getOfflinePlayer(hunter), 50)) {
                Bukkit.getPlayer(hunter).sendMessage(ChatColor.RED + "You don't have enough money to pay the monthly fee. Your hunter status has been revoked.");
                removePlayerFromTeam(Bukkit.getPlayer(hunter), "Hunter");
                hunterPlayers.remove(hunter);
            } else {
                econ.withdrawPlayer(Bukkit.getOfflinePlayer(hunter), 50);
                Bukkit.getPlayer(hunter).sendMessage(ChatColor.GREEN + "50 coins have been deducted for your monthly hunter fee.");
            }
        }
    }

    private void checkStatusExpiry() {
        long currentTime = System.currentTimeMillis();
        preyPlayers.entrySet().removeIf(entry -> {
            if (currentTime > entry.getValue()) {
                removePlayerFromTeam(Bukkit.getPlayer(entry.getKey()), "Prey");
                return true;
            }
            return false;
        });
        hunterPlayers.entrySet().removeIf(entry -> {
            if (currentTime > entry.getValue()) {
                removePlayerFromTeam(Bukkit.getPlayer(entry.getKey()), "Hunter");
                return true;
            }
            return false;
        });
    }

    private void setPlayerTeam(Player player, String teamName, ChatColor color) {
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.setColor(color);
        }
        team.addEntry(player.getName());
    }

    private void removePlayerFromTeam(Player player, String teamName) {
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.removeEntry(player.getName());
        }
    }
}
