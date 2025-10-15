package fr.Like.feedHappyGhast;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public final class FeedHappyGhast extends JavaPlugin implements Listener {

    private Material feedingItem;
    private int effectDuration;
    private double speedBoost;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }
    private void loadConfig() {
        this.reloadConfig();
        this.feedingItem = Material.valueOf(this.getConfig().getString("item").toUpperCase());
        this.effectDuration = this.getConfig().getInt("duration_in_seconds");
        this.speedBoost = this.getConfig().getDouble("speed_boost");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("fhgreload")) {
            if (sender.hasPermission("feedhappyghast.reload")) {
                this.loadConfig();
                sender.sendMessage("§aLa configuration du plugin FeedHappyGhast a été rechargée !");
                return true;
            } else {
                sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande.");
                return false;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.valueOf("HAPPY_GHAST")) {
            Player player = event.getPlayer();
            ItemStack item = player.getInventory().getItemInMainHand();

            if (item.getType() == this.feedingItem) {
                event.setCancelled(true);
                item.setAmount(item.getAmount() - 1);

                LivingEntity happyGhast = (LivingEntity) event.getRightClicked();
                UUID ghastUUID = happyGhast.getUniqueId();

                // Exécute la commande pour augmenter la vitesse de l'entité
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "attribute " + ghastUUID + " minecraft:flying_speed modifier add 1-1-1-1-1 " + this.speedBoost + " add_value");
                Bukkit.getConsoleSender().sendMessage("Le Happy ghast " + ghastUUID + " à été acceleré par " + player);
                player.sendMessage("Vous avez nourri le Happy Ghast ! Sa vitesse a augmenté.");

                // Planifie la réinitialisation de la vitesse après la durée définie
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (happyGhast.isValid()) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "attribute " + ghastUUID + " minecraft:flying_speed modifier remove 1-1-1-1-1");
                            player.sendMessage("L'effet de vitesse du Happy Ghast est terminé.");
                        }
                    }
                }.runTaskLater(this, this.effectDuration * 20L);
            }
        }
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(Component.text("§a[FeedHappyGhast] Plugin actif !"));
    }
}
