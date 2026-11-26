# ELM IPTV

## In English

**ELM IPTV** (*Every Live Moment*) is a free, open-source IPTV player for **Fire TV** (Fire OS 6–8) and
**Android TV / Google TV** (Android 6+), built for the remote control. The same app also runs on
**Android phones and tablets**, and there are versions for **Windows** and **Linux**. It is only a
player: it provides and sells no channels, films or series. You add your own provider's
**Xtream Codes** login or **M3U** link.

- Live TV with categories, favorites and your own lists, a TV guide (XMLTV), catch-up and recording
- Films and series with continue watching; profiles, a kids profile and a parental PIN
- Backups (optionally with a password) and a phone page on the home network for restoring them
- No analytics, ads or accounts: everything stays on the device
- Turkish and English

**Install:** download `ELM-IPTV-x.y.z.apk` from [Releases](../../releases/latest), for example with the
Downloader app on Fire TV. Only APKs from this page, signed with the developer's key, are official; once
installed, the app tells you about new versions and installs them itself. Windows (`.msi`) and Linux
(`.AppImage`) builds are on the same page; they play through the computer's own VLC 3, which must be
installed.

**Support:** ELM IPTV is free and has no ads. If you would like to support it:
[GitHub Sponsors](https://github.com/sponsors/TA3HRJ) (also in the app under About).

**License:** GPL-3.0-or-later ([LICENSE](LICENSE)). The ELM IPTV name and logo are not covered by it
([TRADEMARKS.md](TRADEMARKS.md)). Contributions: [CONTRIBUTING.md](CONTRIBUTING.md). Security reports:
[SECURITY.md](SECURITY.md).

Fire TV is a trademark of Amazon.com, Inc. or its affiliates; Android TV and Google TV are trademarks of
Google LLC. ELM IPTV is not affiliated with or endorsed by them.

---

## Türkçe

**E**very **L**ive **M**oment — Fire TV ve Android TV için kumandayla kullanılan bir IPTV oynatıcı.
Aynı APK Android telefon ve tabletlerde dokunmatik çalışır; Windows ve Linux için masaüstü sürümü de vardır.

ELM IPTV yalnızca bir oynatıcıdır; içerik sağlamaz ve satmaz. Kendi IPTV sağlayıcınızın
Xtream Codes bilgilerini veya M3U bağlantısını kullanırsınız.

> Fire TV, Amazon.com, Inc. veya iştiraklerinin ticari markasıdır. ELM IPTV, Amazon ile bağlantılı
> değildir ve Amazon tarafından onaylanmamıştır.

## Özellikler (1.0)

- **Hesap düzenleme:** Ayarlar → Hesaplar ve listeler → Düzenle: sunucu, kullanıcı adı, şifre, M3U adresi değiştirilebilir;
  favoriler, listeler, son izlenenler, hatırlatıcılar ve dizi kayıtları yeni bilgilere taşınır
- **Konum çubukları:** kategori ve kanal listelerinin, rehberin ve oynatıcıdaki kanal listesinin sağında
- **Hakkında:** ana ekrandaki ELM IPTV logosu; **Çıkış:** ana ekranda Geri (onaylı) veya Ayarlar → Çıkış
- **Favori sırası:** favoriler ve kendi listeleriniz kendi sıranızda durur; kanalda Menü (☰) veya OK'a basılı tut →
  Sırayı değiştir, ▲ ▼ ile taşı, OK veya Geri ile bitir (Ayarlar'daki kanal sırası diğer listeler için geçerli)
- **Rehber izlenen kanalda açılır:** TV Rehberi son izlenen kanalın satırında (çevresindeki kanallarla) açılır, oynatıcıdan
  dönünce yeni kanala gider; ⏪ ⏩ (veya CH± / sayfa tuşları) bir kategori atlar, rakam tuşları kanal numarasına gider
- **Kumandaya uygun metin kutuları:** kutunun üzerine gelmek onu yalnızca seçer ("OK: yaz"); klavye OK ile açılır,
  klavyedeki İleri / Tamam ya da Geri ile kapanır
- **Yazı ve öğe boyutu:** Kompakt / Normal / Büyük / Çok büyük (Ayarlar → Dil ve görünüm); varsayılan daha sıkı. Yeni boyut 15 sn içinde onaylanmazsa eskisine döner; büyük boyutta başlık düğmeleri simgeye küçülür ve Ayarlar hep ekranda kalır
  görünümde ekrana daha çok satır sığar
- **Profiller:** her profilin kendi favorileri, listeleri, son izlenenleri, izleme listesi ve kaldığı yerleri; açılışta
  "Kim izliyor?" (Ayarlar → Profiller'den kapatılabilir), başlıkta profil düğmesi
- **Çocuk profili:** kilitli kategorileri hiç görmez (PIN girilse bile), yalnızca seçilen canlı / film / dizi
  kategorilerini görür (çocuklara uygun görünenler önceden işaretlenir); çocuk profilinden çıkmak ve Ayarlar PIN ister
- **Dizi kaydı:** rehberde programın üzerinde Menü (☰) → Dizi kaydı: aynı kanaldaki tüm bölümler rehber güncellendikçe
  kendiliğinden planlanır; Kayıtlar ekranından kapatılır
- **Harici oynatıcı:** kanal veya film Menü (☰) → Harici oynatıcıda aç (VLC, MX Player…; User-Agent birlikte gider)
- **Çoklu ekranda ses seçimi:** Menü (☰) → Sesi bu pencereye sabitle; sesli pencerede 🔊 işareti
- **Xtream Codes** girişi (sunucu, kullanıcı adı, şifre); abonelik bitiş tarihi gösterimi
- **M3U / M3U8** bağlantısı (`tvg-id`, `tvg-logo`, `group-title`, `#EXTGRP`, `#EXTVLCOPT:http-user-agent`)
- Birden fazla liste; listeler arasında geçiş, yenileme, silme
- Kategoriler, kanal logoları, kanal arama
- Favoriler (kumandada **Menü ☰** tuşu veya **OK'a basılı tutma**); liste yenilense de korunur
- Tam ekran oynatma (Media3/ExoPlayer): MPEG-TS ve HLS
- Oynatırken **yukarı/aşağı** veya **Kanal +/-** ile kanal değiştirme
- **İzlerken kanal bilgisi ve kanal listesi:** oynatırken ilk **OK** kanal bilgisini (ad, FHD/Stereo, program) gösterir,
  bilgi açıkken **OK** soldan kanal listesini açar (şu anki programlar, ◀ ▶ ile kategori, OK ile geçiş). Bilgi ayrıca
  Info tuşunda, Menü (☰) → Kanal bilgisi'nde ve her kanal değişiminde
- Bağlantı koparsa otomatik yeniden bağlanma (5 deneme); hata mesajında sunucunun HTTP kodu
- **Kanal numaraları** (`tvg-chno` / Xtream `num`, yoksa liste sırası); oynatırken kumandanın rakam tuşlarıyla kanal seçimi
- **Açılışta son izlenen kanaldan devam**, izlendiği listede (favoriler, kendi listeleriniz, son izlenenler ya da kategori):
  kanal değiştirme ve kanal listesi o listede sürer, Geri ana ekranda o listeye döner (Ayarlar'dan kapatılabilir)
- **Ebeveyn kilidi:** 4 haneli PIN; canlı TV, film ve dizi kategorileri kilitlenebilir. PIN girilince uygulama
  kapanana kadar (veya "Şimdi kilitle"ye kadar) açık kalır. Kilitli kategorilerin kanalları aramada ve favorilerde de görünmez
- **Gizleme:** kanal ve kategori gizleme; Ayarlar → Gizlenenler'den geri açılır
- Kanal ve kategori seçenekleri: üzerindeyken **Menü (☰)** veya **OK'a basılı tut**
- **Dil:** Türkçe / English (Ayarlar → Dil; varsayılan cihaz dili)
- **TV rehberi tüm kategorileri art arda gösterir**; programın üzerinde Menü (☰): canlı izle, arşivden izle,
  hatırlatıcı, kaydet
- **Önizlemeli rehber:** Ayarlar'dan açılış ekranı "TV rehberi" seçilirse odaktaki kanalın küçük canlı önizlemesi
- **Kayıt:** oynatırken Menü (☰) → Kaydet (15–180 dk) veya rehberden program kaydı; en fazla 3 saat; en çok boş
  alanı olan depolamaya (takılıysa USB) `.ts` olarak; Kayıtlar ekranından oynatma/silme
- **Canlı yayını duraklatma ve geri sarma:** Oynat/Duraklat; sol/sağ 10 sn (2 dakikaya kadar geri). Uzun duraklatmada
  geriye dönük izlemesi olan kanallar arşivden devam eder
- **Ses, altyazı, görüntü:** oynatırken Menü (☰) → ses dili, altyazı, Sığdır / Doldur / Uzat
- **Çoklu ekran:** 4 kanala kadar (her pencere ayrı bağlantı; sağlayıcı izin verdiği sürece). Menü (☰) → Çoklu ekran
- **Genel arama:** kanal, film ve dizi tek ekranda
- **Saat:** ana ekran, TV rehberi ve bölümler ekranının başlığında saat ve tarih (cihazın 12/24 saat ayarına göre)
- **Arşiv:** kategori listesinde "⟲ Arşiv": yalnızca geriye dönük izlemesi olan kanallar; kanal seçince geçmiş
  programları açılır. Bölümler açılış ekranında da Arşiv kartı
- **Rehberi elle eşleştirme:** rehberi olmayan ya da yanlış rehber gösteren kanalda Menü (☰) → Rehberi eşleştir…
  (ana ekran veya TV Rehberi): rehberin kanalları benzer adlar önde listelenir, aranabilir; "Rehber yok" ve
  "Otomatik eşleştirmeye dön" seçenekleri; seçim rehber güncellemelerinde ve yedekte korunur
- **Parolalı yedek:** Yedekleme → "Parolayla yedekle…": dosya AES-256 ile şifrelenir, geri yüklerken (dosya, adres veya
  telefon sayfası) parola sorulur
- **Hata günlüğünü telefondan indirme:** Hata günlüğü ve Yedekleme ekranındaki adresten `errors.txt`
- **Ses gecikmesi (dudak senkronu):** oynatırken Menü (☰) → Ses gecikmesi, ±500 ms; tüm kanallar için saklanır
- **Büyük listelerde düşük bellek:** Xtream kanal, film ve dizi listeleri parça parça okunur (on binlerce içerik)
- **Türkçe uyumlu arama:** büyük/küçük harf ve ç ğ ı İ ö ş ü fark etmez ("cocuk" → "ÇOCUK", "istanbul" → "İSTANBUL");
  kanal, film/dizi ve program aramalarında
- **Arka planda günlük güncelleme:** uygulama açılmasa da kanallar ve rehber günde bir kez güncellenir, dizi kayıtları yeni
  bölümleri yakalar (Ayarlar → Genel ve görünüm'den kapatılabilir)
- **PIN deneme sınırı:** 5 yanlış denemeden sonra 30 sn bekleme, her yanlışta iki katına (en çok 15 dk)
- **Kopan yayına yeniden bağlanma:** kısa denemelerden sonra artan aralıklarla (en çok 60 sn) denemeye devam eder,
  ağ geri gelince hemen bağlanır
- **Telefondan yedek aktarımı:** Yedekleme ekranı açıkken gösterilen adres (ör. `http://192.168.1.20:8090/?k=…`) aynı ev ağındaki
  telefon/bilgisayar tarayıcısında açılır; yedek dosyası TV'ye gönderilir ya da TV'deki yedek indirilir (dosya seçicisi olmayan
  Fire TV için). Adres rastgele bir anahtar içerir ve yalnızca ekran açıkken çalışır. Yedekler silinebilir; en yeni 10 yedek tutulur
- **Hesap bilgisi:** Hesaplar ve listeler → Hesap: durum, bitiş ve oluşturulma tarihi, en fazla bağlantı, sağlayıcı mesajı;
  çoklu ekran bağlantı sınırını aşacak pencere eklenirken uyarır
- **Bölümlü Ayarlar:** solda Hesaplar ve sistem / Genel ve görünüm / Oynatma ve görüntü / Ebeveyn kilidi / Gizlenenler
- **Film ve dizi sıralaması:** liste sırası, ad, puan, yıl; afişlerde yıl ve puan
- **Yenile düğmesi:** başlıkta, son güncellemeden bu yana geçen süreyle ("5 dk önce")
- **Bölümler açılış ekranı** (isteğe bağlı): Canlı TV, TV Rehberi, Filmler, Diziler kartları ve sayıları
- **Yedekleme / geri yükleme:** listeler, favoriler, kilit/gizleme, PIN ve ayarlar JSON dosyasına; dosyadan ya da
  web adresinden geri yükleme
- **İzlemeye devam et:** Android TV / Google TV ana ekranında (Fire TV bu satırı dışarıdan kurulan uygulamalara açmıyor)
- **Hakkında** ekranı
- **AC3 / E-AC3 / DTS / MP2 ses:** cihazın çözemediği biçimler için FFmpeg yazılım çözücü (önce donanım çözücü denenir)
- **Otomatik güncelleme:** uygulama açıkken kanallar, rehber, film ve diziler seçilen aralıkla (6 / 12 / 24 saat ya da
  kapalı) arka planda yenilenir; oynatıcı açıkken bağlantıyı paylaşmamak için beklenir
- **Sıralama:** kanallar liste sırası / numara / ada göre, kategoriler liste sırası / ada göre; liste, rehber ve
  oynatıcıdaki kanal geçişi aynı sırayı izler
- **Son izlenenler:** son 10 kanal listenin en başında; Menü (☰) → Geçmişi temizle
- **Kendi listeleriniz:** "Spor", "Haber" gibi istediğiniz kadar kanal listesi; kanalda Menü (☰) → Listelerim…;
  listede Menü (☰) → yeniden adlandır / sil; yedeğe dahil
- **Yetişkin içerik kilidi:** "+18", "XXX", "Adult", "Yetişkin" gibi kategoriler (canlı, film, dizi) otomatik
  kilitlenir; PIN yoksa gizlenir, PIN belirlenince kilitli görünür. Ayarlar'dan kapatılabilir (PIN ister)
- **Kanal arşivi:** geriye dönük izlemesi olan kanalda Menü (☰) → Kanal arşivi: geçmiş günlerin programları gün gün;
  Xtream hesaplarında arşiv listesi doğrudan sunucudan alınır (rehberden daha eskiye gider)
- **Program arama:** Ara ekranında program adı (en az 3 harf): yayındakini izle, ilerikine hatırlatıcı kur,
  geçmiştekini arşivden izle
- **Kare hızı eşleme:** TV'nin yenileme hızı yayına göre (25/50 fps → 50 Hz, film → 24 Hz); Ayarlar → Görüntü
- **İzlemeye devam et:** yarım kalan film, bölüm ve kayıtlar ana ekranın üstünde; OK devam eder, Menü (☰) satırdan kaldırır
- **İzleme listem:** film ve dizi ayrıntısında "İzleme listeme ekle"; Filmler / Diziler ekranında en üstte; yedeğe dahil
- **Baştan başlat:** arşivi olan kanalda oynatırken Menü (☰) → Baştan başlat; yayındaki program en başından oynar
- **"Orada mısınız?":** seçilen süre (2–6 saat) boyunca tuşa basılmazsa ekranın ortasında geri sayımla sorar; 2 dakika
  cevap yoksa yayını durdurur (bağlantı kapanır, TV ekran koruyucuya geçebilir) ama oynatıcıda kalır ve nedenini
  yazar; bir tuş kaldığı yerden sürdürür, Geri çıkar
- **Tampon ayarı** (küçük / normal / büyük) ve **bağlantı testi** (sunucu yanıt süresi, indirme hızı ve öneri): Ayarlar → Oynatma
- **Hata günlüğü:** beklenmedik kapanmalar cihazda saklanır (son 10), Ayarlar → Hata günlüğü'nden okunur; bir sonraki
  açılışta haber verilir. Her sürümün `mapping-x.y.z.txt` dosyası Releases'ta (okunaklı hata kaydı için)
- **Uygulama içi güncelleme:** açılışta günde en fazla bir kez kendi güncelleme sunucumuzdaki `latest.json` okunur;
  "Şimdi güncelle" APK'yı indirir, SHA-256 özetini, aynı uygulama ve aynı imza anahtarıyla imzalanmış daha yeni bir
  sürüm olduğunu doğrulayıp sistem yükleyicisini açar. Ayarlar → Hesaplar ve sistem → Güncelleme. Sunucu adresi repoda
  değil: CI, `ELM_UPDATE_URL` değişkeninden derlemeye verir (yalnızca `https://`); onsuz derlenen sürüm güncelleme
  denetlemez. Yayın: CI, yeni sürümde APK ile `latest.json`'u `ELM_DEPLOY_*` sırlarıyla sunucuya yükler
- **Tanıtım sitesi:** `site/` (tek sayfa, TR/EN; sürüm ve indirme bağlantısı `latest.json`'dan okunur). CI, main'e
  her push'ta güncelleme sunucusuna yükler. Ekran görüntüleri uydurma kanal adlarıyla üretilir:
  `./gradlew :app:testDebugUnitTest --tests "*ShotTest" -Dshots=DIR -Ddemo=1 -Dsize=2 -Dlang=tr|en -Dscreen=home|settings`
- **Ana ekranda canlı önizleme ve yayın bilgisi:** kanal listesinin yanında seçili kanal küçük bir pencerede oynar;
  altında kanal, program, görüntü boyutu ve kare hızı (ör. 1920x1080 · 25 fps) ve bağlantı kalitesi (çubuklar) görünür.
  Geniş ekranda (Kompakt ve Normal boyut) çıkar; kayıt sürerken açılmaz; Ayarlar → Oynatma'dan kapatılır. Kanala OK ile
  geçince önizlemede çalan oynatıcı olduğu gibi tam ekran oynatıcıya devredilir: aynı bağlantı ve tampon, siyah ekran
  ya da yeniden yükleme yok (rehber açılış ekranındaki önizleme de aynı şekilde). Geri tuşuyla tam ekrandan dönerken de
  oynatıcı önizlemeye geri verilir: aynı kanalda yeniden bağlanmadan sürer. Uyku zamanlayıcısı oynatıcıyı kapatınca
  önizleme o kanalı yeniden başlatmaz
- **Ses profili ve çözücü bilgisi:** Ayarlar → Oynatma → Ses profili (Genel / Film): sesin TV ve cihaza içerik türü
  belirtilerek mi gönderileceğini seçer (bazı TV'ler "film" sesini farklı işler). Kanal bilgisinde (OK) ve ana ekran
  önizleme kutusunda "Görüntü: …" ve "Ses: …" satırları görüntü ve sesi kimin çözdüğünü gösterir: cihazın donanımı,
  Android'in yazılım çözücüsü, FFmpeg ya da TV'ye doğrudan çıkış (passthrough)
- **Film/dizi sıralaması:** liste, ad (A–Z / Z–A), puan, yıl (yeni / eski), eklenme tarihi (yeni / eski)
- **Altyazı listesi:** her parçada dil kodu, zorunlu (forced) ve işitme engelliler işareti
- **Bellek kaydı:** uygulama 5 dakikada bir bellek kullanımını not eder (son ~10 gün); uzun izlemede yavaşlama
  ya da kapanma olursa `memory.txt` telefondan indirilir. Java belleği %85'i aşarsa hata günlüğüne uyarı düşer
- **Önceki kanal:** oynatırken tek başına **0** tuşu, kumandadaki "son kanal" tuşu veya Menü (☰) → Önceki kanal
- **Uyku zamanlayıcısı:** Menü (☰) → 15–120 dk; kapanmadan 1 dakika önce uyarı, herhangi bir tuş iptal eder
- **Oynatma hızı** (film, bölüm, arşiv): 0,75x – 2x
- **Altyazı görünümü:** boyut, renk ve arka plan (Ayarlar, önizlemeli)
- **Rehber saat kaydırma:** liste başına ±12 saat, 30 dakikalık adımlarla (Listeler → Rehber)
- **Ayarları PIN ile koruma:** ebeveyn kilidi Ayarlar'dan kaldırılamasın diye
- **Görünüm:** ikonlu menüler, odakta büyüyen ve parlayan kartlar, açılış ekranı, yüklenirken iskelet görünüm,
  odaktaki kanal/afişten bulanık arka plan, logosu olmayan kanallarda renkli baş harf rozeti; oynatıcı bilgi
  çubuğunda logo, kalan süre ve 4K / FHD / HD, Dolby, 5.1, CC rozetleri
- **İlk açılış rehberi:** liste ekleme adımları, dil seçimi ve yedekten geri yükleme
- Nötr User-Agent (`ExoPlayer (Linux; Android)`: uygulama adı ve sürüm göndermez; tarayıcı başlığı kullanmaz çünkü birçok panel tarayıcıları engeller); liste başına değiştirilebilir
- **TV rehberi (EPG)**
  - Kaynak: Xtream'de `xmltv.php`, M3U'da `url-tvg` / `x-tvg-url` başlığı otomatik kullanılır; liste başına kendi
    XMLTV adres(ler)inizi de girebilirsiniz (`.xml` ve `.xml.gz`)
  - Kanal eşleştirme önce `tvg-id` ile, olmazsa kanal adıyla ("TR: ", "HD", "FHD" gibi ekler yok sayılır)
  - Kanal listesinde şu anki program ve ilerleme çubuğu
  - Oynatıcıda şimdi / sırada bilgisi
  - Saat çizelgeli **TV Rehberi** ekranı: 3 saatlik pencere, sağa/sola kenardan kaydırma, odaktaki programın açıklaması
  - 6 saat geriye (geriye dönük izlemeli listelerde 48 saat), 48 saat ileriye kadar saklanır; 12 saatten eskiyse arka planda yenilenir
- **M3U listelerindeki film ve diziler** de ayrı bölümde: adreste `/movie/` / `/series/` geçen ya da `.mp4`, `.mkv`
  gibi video dosyası olan kayıtlar canlı kanallardan ayrılır; "Ad S01 E02" biçimindeki bölümler dizi ve sezona göre gruplanır
- **Filmler ve diziler** (Xtream Codes): kategoriler, "son eklenenler", arama, afişler, film/dizi detayları,
  sezon ve bölümler; kaldığınız yerden devam etme; bölüm bitince 10 saniyelik sayaçla sonraki bölüme geçiş
  (OK: hemen başlat, Geri: kapat; sezon bitince sonraki sezona geçer)
- **Geriye dönük izleme (catch-up):** rehberde geçmiş programa OK → arşivden izlenir (⟲ işareti).
  Xtream `timeshift` (sunucu saat dilimiyle) ve M3U `catchup` türleri: `default`, `append`, `shift`, `flussonic`, `xc`
- **Program hatırlatıcısı:** rehberde gelecek programa OK veya Menü (☰) → ⏰ kurulur; başlamadan 1 dakika önce
  ekranda "İzle / Kapat" penceresi ve bildirim çıkar; cihaz yeniden başlasa da korunur
- Film, bölüm ve arşiv oynatıcısında ileri/geri sarma: sol −10 sn, sağ +30 sn; OK ile kontrol çubuğu

## İndirme

- **Sürümler:** depodaki **Releases** sayfasında imzalı `ELM-IPTV-x.y.z.apk`. Günlük kullanım için bunu kurun.
- **Geliştirme derlemeleri:** **Actions → Build → son çalıştırma → Artifacts → elm-iptv-apks**.
  İçindeki `-debug` APK "ELM IPTV (test)" adıyla ayrı bir uygulama olarak kurulur, asıl kurulumunuza dokunmaz.

## Desteklenen cihazlar

- **Çalışır:** Fire OS 6, 7 veya 8 kullanan Fire TV cihazları (Fire TV Stick 2. nesil ve sonrası, Stick Lite, Stick 4K,
  4K Max, Cube, Fire TV'li televizyonlar) ve Android 6.0 (API 23) veya üzeri Android TV / Google TV cihazları
  (Nvidia Shield, Chromecast with Google TV, TV kutuları, Android TV'li televizyonlar).
- **Çalışmaz:** Vega OS kullanan yeni Fire TV cihazları (Fire TV Stick 4K Select ve sonrası). Vega OS Android
  uygulamalarını çalıştırmaz ve dışarıdan uygulama kurmaya izin vermez; bu cihazlar için uygulamanın React Native ile
  yeniden yazılıp Amazon Appstore'a girmesi gerekir. Fire OS 5 kullanan 2014–2016 Fire TV'ler de desteklenmez
  (Media3 1.11 ve güncel AndroidX Android 6 istiyor).
- **Telefon ve tablet (aynı APK):** Android 6.0+ telefon ve tabletler. Düğmeler ve listeler dokunmayla çalışır
  (dokun: seç/aç, basılı tut: menü). Dar ekranda ana sayfa tek sütundur: önce kategoriler, dokununca kanallar
  (Geri veya "‹ kategori" ile dönülür). Oynatıcıda yukarı/aşağı kaydırma kanal değiştirir, sola/sağa kaydırma
  sarar veya panelleri açar, dokunmak OK, basılı tutmak veya çift dokunmak menüdür. Kanal sırası ▲ ▼ düğmeleriyle
  değiştirilir. Arayüz kumanda öncelikli kaldı; bazı ekranlar telefonda sıkışık görünebilir.
- **Windows ve Linux (masaüstü):** Windows 10/11 (64 bit) ve 64 bit Linux. Bilgisayarda **VLC 3** kurulu olmalı
  (yayınları VLC oynatır); yoksa uygulama indirme sayfasını gösterir. Ayrıntılar aşağıda.
- **Önerilen donanım:** 4K yayın için 4K destekli cihaz. AC3 / E-AC3 / DTS ses, cihaz çözemezse yazılımla çözülür.
  En düşük bellek henüz ölçülmedi (1 GB RAM'li Fire TV Stick Lite'ta denenmeli).
- Cihazın Fire OS mu Vega OS mu kullandığı: Fire TV Ayarlar → Cihazım (My Fire TV) → Hakkında.

## Windows ve Linux sürümü

`desktop/` modülü: Compose Multiplatform arayüzü, oynatma bilgisayardaki VLC ile (vlcj). Liste, rehber ve arşiv
kodu Android uygulamasıyla ortaktır (`core/`).

- **Kurulum:** Releases sayfasında (veya Actions → Build → Artifacts) `ELM-IPTV-X.Y.Z-windows-x64.msi`
  (yönetici izni istemez, kullanıcının klasörüne kurulur) ve `ELM-IPTV-X.Y.Z-x86_64.AppImage`
  (`chmod +x` ile çalıştırılır). Önce VLC 3: Windows'ta videolan.org, Linux'ta `sudo apt install vlc`
  (`dnf` / `pacman` da olur). Java ayrıca gerekmez, pakete dahildir.
- **Neler var:** M3U (adres veya yerel dosya) ve Xtream listeleri, kategoriler, kanal arama (Türkçe harf duyarsız),
  favoriler, son izlenenler, şimdi/sonra bilgisi, tam TV rehberi, arşivden izleme, film ve diziler (kaldığı yerden
  devam), ses ve altyazı seçimi, tam ekran, yetişkin kategorilerini gizleme, Türkçe/İngilizce.
- **Kısayollar:** Boşluk/K duraklat · F/F11 veya çift tık tam ekran · Esc çık · Yukarı/Aşağı, PgUp/PgDn kanal ·
  Sol/Sağ sar · Ctrl+Yukarı/Aşağı, +/−, tekerlek ses · M sessiz · L kanal listesi · Backspace önceki kanal ·
  F5/Ctrl+R listeyi yenile.
- **Veriler:** Windows'ta `%APPDATA%\ELM IPTV`, Linux'ta `~/.config/elm-iptv` (yalnızca kullanıcı okuyabilir).
  Android sürümüyle eşitlenmez.
- **Geliştirme:** `./gradlew :desktop:run` (VLC kurulu olmalı), testler `./gradlew :desktop:test`, paketler
  `./gradlew :desktop:packageMsi` (Windows) ve `:desktop:packageAppImage` + `desktop/packaging/make-appimage.sh`
  (Linux). Paketler yalnızca kendi işletim sisteminde derlenir; CI ikisini de yapar ve sürüm yayınlanınca
  Releases'e ekler. Güncelleme sunucusuna konmaz; uygulama içi güncelleme yalnızca Android içindir.
- **Lisans notu:** vlcj GPL-3.0 lisanslıdır; ELM IPTV de GPL-3.0-or-later olduğu için masaüstü paketleri bu
  lisansla dağıtılır. Android uygulaması vlcj içermez.

## Destek

ELM IPTV ücretsizdir ve reklam içermez. Desteklemek isterseniz: [GitHub Sponsors](https://github.com/sponsors/TA3HRJ)
(uygulamada Hakkında → Destek ol). Tamamen isteğe bağlıdır.

## Lisans

ELM IPTV, GNU Genel Kamu Lisansı 3.0 veya sonrası (GPL-3.0-or-later) ile açık kaynaktır (bkz. `LICENSE`).
"ELM IPTV" adı ve logosu bu lisansın kapsamında değildir (bkz. `TRADEMARKS.md`). Resmi sürümler yalnızca
bu deponun Releases sayfasında yayınlanır ve geliştiricinin anahtarıyla imzalıdır.

Üçüncü taraf bileşenler `THIRD_PARTY_NOTICES.md` dosyasında. FFmpeg ses çözücüleri LGPL-2.1-or-later;
yükümlülükler için `ffmpeg/README.md`.

Katkı: `CONTRIBUTING.md` · Güvenlik bildirimi: `SECURITY.md` · Sürüm geçmişi: `CHANGELOG.md` ·
İletişim: errhan@hotmail.com

## Fire TV'ye kurulum

1. **Ayarlar → My Fire TV → Geliştirici seçenekleri**: **ADB hata ayıklama** ve
   **Bilinmeyen kaynaklardan uygulamalar** açık olsun. (Menü görünmüyorsa
   **Ayarlar → My Fire TV → Hakkında** ekranında cihaz adına 7 kez basın.)
2. IP adresi: **Ayarlar → My Fire TV → Hakkında → Ağ**.
3. Bilgisayardan:

   ```bash
   adb connect FIRE_TV_IP:5555
   adb install -r ELM-IPTV-0.1.0.apk
   ```

   Fire TV'de çıkan "hata ayıklamaya izin ver" sorusunu onaylayın.

Alternatif: Fire TV'deki **Downloader** uygulamasına Releases sayfasındaki APK bağlantısını yazın.

## Proje yapısı

| Modül | İçerik |
|---|---|
| `core/` | Saf Kotlin: M3U ayrıştırıcı, Xtream Codes istemcisi, liste indirici ve birim testleri (`./gradlew :core:test`). |
| `app/src/test/` | Robolectric testleri (`./gradlew :app:testDebugUnitTest`): her eski veritabanı sürümünden geçiş, Türkçe arama, profil başına favoriler, çocuk profili, favori sırası, PIN deneme sınırı. |
| `app/` | Android uygulaması: Room veritabanı, Compose for TV ekranları, Media3 oynatıcı. |
| `desktop/` | Windows/Linux sürümü: Compose Multiplatform ekranları, VLC oynatıcı (vlcj), `core/` paylaşılır; testler `./gradlew :desktop:test`. |
| `app/schemas/` | Room veritabanı şemaları (sürüm başına). Yükseltmeler (`data/Migrations.kt`) bunlara göre yazılır; kullanıcı verisi korunur. |

Uygulama kimliği: `io.github.ta3hrj.elm` (debug: `io.github.ta3hrj.elm.debug`). Bu kimlik değiştirilmemelidir;
değişirse cihazlar uygulamayı yeni bir uygulama sayar ve kayıtlı listeler taşınmaz.

## İmzalama ve sürüm çıkarma

Android bir güncellemeyi yalnızca **aynı anahtarla** imzalanmışsa kabul eder. Sürüm APK'ları bu yüzden
GitHub Actions'ta, depo **Secrets** bölümünde saklanan kalıcı bir anahtarla imzalanır. Anahtar dosyası
depoya asla eklenmez.

| Secret | İçerik |
|---|---|
| `ELM_KEYSTORE_BASE64` | `elm-release.jks` dosyasının Base64 hali |
| `ELM_KEYSTORE_PASSWORD` | Anahtar deposu şifresi |
| `ELM_KEY_ALIAS` | `elm` |
| `ELM_KEY_PASSWORD` | Anahtar şifresi (PKCS12 depolarda depo şifresiyle aynı) |

**Anahtarın ve şifrenin yedeğini güvenli bir yerde saklayın.** Kaybolursa mevcut kurulumlar güncellenemez.

Yeni sürüm:

1. `app/build.gradle.kts` içinde `versionCode` değerini 1 artırın, `versionName` değerini güncelleyin (ör. `0.3.0`).
2. Değişikliği `main` dalına gönderin. Actions testleri çalıştırır, imzalı APK'yı derler ve bu sürüm daha önce
   yayınlanmamışsa `v0.3.0` etiketiyle **Releases** sayfasına ekler. (Elle `v*` etiketi göndermek de çalışır.)
   İmza bilgileri eksikse sürüm yayınlanmaz.

Yerelde imzalı derleme için aynı değişkenleri ortamda tanımlayın (`ELM_KEYSTORE_FILE` dosya yolunu gösterir)
ve `./gradlew :app:assembleRelease` çalıştırın. Değişkenler yoksa sürüm derlemesi test için debug anahtarıyla imzalanır.
