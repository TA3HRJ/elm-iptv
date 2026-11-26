# Contributing to ELM IPTV / Katkı

## English

Thanks for helping. Bug reports, translations and fixes are welcome.

**Reporting a bug.** Open an issue with the app version (About screen), the device (e.g. Fire TV Stick 4K, Fire OS
8) and the steps. If the app closed or slowed down, attach `errors.txt` (and `memory.txt` for long-run problems):
Settings → Error log shows an address to open on a phone on the same network. **Never post playlist addresses,
user names or passwords**; remove them from logs and screenshots.

**Not accepted:** playlists, channel lists, links to streams, or features meant to get around a provider's or
another app's access controls. ELM IPTV is a player only.

**Pull requests.**

- Keep a change focused on one thing; describe what it changes for the user.
- Run `./gradlew :core:test :app:testDebugUnitTest :app:lintRelease` before sending. Add a test when you fix a bug
  that a test can catch.
- Database changes need a Room migration, the exported schema in `app/schemas/` and a case in `MigrationTest`.
- User-visible texts come in Turkish and English (`tx("…", "…")`).
- The app is used with a remote control: every screen must be reachable and usable with the D-pad, OK and Back.

**Sign-off (DCO).** Every commit must carry a `Signed-off-by: Your Name <email>` line (`git commit -s`). With it you
confirm the [Developer Certificate of Origin 1.1](https://developercertificate.org/): you wrote the change or have
the right to submit it under the project's license (GPL-3.0-or-later).

## Türkçe

Katkınız için teşekkürler. Hata bildirimleri, çeviriler ve düzeltmeler memnuniyetle karşılanır.

**Hata bildirmek.** Uygulama sürümünü (Hakkında ekranı), cihazı (ör. Fire TV Stick 4K, Fire OS 8) ve adımları
yazarak bir "issue" açın. Uygulama kapandıysa ya da yavaşladıysa `errors.txt` (uzun süreli sorunlarda `memory.txt`)
ekleyin: Ayarlar → Hata günlüğü ekranındaki adresi aynı ağdaki telefonda açın. **Liste adreslerini, kullanıcı
adlarını ve şifreleri asla paylaşmayın**; günlük ve ekran görüntülerinden silin.

**Kabul edilmeyenler:** oynatma listeleri, kanal listeleri, yayın bağlantıları ya da bir sağlayıcının veya başka
bir uygulamanın erişim denetimlerini aşmaya yönelik özellikler. ELM IPTV yalnızca bir oynatıcıdır.

**Değişiklik önerileri (pull request).**

- Her değişiklik tek bir konuya odaklansın; kullanıcı için neyi değiştirdiğini anlatın.
- Göndermeden önce `./gradlew :core:test :app:testDebugUnitTest :app:lintRelease` çalıştırın. Testle
  yakalanabilecek bir hatayı düzeltiyorsanız test ekleyin.
- Veritabanı değişiklikleri Room geçişi, `app/schemas/` altındaki şema ve `MigrationTest` içinde bir durum ister.
- Kullanıcıya görünen metinler Türkçe ve İngilizce yazılır (`tx("…", "…")`).
- Uygulama kumandayla kullanılır: her ekran yön tuşları, OK ve Geri ile ulaşılabilir ve kullanılabilir olmalı.

**Onay satırı (DCO).** Her commit'te `Signed-off-by: Adınız <e-posta>` satırı olmalı (`git commit -s`). Bununla
[Developer Certificate of Origin 1.1](https://developercertificate.org/) beyanını onaylarsınız: değişikliği siz
yazdınız ya da projenin lisansıyla (GPL-3.0-or-later) gönderme hakkınız var.
