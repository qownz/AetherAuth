# AetherAuth v1.0.0

İlk stabil sürüm. Paper/Spigot sunucular için modern, hafif ve güvenli kimlik doğrulama eklentisi.

## Neler Var?

- **Paper 1.21.6+ dialog arayüzü** — görsel kayıt, giriş, şifre ve e-posta değişiklik ekranları.
- **Komut fallback modu** — Paper/Spigot 1.13 – 1.21.5 için `/login` ve `/register` tabanlı akış.
- **Şifreleme** — şifreler PBKDF2-HMAC-SHA512 ile hashlenir, e-posta adresleri AES-256/GCM ile şifrelenir.
- **SQLite depolama** — otomatik yedekleme, bağlantı havuzu, WAL modu ve asenkron I/O.
- **Kısa süreli kısıtlamalar** — IP başına hesap limiti (varsayılan 3), hatalı giriş denemesi limiti, hesap kilitleme, oturum süresi ve IP bağlama.
- **Türkçe ve tamamen özelleştirilebilir mesajlar** — `messages.yml` içinde MiniMessage ve `&` renk kodları desteği.
- **Yönetici komutları** — `info`, `stats`, `unregister`, `resetpassword`, `resetmail`, `backup`, `reload`.
- **Güncelleme kontrolü** — GitHub üzerinden otomatik sürüm takibi.

## Gereksinimler

- **Sunucu:** Paper 1.21.6+ (dialog) veya Paper/Spigot 1.13+ (komut modu).
- **Java:** Dialog için Java 21, komut modu için Java 8+.
- **Oyuncu istemcisi:** Dialog ekranı için Minecraft 1.21.6+.

## Kurulum

1. `AetherAuth-1.0.0.jar` dosyasını sunucunun `plugins/` klasörüne kopyalayın.
2. Sunucuyu başlatın — `plugins/AetherAuth/` klasörü otomatik oluşur.
3. `config.yml` ve `messages.yml` dosyalarını ihtiyacınıza göre düzenleyin.
4. Ayar değişikliklerinden sonra `/aetherauth reload` kullanın.

> `database/secret.key` dosyasını mutlaka yedekleyin. Bu dosya kaybolursa mevcut hesaplar kurtarılamaz.

## Doğrulama

```bash
sha256sum AetherAuth-1.0.0.jar
```

Beklenen değer:

```
b1dfcf88b88030526a3566adfb527673cb354ccd345b3f882bdac2ed8cda2303
```

## Tam changelog

https://github.com/qownz/AetherAuth/blob/main/README.md
