# AetherAuth

Modern, hafif ve güvenli bir Minecraft kimlik doğrulama eklentisi.

<div align="center">
  <img src="logo.jpeg" alt="AetherAuth" width="100%">
</div>

AetherAuth; kayıt, giriş, oturum yönetimi, şifre ve e-posta değiştirme, yönetici komutları ile güncelleme kontrolü tek bir eklenti paketi altında toplar.

Paper **1.21.6+** sunucularda modern dialog arayüzü kullanır; daha eski Paper/Spigot sürümlerinde komut tabanlı fallback moduna geçer.

---

## Özellikler

- **Paper 1.21.6+ Dialog** arayüzü: görsel kayıt, giriş, şifre ve e-posta değişiklik ekranları.
- **Komut fallback modu:** Paper/Spigot **1.13 – 1.21.5** ve eski istemciler için `/login`, `/register` tabanlı akış.
- **Güvenli veri saklama:** şifreler PBKDF2-HMAC-SHA512 ile hashlenir; e-posta adresleri AES-256/GCM ile şifrelenir.
- **SQLite yerel veritabanı:** otomatik yedekleme, bağlantı havuzu ve asenkron I/O.
- **Kısa süreli kısıtlamalar:** IP başına hesap limiti, hatalı giriş denemesi limiti, hesap kilitleme, oturum süre/IP kısıtları.
- **Özelleştirilebilir mesajlar:** `messages.yml` içinde tüm kullanıcı mesajları; MiniMessage ve `&` renk kodları desteklenir.
- **Güncelleme kontrolü:** proje kökündeki `version.txt` ile GitHub üzerinden sürüm karşılaştırması.
- **Çok modüllü mimari:** `api`, `dialog`, `plugin` modülleri.

---

## Desteklenen sürümler

| Sunucu | Durum |
|---|---|
| Paper **1.21.6+** | Tam destek: dialog arayüzü |
| Purpur / Paper fork **1.21.6+** | Tam destek: Paper Dialog API korunuyorsa dialog arayüzü |
| Paper / Spigot **1.13 – 1.21.5** | Komut modu |
| Spigot **1.21.6+** | Komut modu |
| Folia | Desteklenmiyor |

**Java:** Dialog arayüzü için Java 21; komut modu için Java 8+.

**Oyuncu istemcisi:** Dialog arayüzü için Minecraft client sürümü **1.21.6+** olmalıdır. ViaVersion ile daha yeni client'lar bağlanabilir, ancak dialog görünümünü garanti etmez.

> Test edilenler: Paper **1.21.8** (dialog), Paper **1.16.5** (komut modu, Velocity + ViaVersion arkasında).

---

## Kurulum

1. `AetherAuth-1.0.0.jar` dosyasını sunucuya at.
2. `plugins/AetherAuth/` klasörüne koy.
3. İlk başlatma sonrası aşağıdaki klasör yapısı oluşur:

```text
plugins/AetherAuth/
├── config.yml
├── messages.yml
└── database/
    ├── aetherauth.db
    ├── secret.key
    └── backups/
```

4. Gerekirse `config.yml` ve `messages.yml` dosyalarını düzenle.
5. Sunucuyu yeniden başlat.
6. `/aetherauth reload` komutu ile ayarları yenile.

---

## Komutlar

### Kullanıcı komutları

| Komut | Açıklama |
|---|---|
| `/register <mail> <şifre>` | Yeni hesap oluşturur. |
| `/login <şifre>` | Giriş yapar. |
| `/logout` | Oturumu kapatır. |
| `/changepassword <eski> <yeni>` | Şifreyi değiştirir. |
| `/changemail <şifre> <yeni-mail>` | E-posta adresini değiştirir. |

### Yönetici komutları

| Komut | Açıklama |
|---|---|
| `/aetherauth help` | Yardım mesajını gösterir. |
| `/aetherauth reload` | Ayar dosyalarını yeniler. |
| `/aetherauth info <oyuncu>` | Hesap bilgilerini gösterir. |
| `/aetherauth unregister <oyuncu>` | Hesabı silir. |
| `/aetherauth resetpassword <oyuncu> [yeni]` | Şifreyi sıfırlar. |
| `/aetherauth resetmail <oyuncu> <yeni-mail>` | E-posta adresini sıfırlar. |
| `/aetherauth backup` | Veritabanı yedekler. |
| `/aetherauth stats` | İstatistikleri gösterir. |
| `/aetherauth logout` | Başka oyuncunun oturumunu kapatır. |

---

## İzinler

| İzin | Açıklama |
|---|---|
| `aetherauth.admin` | Tüm AetherAuth yönetim izinleri. |
| `aetherauth.admin.reload` | Ayar dosyasını yenileme. |
| `aetherauth.admin.info` | Hesap bilgisi görüntüleme. |
| `aetherauth.admin.unregister` | Hesap silme. |
| `aetherauth.admin.reset` | Şifre ve e-posta sıfırlama. |
| `aetherauth.admin.backup` | Veritabanı yedekleme. |
| `aetherauth.admin.stats` | İstatistik görüntüleme. |
| `aetherauth.admin.logout` | Oyuncu oturumunu kapatma. |

---

## Yapılandırma

`config.yml` içinde şu ana başlıklar bulunur:

- `database`: SQLite dosyası, bağlantı havuzu, yedek ayarları.
- `security`: şifre hashleme, AES/GCM, e-posta şifreleme.
- `auth`: kayıt, giriş, deneme, kilitleme, IP limiti, oturum.
- `gui`: dialog / komut modu seçimi.
- `update-check`: sürüm kontrolü.
- `sounds`: ses ayarları.

`messages.yml` içinde tüm kullanıcıya gösterilen mesajlar tutulur. `{prefix}`, `{player}`, `{email}`, `{seconds}`, `{min}`, `{max}`, `{reason}` gibi yer tutucular kullanılabilir.

---

## Güvenlik

- Şifreler düz metin saklanmaz; PBKDF2-HMAC-SHA512 ile hashlenir.
- E-posta adresleri varsayılan olarak AES-256/GCM ile şifrelenir.
- `database/` altındaki `secret.key` dosyası kritiktir; yedeklenmelidir.
- `online-mode=true` kullanılıyorsa eklenti kullanmadan önce doğru auth server yapılandırılmalıdır.
- Komut modunda şifreler komut satırına yazılabilir ve sunucu loglarına düşebilir; mümkünse dialog modu tercih edilmelidir.

---

## Güncelleme Kontrolü

Proje kökündeki `version.txt` mevcut sürümü içerir.

```yaml
update-check:
  enabled: true
  version-url: 'https://raw.githubusercontent.com/qownz/AetherAuth/main/version.txt'
  download-url: 'https://github.com/qownz/AetherAuth/releases/latest'
```

Sürüm bulunursa konsola uyarı yazılır ve ilk giren op oyuncuya bildirim gönderilir.

---

## Derleme ve Test

```bash
mvn -pl plugin -am -DskipTests package
```

Çıktı jar: `plugin/target/AetherAuth-1.0.0.jar`

Testler:

```bash
mvn -pl plugin -am test
```

Mevcut test paketi: **48 test**.

---

### Kayıt GUI

<div align="center">
  <img src="register.png" alt="Kayıt GUI" width="900">
</div>

---

## Lisans

Bu proje **MIT License** ile yayınlanmaktadır.
