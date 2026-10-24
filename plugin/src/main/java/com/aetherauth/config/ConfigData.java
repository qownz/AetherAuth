package com.aetherauth.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

/**
 * Raw configuration model. Every field is initialised with a sensible default,
 * so a partially written config.yml never causes null values.
 */
public final class ConfigData {

    public int configVersion = 1;

    public Database database = new Database();
    public Security security = new Security();
    public Auth auth = new Auth();
    public Gui gui = new Gui();
    public Sounds sounds = new Sounds();
    @SerializedName("update-check")
    public Update update = new Update();

    public static final class Database {
        public String file = "aetherauth.db";
        public int poolSize = 4;
        public int busyTimeoutMs = 5000;
        public int cacheSize = 4096;
        public String synchronous = "NORMAL";
        public boolean backupOnStart = false;
        public int backupsToKeep = 7;
        public int auditRetention = 20000;
    }

    public static final class Security {
        public int iterations = 40000;
        public String cipher = "GCM";
        public int saltBytes = 32;
        public boolean rehashOnLogin = true;
        public boolean encryptEmail = true;
    }

    public static final class Auth {
        public boolean requireEmail = true;
        public int minPasswordLength = 6;
        public int maxPasswordLength = 64;
        public boolean requireDigit = false;
        public boolean requireUppercase = false;
        public boolean requireSpecial = false;
        public boolean blockCommonPasswords = true;

        public int maxAttempts = 5;
        public int lockoutSeconds = 60;
        public int timeoutSeconds = 120;
        public int maxAccountsPerIp = 3;
        public int registerCooldownSeconds = 8;
        public boolean kickOnMaxAttempts = true;
        public boolean registerEnabled = true;
        public int loginAttemptsPerIp = 20;
        public int loginWindowSeconds = 300;
        public int workerThreads = 2;

        public boolean sessionsEnabled = true;
        public int sessionDurationMinutes = 15;
        public boolean sessionBindIp = true;

        public boolean teleportToAuthSpawn = false;
        public boolean returnAfterLogin = true;
        public Spawn authSpawn = new Spawn();

        public boolean blindness = true;
        public boolean hidePlayers = true;
        public boolean freeze = true;
        public boolean blockChat = true;
        public boolean hideJoinMessage = true;

        public List<String> allowedCommands = new ArrayList<String>(Arrays.asList(
                "login", "l", "register", "reg", "kayit", "giris"
        ));

        public List<String> loginCommands = new ArrayList<String>();
        public List<String> registerCommands = new ArrayList<String>();
        public List<String> logoutCommands = new ArrayList<String>();
    }

    public static final class Spawn {
        public String world = "world";
        public double x = 0.5D;
        public double y = 64.0D;
        public double z = 0.5D;
        public float yaw = 0.0F;
        public float pitch = 0.0F;
    }

    public static final class Gui {
        /** auto, dialog or command */
        public String mode = "auto";
        public boolean autoOpen = true;
        public int openDelayTicks = 0;
        public boolean confirmPassword = true;
        public boolean showEmail = true;
        public int width = 320;
        public int buttonWidth = 150;
        /** Ask for confirmation before applying a password or e-mail change. */
        public boolean confirmChanges = true;
        public boolean canClose = false;
    }

    public static final class Sounds {
        public String login = "entity.player.levelup";
        public String register = "block.note_block.pling";
        public String error = "entity.villager.no";
        public String click = "ui.button.click";
        public String logout = "block.chest.close";
        public boolean enabled = true;
        public float volume = 0.7F;
        public float pitch = 1.0F;
    }

    public static final class Update {
        public boolean enabled = true;
        @SerializedName("version-url")
        public String versionUrl = "https://raw.githubusercontent.com/qownz/AetherAuth/main/version.txt";
        @SerializedName("download-url")
        public String downloadUrl = "https://github.com/qownz/AetherAuth/releases/latest";
    }

