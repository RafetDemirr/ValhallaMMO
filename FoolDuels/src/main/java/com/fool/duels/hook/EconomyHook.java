package com.fool.duels.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

/** Thin wrapper around Vault. All methods are safe to call when Vault is missing. */
public final class EconomyHook {
    private Economy economy;

    public void setup(Logger log) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            log.warning("Vault bulunamadı: bahis ve turnuva ücretleri kapalı.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            log.warning("Vault var ama ekonomi eklentisi yok: bahis ve turnuva ücretleri kapalı.");
            return;
        }
        economy = rsp.getProvider();
        log.info("Ekonomi bağlandı: " + economy.getName());
    }

    public boolean enabled() {
        return economy != null;
    }

    public boolean has(OfflinePlayer p, double amount) {
        return amount <= 0 || (economy != null && economy.has(p, amount));
    }

    public boolean withdraw(OfflinePlayer p, double amount) {
        if (amount <= 0) return true;
        return economy != null && economy.withdrawPlayer(p, amount).transactionSuccess();
    }

    public void deposit(OfflinePlayer p, double amount) {
        if (amount <= 0 || economy == null) return;
        economy.depositPlayer(p, amount);
    }

    public String format(double amount) {
        if (economy == null) return String.format("%.0f", amount);
        return economy.format(amount);
    }
}
