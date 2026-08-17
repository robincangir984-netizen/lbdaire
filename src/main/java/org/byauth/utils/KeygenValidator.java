package org.byauth.utils;

import org.byauth.ByCircleGame;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

public class KeygenValidator {

    private final JavaPlugin plugin;
    private final String ACCOUNT_ID = "d0ec9aa8-d84f-4574-be8d-323a36ec647c";
    private final String PRODUCT_TOKEN = "prod-87cad9311ceeb7ba07e0e6c4306ac7d4ba261952557ce35140651b7bf7bde93cv3";

    public KeygenValidator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void validate() {
        String licenseKey = plugin.getConfig().getString("license.key", "");
        if (licenseKey.isEmpty()) {
            licenseKey = plugin.getConfig().getString("license-key", "").trim();
        } else {
            licenseKey = licenseKey.trim();
        }

        if (licenseKey.isEmpty()) {
            shutdown("config.yml içinde 'license.key' veya 'license-key' boş bırakılmış!");
            return;
        }

        String finalLicenseKey = licenseKey;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String hwid = generateHWID();
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();

                // 1. ADIM: Lisansı ve HWID'yi kontrol et
                HttpResponse<String> response = validateKey(client, finalLicenseKey, hwid);

                if (response.statusCode() == 200) {
                    if (response.body().contains("\"valid\":true")) {
                        plugin.getLogger().info("[LBDaire] Lisans ve HWID başarıyla doğrulandı!");
                        onSuccess();
                        return;
                    }

                    // 2. ADIM: Makine henüz kayıtlı değilse (NO_MACHINE / NO_MACHINES hatası) otomatik kaydet
                    if (response.body().contains("NO_MACHINE")) {
                        plugin.getLogger().info("[LBDaire] İlk kullanım tespit edildi. Makine Keygen'e otomatik kaydediliyor...");

                        String licenseId = extractLicenseId(response.body());

                        if (licenseId != null && registerMachine(client, licenseId, hwid)) {
                            plugin.getLogger().info("[LBDaire] Makine kaydı başarılı! Eklenti aktifleştirildi.");
                            onSuccess();
                            return;
                        } else {
                            shutdown("Makine kaydı başarısız!");
                            return;
                        }
                    }
                }

                plugin.getLogger().severe("[LBDaire] HTTP Kodu: " + response.statusCode());
                plugin.getLogger().severe("[LBDaire] Yanıt: " + response.body());
                shutdown("Geçersiz lisans, süresi dolmuş veya başka bir makinede kullanılıyor!");

            } catch (Exception e) {
                plugin.getLogger().severe("[LBDaire] Bağlantı Hatası: " + e.getMessage());
                shutdown("Lisans sunucusuna bağlanılamadı!");
            }
        });
    }

    private HttpResponse<String> validateKey(HttpClient client, String licenseKey, String hwid) throws Exception {
        String endpoint = String.format("https://api.keygen.sh/v1/accounts/%s/licenses/actions/validate-key", ACCOUNT_ID);
        String jsonPayload = String.format("{\"meta\":{\"key\":\"%s\",\"scope\":{\"fingerprint\":\"%s\"}}}", licenseKey, hwid);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/vnd.api+json")
                .header("Accept", "application/vnd.api+json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(5))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private boolean registerMachine(HttpClient client, String licenseId, String hwid) {
        try {
            String endpoint = String.format("https://api.keygen.sh/v1/accounts/%s/machines", ACCOUNT_ID);
            String jsonPayload = String.format(
                    "{\"data\":{\"type\":\"machines\",\"attributes\":{\"fingerprint\":\"%s\"},\"relationships\":{\"license\":{\"data\":{\"type\":\"licenses\",\"id\":\"%s\"}}}}}",
                    hwid, licenseId
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + PRODUCT_TOKEN)
                    .header("Content-Type", "application/vnd.api+json")
                    .header("Accept", "application/vnd.api+json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                return true;
            } else {
                plugin.getLogger().severe("[LBDaire] Makine Kayıt Hatası (HTTP " + response.statusCode() + "): " + response.body());
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[LBDaire] Otomatik makine kaydı istisna hatası: " + e.getMessage());
            return false;
        }
    }

    private String extractLicenseId(String jsonResponse) {
        try {
            int idIndex = jsonResponse.indexOf("\"id\":\"");
            if (idIndex != -1) {
                int start = idIndex + 6;
                int end = jsonResponse.indexOf("\"", start);
                return jsonResponse.substring(start, end);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String generateHWID() {
        try {
            String raw = System.getenv("COMPUTERNAME") + 
                         System.getProperty("user.name") + 
                         System.getProperty("os.name") + 
                         Runtime.getRuntime().availableProcessors();
                         
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 32);
        } catch (Exception e) {
            return "default-server-hwid";
        }
    }

    private void onSuccess() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin instanceof ByCircleGame) {
                ((ByCircleGame) plugin).initializePlugin();
            }
        });
    }

    private void shutdown(String reason) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getLogger().severe("========================================");
            plugin.getLogger().severe("[LBDaire] LİSANS HATASI: " + reason);
            plugin.getLogger().severe("[LBDaire] Eklenti devre dışı bırakılıyor...");
            plugin.getLogger().severe("========================================");
            
            Bukkit.getPluginManager().disablePlugin(plugin);
        });
    }
}