    public static Map<String, String> defaultMessages() {
        Map<String, String> m = new LinkedHashMap<String, String>();

        m.put("prefix", "<gradient:#6EE7FF:#A78BFA><bold>AETHER</bold></gradient> <dark_gray>»</dark_gray> ");
        m.put("divider", "<dark_gray><strikethrough>                                                  </strikethrough></dark_gray>");

        m.put("title.register", "<gradient:#6EE7FF:#A78BFA><bold>KAYIT</bold></gradient>");
        m.put("subtitle.register", "<gray>Hesabını oluşturmak için formu doldur</gray>");
        m.put("title.login", "<gradient:#6EE7FF:#A78BFA><bold>GİRİŞ</bold></gradient>");
        m.put("subtitle.login", "<gray>Devam etmek için şifreni gir</gray>");
        m.put("title.success", "<gradient:#7CF29B:#6EE7FF><bold>HOŞ GELDİN</bold></gradient>");
        m.put("subtitle.success", "<gray>İyi oyunlar, <white>{player}</white>!</gray>");
        m.put("title.timeout", "<#FF6B81><bold>ZAMAN AŞIMI</bold></#FF6B81>");
        m.put("subtitle.timeout", "<gray>{seconds} saniye içinde giriş yapmadın.</gray>");

        m.put("actionbar.waiting", "<gray>Kimlik doğrulaması bekleniyor <dark_gray>•</dark_gray> <#FFD166>/login</#FFD166> <gray>veya</gray> <#FFD166>/register</#FFD166>");
        m.put("actionbar.processing", "<#FFD166>İşleniyor...</#FFD166>");
        m.put("actionbar.locked", "<#FF6B81>Kilitli <dark_gray>•</dark_gray> <white>{seconds}s</white></#FF6B81>");

        m.put("bossbar.waiting", "<gradient:#6EE7FF:#A78BFA>Kimlik doğrulaması bekleniyor</gradient> <dark_gray>•</dark_gray> <white>{seconds}s</white>");

        m.put("join.checking", "{prefix}<gray>Hesabın kontrol ediliyor...</gray>");
        m.put("join.register-required", "{prefix}<white>Sunucuya hoş geldin <#6EE7FF><bold>{player}</bold></#6EE7FF><white>!</white> <gray>Devam etmek için kayıt olmalısın.</gray>");
        m.put("join.login-required", "{prefix}<white>Tekrar hoş geldin <#6EE7FF><bold>{player}</bold></#6EE7FF><white>!</white> <gray>Devam etmek için giriş yapmalısın.</gray>");
        m.put("join.usage.register", "{prefix}<gray>Kayıt olmak için: <#FFD166>/register <mail> <şifre></#FFD166></gray>");
        m.put("join.usage.login", "{prefix}<gray>Giriş yapmak için: <#FFD166>/login <şifre></#FFD166></gray>");
        m.put("join.autologin", "{prefix}<#7CF29B>Oturumun doğrulandı, tekrar hoş geldin!</#7CF29B>");

        m.put("chat.blocked", "{prefix}<#FF6B81>Sohbeti kullanmak için önce giriş yapmalısın.</#FF6B81>");
        m.put("command.blocked", "{prefix}<#FF6B81>Bu komutu kullanmak için önce giriş yapmalısın.</#FF6B81>");

        m.put("register.usage", "{prefix}<gray>Kullanım: <white>/register <mail> <şifre></white></gray>");
        m.put("register.success", "{prefix}<#7CF29B><bold>Kayıt başarılı!</bold></#7CF29B> <gray>Artık oynamaya başlayabilirsin.</gray>");
        m.put("register.email-taken", "{prefix}<#FF6B81>Bu e-posta adresi zaten kullanılıyor.</#FF6B81>");
        m.put("register.email-invalid", "{prefix}<#FF6B81>Geçerli bir e-posta adresi gir.</#FF6B81>");
        m.put("register.password-weak", "{prefix}<#FF6B81>Şifren yeterince güçlü değil: <white>{reason}</white></#FF6B81>");
        m.put("register.password-mismatch", "{prefix}<#FF6B81>Şifreler birbiriyle uyuşmuyor.</#FF6B81>");
        m.put("register.limit", "{prefix}<#FF6B81>Bu IP adresinden en fazla <white>{max}</white> hesap oluşturulabilir.</#FF6B81>");
        m.put("register.cooldown", "{prefix}<gray>Çok hızlı denedin. <white>{seconds}</white> saniye beklemelisin.</gray>");
        m.put("register.already", "{prefix}<#FFD166>Bu hesap zaten kayıtlı. <white>/login</white> ile giriş yapabilirsin.</#FFD166>");
        m.put("register.disabled", "{prefix}<#FF6B81>Kayıt şu anda kapalı.</#FF6B81>");
        m.put("register.cancelled", "{prefix}<#FF6B81>Kayıt başka bir eklenti tarafından iptal edildi.</#FF6B81>");

        m.put("login.usage", "{prefix}<gray>Kullanım: <white>/login <şifre></white></gray>");
        m.put("login.success", "{prefix}<#7CF29B><bold>Giriş başarılı!</bold></#7CF29B> <gray>İyi oyunlar.</gray>");
        m.put("login.failed", "{prefix}<#FF6B81>Şifre hatalı.</#FF6B81> <gray>Kalan deneme: <white>{remaining}</white></gray>");
        m.put("login.locked", "{prefix}<#FF6B81>Çok fazla hatalı deneme yaptın. <white>{seconds}</white> saniye sonra tekrar dene.</#FF6B81>");
        m.put("login.unknown", "{prefix}<gray>Bu isimle kayıtlı bir hesap yok. <white>/register</white> ile kayıt olabilirsin.</gray>");
        m.put("login.already", "{prefix}<#7CF29B>Zaten giriş yapmış durumdasın.</#7CF29B>");
        m.put("login.timeout", "<#FF6B81><bold>Zaman aşımı</bold></#FF6B81>\n<gray>{seconds} saniye içinde giriş yapmadığın için sunucudan çıkarıldın.</gray>");
        m.put("login.kicked", "<#FF6B81><bold>Çok fazla hatalı deneme</bold></#FF6B81>\n<gray>Lütfen daha sonra tekrar dene.</gray>");
        m.put("login.cancelled", "{prefix}<gray>Giriş iptal edildi. <white>/login</white> ile tekrar deneyebilirsin.</gray>");
        m.put("login.processing", "{prefix}<#FFD166>Giriş bilgileri doğrulanıyor...</#FFD166>");
        m.put("login.rate-limited", "{prefix}<#FF6B81>Çok fazla deneme yaptın. Lütfen biraz bekle.</#FF6B81>");
        m.put("login.rehash-failed", "{prefix}<#FF6B81>Hesap verisi okunamadı. Lütfen yöneticiye başvur.</#FF6B81>");

        m.put("logout.success", "{prefix}<gray>Çıkış yaptın. Devam etmek için tekrar giriş yapmalısın.</gray>");
        m.put("logout.not-authenticated", "{prefix}<#FF6B81>Giriş yapmadığın için çıkış yapamazsın.</#FF6B81>");
        m.put("logout.no-permission", "{prefix}<#FF6B81>Çıkış komutunu sadece yetkililer kullanabilir.</#FF6B81>");
        m.put("logout.target", "{prefix}<#7CF29B><white>{player}</white> adlı oyuncunun oturumu kapatıldı.</#7CF29B>");
        m.put("logout.target-not-found", "{prefix}<#FF6B81><white>{player}</white> adlı oyuncu çevrimiçi değil.</#FF6B81>");

        m.put("password.usage", "{prefix}<gray>Kullanım: <white>/changepassword <eski> <yeni></white></gray>");
        m.put("password.changed", "{prefix}<#7CF29B>Şifren başarıyla güncellendi.</#7CF29B>");
        m.put("password.old-wrong", "{prefix}<#FF6B81>Mevcut şifren hatalı.</#FF6B81>");
        m.put("password.same", "{prefix}<#FF6B81>Yeni şifren eskisiyle aynı olamaz.</#FF6B81>");
        m.put("password.not-authenticated", "{prefix}<#FF6B81>Önce giriş yapmalısın.</#FF6B81>");

        m.put("email.usage", "{prefix}<gray>Kullanım: <white>/changemail <şifre> <yeni mail></white></gray>");
        m.put("email.changed", "{prefix}<#7CF29B>E-posta adresin güncellendi.</#7CF29B>");
        m.put("email.taken", "{prefix}<#FF6B81>Bu e-posta adresi zaten kullanılıyor.</#FF6B81>");
        m.put("email.invalid", "{prefix}<#FF6B81>Geçerli bir e-posta adresi gir.</#FF6B81>");
        m.put("email.wrong-password", "{prefix}<#FF6B81>Şifren hatalı.</#FF6B81>");
        m.put("email.same", "{prefix}<#FF6B81>Bu zaten mevcut e-posta adresin.</#FF6B81>");

        m.put("common.no-permission", "{prefix}<#FF6B81>Bu işlem için yetkin yok.</#FF6B81>");
        m.put("common.player-only", "{prefix}<#FF6B81>Bu komut sadece oyuncular tarafından kullanılabilir.</#FF6B81>");
        m.put("common.not-authenticated", "{prefix}<#FF6B81>Önce giriş yapmalısın.</#FF6B81>");
        m.put("common.already-authenticated", "{prefix}<#7CF29B>Zaten giriş yapmış durumdasın.</#7CF29B>");
        m.put("common.internal-error", "{prefix}<#FF6B81>Bir hata oluştu. Lütfen yöneticiye başvur.</#FF6B81>");
        m.put("common.unknown-subcommand", "{prefix}<gray>Bilinmeyen komut. <white>/aetherauth</white> yazarak yardım alabilirsin.</gray>");

        m.put("admin.usage", "<gradient:#6EE7FF:#A78BFA><bold>AETHER AUTH</bold></gradient> <dark_gray>•</dark_gray> <gray>yardım</gray>\n"
                + "<dark_gray>▪</dark_gray> <white>/aetherauth reload</white> <dark_gray>-</dark_gray> <gray>ayarları yenile</gray>\n"
                + "<dark_gray>▪</dark_gray> <white>/aetherauth info <oyuncu></white> <dark_gray>-</dark_gray> <gray>hesap bilgisi</gray>\n"
                + "<dark_gray>▪</dark_gray> <white>/aetherauth unregister <oyuncu></white> <dark_gray>-</dark_gray> <gray>hesabı sil</gray>\n"
                + "<dark_gray>▪</dark_gray> <white>/aetherauth resetpassword <oyuncu> [yeni]</white> <dark_gray>-</dark_gray> <gray>şifre sıfırla</gray>\n"
                + "<dark_gray>▪</dark_gray> <white>/aetherauth resetmail <oyuncu> <yeni-mail></white> <dark_gray>-</dark_gray> <gray>e-posta sıfırla</gray>\n"
                + "<dark_gray>▪</dark_gray> <white>/aetherauth backup</white> <dark_gray>-</dark_gray> <gray>yedek al</gray>\n"
                + "<dark_gray>▪</dark_gray> <white>/aetherauth stats</white> <dark_gray>-</dark_gray> <gray>istatistikler</gray>");
        m.put("admin.reloaded", "{prefix}<#7CF29B>Yapılandırma yeniden yüklendi.</#7CF29B> <dark_gray>({time} ms)</dark_gray>");
        m.put("admin.not-found", "{prefix}<#FF6B81><white>{player}</white> için kayıtlı bir hesap bulunamadı.</#FF6B81>");
        m.put("admin.info", "<gradient:#6EE7FF:#A78BFA><bold>HESAP BİLGİSİ</bold></gradient>\n"
                + "<dark_gray>▪</dark_gray> <gray>Oyuncu:</gray> <white>{player}</white>\n"
                + "<dark_gray>▪</dark_gray> <gray>UUID:</gray> <white>{uuid}</white>\n"
                + "<dark_gray>▪</dark_gray> <gray>E-posta:</gray> <white>{email}</white>\n"
                + "<dark_gray>▪</dark_gray> <gray>Kayıt:</gray> <white>{created}</white>\n"
                + "<dark_gray>▪</dark_gray> <gray>Son giriş:</gray> <white>{last_login}</white>\n"
                + "<dark_gray>▪</dark_gray> <gray>Son IP:</gray> <white>{ip}</white>\n"
                + "<dark_gray>▪</dark_gray> <gray>Yan hesap:</gray> <white>{alt_accounts}</white>");
        m.put("admin.unregistered", "{prefix}<#7CF29B><white>{player}</white> hesabı silindi.</#7CF29B>");
        m.put("admin.unregistered-kick", "<#FF6B81><bold>Hesabın silindi</bold></#FF6B81>\n<gray>Yeniden kayıt olabilirsin.</gray>");
        m.put("admin.reset", "{prefix}<#7CF29B><white>{player}</white> için yeni şifre: <#FFD166><bold>{password}</bold></#FFD166></#7CF29B>");
        m.put("admin.reset-target", "{prefix}<#FFD166>Şifren bir yönetici tarafından sıfırlandı. Yeni şifreni öğrenmek için yöneticiyle iletişime geç.</#FFD166>");
        m.put("admin.resetmail", "{prefix}<#7CF29B><white>{player}</white> için yeni e-posta: <#FFD166>{email}</#FFD166></#7CF29B>");
        m.put("admin.resetmail-target", "{prefix}<#FFD166>E-posta adresin bir yönetici tarafından değiştirildi.</#FFD166>");
        m.put("admin.resetmail-invalid", "{prefix}<#FF6B81>Geçerli bir e-posta adresi gir.</#FF6B81>");
        m.put("admin.resetmail-same", "{prefix}<#FF6B81>Bu e-posta adresi zaten bu hesaba ait.</#FF6B81>");
        m.put("admin.resetmail-taken", "{prefix}<#FF6B81>Bu e-posta adresi başka bir hesapta kullanılıyor.</#FF6B81>");
        m.put("admin.backup", "{prefix}<#7CF29B>Yedek oluşturuldu: <white>{file}</white></#7CF29B>");
        m.put("admin.stats", "<gradient:#6EE7FF:#A78BFA><bold>İSTATİSTİK</bold></gradient>\n"
                + "<dark_gray>▪</dark_gray> <gray>Kayıtlı oyuncu:</gray> <white>{registered}</white>\n"
                + "<dark_gray>▪</dark_gray> <gray>Toplam yan hesap:</gray> <white>{alt_accounts}</white>\n"
                + "<dark_gray>▪</dark_gray> <gray>Anlık giriş:</gray> <white>{online}</white>\n"
                + "<dark_gray>▪</dark_gray> <gray>Bekleyen:</gray> <white>{pending}</white>\n"
                + "<dark_gray>▪</dark_gray> <gray>Veritabanı:</gray> <white>{size}</white>\n"
                + "<dark_gray>▪</dark_gray> <gray>Sunucu:</gray> <white>{version}</white>\n"
                + "<dark_gray>▪</dark_gray> <gray>Dialog:</gray> <white>{dialog}</white>");
        m.put("admin.invalid-usage", "{prefix}<gray>Kullanım: <white>{usage}</white></gray>");
        m.put("admin.backup-failed", "{prefix}<#FF6B81>Yedek alınamadı: <white>{reason}</white></#FF6B81>");
        m.put("admin.unknown-player", "{prefix}<#FF6B81>Oyuncu bulunamadı: <white>{player}</white></#FF6B81>");

        m.put("dialog.register.title", "<gradient:#6EE7FF:#A78BFA><bold>AETHER AUTH</bold></gradient>");
        m.put("dialog.register.body1", "<white>Merhaba <#6EE7FF><bold>{player}</bold></#6EE7FF>!</white> <gray>Lütfen kaydını tamamla.</gray>");
        m.put("dialog.register.body2", "<gray>Hesabın tamamen sana özel ve güvende.</gray>");
        m.put("dialog.login.title", "<gradient:#6EE7FF:#A78BFA><bold>AETHER AUTH</bold></gradient>");
        m.put("dialog.login.body1", "<white>Tekrar hoş geldin <#6EE7FF><bold>{player}</bold></#6EE7FF>!</white>");
        m.put("dialog.login.body2", "<gray>Devam etmek için şifreni gir.</gray>");
        m.put("dialog.email.label", "<white>Mail</white>");
        m.put("dialog.password.label", "<white>Şifre</white>");
        m.put("dialog.password.label-register", "<white>Şifre</white> <dark_gray>(en az {min} karakter)</dark_gray>");
        m.put("dialog.confirm.label", "<white>Şifre (tekrar)</white>");
        m.put("dialog.submit.register", "<#7CF29B><bold>Kayıt Ol</bold></#7CF29B>");
        m.put("dialog.submit.login", "<#7CF29B><bold>Giriş Yap</bold></#7CF29B>");
        m.put("change.cancelled", "{prefix}<gray>Değişiklik iptal edildi.</gray>");
        m.put("dialog.cancel", "<gray>Vazgeç</gray>");
        m.put("dialog.change.title", "<gradient:#6EE7FF:#A78BFA><bold>ONAY</bold></gradient>");
        m.put("dialog.change.email-body1", "<white>E-posta değişikliğini onayla</white>");
        m.put("dialog.change.email-body2", "<gray>Yeni adresi kontrol et, gerekirse düzenle.</gray>");
        m.put("dialog.change.password-body1", "<white>Şifre değişikliğini onayla</white>");
        m.put("dialog.change.password-body2", "<gray>Yeni şifreni kontrol et, gerekirse düzenle.</gray>");
        m.put("dialog.change.current-label", "<white>Mevcut şifre</white>");
        m.put("dialog.change.new-email", "<white>Yeni e-posta</white>");
        m.put("dialog.change.new-password", "<white>Yeni şifre</white>");
        m.put("dialog.change.submit", "<#7CF29B><bold>Değiştir</bold></#7CF29B>");
        m.put("dialog.change.cancel", "<gray>Vazgeç</gray>");

        m.put("update.available", "{prefix}<#FFD166><bold>Yeni AetherAuth sürümü mevcut!</bold></#FFD166> <gray>Mevcut: <white>{current}</white> <dark_gray>•</dark_gray> Yeni: <white>{latest}</white></gray>\n{prefix}<gray>İndirmek için: <#6EE7FF>{url}</#6EE7FF></gray>");

        return m;
    }
}
