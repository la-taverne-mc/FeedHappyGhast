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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.concurrent.ConcurrentHashMap; // Utilisation de ConcurrentHashMap pour les Map modifiées par des tâches asynchrones/différents threads

public final class FeedHappyGhast extends JavaPlugin implements Listener {

    private Material feedingItem;
    private int effectDuration;
    private double speedBoost;

    // --- MODIFICATIONS DES MAPS ---
    // Clé: UUID du Ghast. Valeur: Temps de fin de l'effet.
    private final Map<UUID, Long> ghastEndTimes = new ConcurrentHashMap<>();
    // Clé: UUID du joueur. Valeur: Tâche d'affichage du timer du joueur.
    private final Map<UUID, BukkitTask> playerTimerTasks = new ConcurrentHashMap<>();
    // Clé: UUID du Ghast. Valeur: UUID du joueur qui l'a nourri (pour suivre l'effet/cooldown persistant).
    private final Map<UUID, UUID> activeEffects = new ConcurrentHashMap<>();
    // --- FIN MODIFICATIONS DES MAPS ---


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
            LivingEntity happyGhast = (LivingEntity) event.getRightClicked();
            UUID ghastUUID = happyGhast.getUniqueId();

            if (item.getType() != this.feedingItem) {
                return; // Permet l'assise si le joueur n'a pas la nourriture en main
            }

            // VÉRIFICATION DU COOLDOWN (Actif si l'effet est actif)
            if (activeEffects.containsKey(ghastUUID)) {
                player.sendMessage("§cCe Happy Ghast est déjà sous l'effet de vitesse et ne peut pas être nourri de nouveau pour le moment.");
                event.setCancelled(true);
                return;
            }

            // Si le joueur est ici, il a l'objet et le Ghast n'est pas en cooldown.
            event.setCancelled(true);

            item.setAmount(item.getAmount() - 1);

            // Démarre l'effet et le Cooldown
            activeEffects.put(ghastUUID, player.getUniqueId());

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "attribute " + ghastUUID + " minecraft:flying_speed modifier add 1-1-1-1-1 " + this.speedBoost + " add_value");

            player.sendMessage("§aVous avez nourri le Happy Ghast ! Sa vitesse a augmenté.");

            long endTime = System.currentTimeMillis() + (this.effectDuration * 1000L);

            // Stocke l'heure de fin par Ghast (c'est la clé de la correction)
            ghastEndTimes.put(ghastUUID, endTime);

            // Le timer du joueur est démarré (ou redémarré) pour vérifier quel Ghast il monte.
            startEffectTimer(player);

            // Le Runnable s'exécutera à la fin de la durée de l'effet/cooldown
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (happyGhast.isValid()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "attribute " + ghastUUID + " minecraft:flying_speed modifier remove 1-1-1-1-1");
                        player.sendMessage("L'effet de vitesse du Happy Ghast est terminé.");
                    }

                    // Le Ghast sort de la map activeEffects et ghastEndTimes
                    activeEffects.remove(ghastUUID);
                    ghastEndTimes.remove(ghastUUID);
                }
            }.runTaskLater(this, this.effectDuration * 20L);
        }
    }

    // --- GESTION DÉCONNEXION/RECONNEXION ---

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // Annule la tâche d'affichage en cours pour ce joueur, mais conserve les données de l'effet.
        BukkitTask task = playerTimerTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Vérifie si le joueur a au moins un Ghast sous effet actif lié à lui (persistance).
        boolean isOwnerOfActiveGhast = activeEffects.containsValue(playerId);

        if (isOwnerOfActiveGhast) {
            // Démarre la tâche du timer. Elle vérifiera sur quel Ghast il est monté.
            startEffectTimer(player);
        }
    }

    // --- LOGIQUE D'AFFICHAGE GLOBALE PAR JOUEUR ---

    /**
     * Démarre (ou redémarre) une tâche qui vérifie si le joueur est assis sur un Ghast ayant un effet actif.
     */
    private void startEffectTimer(Player player) {
        UUID playerId = player.getUniqueId();

        // Annule l'ancienne tâche si elle existe (si le joueur a nourri un autre Ghast)
        BukkitTask existingTask = playerTimerTasks.get(playerId);
        if (existingTask != null) existingTask.cancel();

        // La tâche est démarrée. Elle tournera jusqu'à ce que le joueur se déconnecte
        // ou qu'il n'ait plus de Ghast actif pour lequel il est responsable (via cleanUpTask).

        BukkitTask timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !playerTimerTasks.containsKey(playerId)) {
                    // Si le joueur se déconnecte, la tâche est annulée par onPlayerQuit.
                    this.cancel();
                    return;
                }

                // 1. Détermine sur quelle entité le joueur est assis.
                Entity vehicle = player.getVehicle();

                // 2. Vérifie si c'est un HAPPY_GHAST avec un effet actif.
                if (vehicle != null && vehicle.getType() == EntityType.valueOf("HAPPY_GHAST")) {
                    UUID ghastUUID = vehicle.getUniqueId();

                    // Vérifie si CE Ghast a une heure de fin d'effet enregistrée.
                    if (ghastEndTimes.containsKey(ghastUUID)) {
                        long currentTime = System.currentTimeMillis();
                        long endTime = ghastEndTimes.get(ghastUUID);

                        if (currentTime >= endTime) {
                            // L'effet est fini. Le nettoyage complet sera fait par le Runnable du Ghast.
                            player.sendActionBar(Component.text(""));
                            return;
                        }

                        // 3. Affiche le timer.
                        long timeLeftSeconds = (endTime - currentTime) / 1000;
                        long minutes = timeLeftSeconds / 60;
                        long seconds = timeLeftSeconds % 60;
                        String timeFormatted = String.format("%01d:%02d", minutes, seconds);

                        String message = String.format("§6⚡ §fVitesse Ghast: §e%s", timeFormatted);
                        player.sendActionBar(Component.text(message));
                        return; // Le timer est affiché, on sort de la vérification.
                    }
                }

                // 4. Si le joueur n'est pas assis sur un Ghast AVEC un effet actif, efface l'Action Bar.
                player.sendActionBar(Component.text(""));
            }
        }.runTaskTimer(this, 0L, 5L);

        playerTimerTasks.put(playerId, timerTask);
    }

    // Méthode de nettoyage simplifiée (ne concerne plus que la tâche du joueur)
    private void cleanUpTask(UUID playerId) {
        // La suppression des données d'effet (ghastEndTimes, activeEffects) est gérée par le runTaskLater.

        BukkitTask task = playerTimerTasks.remove(playerId);
        if (task != null) {
            task.cancel();
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendActionBar(Component.text(""));
            }
        }
    }
}