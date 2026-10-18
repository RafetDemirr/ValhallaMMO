# FoolDuels

Bahisli düello, parti düellosu (2v2, 3v3, 4v4), turnuva ve ELO eklentisi. Paper 1.21+ / Java 21.
Duels ve BattleArena'nın sağlam taraflarından (tek zamanlayıcı, zorla ışınlama, kit eşyası koruması,
binary envanter yedekleri) esinlenerek yazıldı.

## Derleme

```bash
cd FoolDuels
mvn package
# target/FoolDuels-1.2.0.jar
```

İsteğe bağlı bağımlılıklar: **Vault** (+ EssentialsX gibi bir ekonomi) bahis ve turnuva ücreti için,
**CombatLogX** entegrasyonu için.

## Kurulum

1. Arena oluştur: `/duelyonet arena olustur arena1`
2. Takım spawnlarını ekle (2v2 için her takıma 2 spawn):
   `/duelyonet arena spawn arena1 1` ve `/duelyonet arena spawn arena1 2`
3. Koruma bölgesi: köşelerde `/duelyonet arena pos1 arena1` ve `/duelyonet arena pos2 arena1`
4. Kit: envanterini hazırla, ikon olacak eşyayı eline al, `/duelyonet kit olustur nodebuff`

## Oyuncu komutları

| Komut | Açıklama |
|---|---|
| `/duel <oyuncu>` | Menü: bahis, kit / kendi envanter, gönder |
| `/duel kabul [oyuncu]`, `/duel red [oyuncu]`, `/duel iptal` | İstekler |
| `/duel elo [oyuncu]`, `/duel top`, `/duel toggle` | ELO, sıralama, istekleri aç/kapat |
| `/parti davet/kabul/ayril/at/dagit/liste` | Parti; lider `/duel` atınca takım düellosu olur |
| `/duel sira <kit\|kendi>` | ELO'ya göre eşleşen 1v1 sıra (lobide beklenir) |
| `/duel izle` | Bekleme/izleme lobisine git |
| `/duel ayril` | Sıradan ve lobiden çık, geldiğin yere dön |
| `/turnuva katil/ayril/bilgi` | Turnuva |
| `/turnuva bahis <oyuncu> <miktar>` | İzleyici bahsi (turnuva maçının geri sayımında) |

## Yönetici komutları

| Komut | Açıklama |
|---|---|
| `/turnuva olustur <preset>` | config.yml'deki hazır turnuvayı açar (1./2./3./katılım ödül komutları) |
| `/turnuva olustur <ücret> <kit\|kendi>` | Kayıtları açar (ücret havuza girer) |
| `/duelyonet lobi` | Bekleme/izleme lobisini ayarlar (arena bölgesinin dışında olmalı) |
| `/turnuva baslat`, `/turnuva iptal`, `/turnuva oduleekle <miktar>` | Başlat, iptal (ücret iadesi), ödül ekle |
| `/duelyonet arena ac/kapat <ad>` | Arenayı etkinlik için kilitle |
| `/duelyonet yedek <oyuncu>`, `/duelyonet geriyukle <oyuncu> <no>` | Düello öncesi yedekler |
| `/duelyonet elo <oyuncu> <değer>`, `/duelyonet reload` | |

## Güvenlik

- **Envanter / XP:** Maç başlamadan önce oyuncunun tüm durumu (envanter, zırh, offhand, XP seviyesi ve
  ilerlemesi, can, açlık, efektler, oyun modu, uçuş, konum) diske yazılır (`pending/`) ve yedeklenir
  (`backups/`, en fazla `backups.max-per-player`). Kitli maçta her şey geri yüklenir. Kendi envanter
  maçında eşyalar oyuncuda kalır, yalnızca maçta tüketilenler gider; XP, can vb. yine geri yüklenir.
- **Çıkış / kick:** Oyuncu maçtan elenir ama eşyaları kaybolmaz; tekrar girince geri yüklenir.
- **Sunucu çökmesi:** Bekleyen geri yüklemeler oyuncu girince uygulanır, emanetteki bahis ve ücretler
  (`escrow.yml`) açılışta otomatik iade edilir.
- **Komutlar:** Maç sırasında `command-whitelist` dışındaki tüm komutlar engellenir (`/ah`, `/spawn`, `/tpa`...).
  `essentials:ah` gibi yazımlar da engellenir.
- **CombatLogX:** Maçtaki oyuncular tag'lenmez ve çıkışta CombatLogX cezası (ölüm/eşya düşürme) uygulanmaz.
  Tag'li oyuncu düelloya giremez.
- **Arena:** Bölgede blok kırma/koyma ve patlama engellenir, dışarıdakiler aktif arenaya giremez,
  maçtakiler dışarı çıkamaz ve ışınlanamaz (ender incisi/chorus hariç).
- **Kit eşyaları** işaretlenir; maç dışında bulunursa silinir.

- **Arenaya geri dönüş yok:** `/back`, `/home`, `/warp`, `/tpa` ve konsol `tp` ile maçta olmayan biri arena
  bölgesine ışınlanamaz; arena içinde `/sethome`, `/setwarp` vb. (`protection.blocked-commands-in-arena`)
  kullanılamaz; arenadaki yatak/spawn noktası yok sayılır.
- **WorldGuard:** Arena bölgesinde `pvp deny` olsa bile düellodaki rakipler birbirine vurabilir ve
  knockback yer (`protection.force-allow-pvp`). Maç dışındaki PvP kuralları değişmez.
- **İstek spamı:** Aynı oyuncuya tekrar istek için `duel.request-cooldown` (15 sn), reddedildikten sonra
  `duel.deny-cooldown` (60 sn) beklenir. Kabul edilen istek beklemeyi sıfırlar (rövanş hemen atılabilir).
  `foolduels.bypass` bu süreyi atlar.
- **Bahis yazma:** Menüden bahis miktarı sohbete yazılınca mesaj kimseye gitmez (EssentialsXChat dahil).

## Menü

`/duel <oyuncu>`: iki oyuncunun ELO/galibiyet bilgisi, bahis (±10/100/1000 veya altın bloğa tıklayıp
sohbete yaz; vergi ve net kazanç gösterilir), kit seçimi (içerik önizlemeli), boştaki arenalar ve aktif
maçlar, sıradaki oyuncu sayıları (tıklayınca seçili kitle sıraya girer) ve kurallar.

## Bahis vergisi

`bet.tax-percent` varsayılan vergidir. Yetkiye göre daha düşük oran (en düşüğü geçerli):

```yaml
bet:
  tax-percent: 5
  tax-permissions:
    vergisiz:
      permission: foolduels.tax.bypass   # kazanan vergi ödemez
      tax: 0
    vip:
      permission: foolduels.tax.vip
      tax: 2
```

`/lp user <oyuncu> permission set foolduels.tax.bypass true`

Eski bir config.yml ile güncellediğinde yeni ayarlar ve mesajlar dosyaya otomatik eklenir; senin
değerlerin ve presetlerin değişmez.

## Turnuva presetleri

```yaml
tournament:
  presets:
    haftasonu:
      display-name: "<gold><bold>Haftasonu Turnuvası"
      fee: 0
      kit: kendi
      announce: ["<yellow>1.</yellow> Efsanevi Anahtar x3"]
      rewards:
        first: ["crates key give %player% efsanevi 3"]
        second: ["crates key give %player% efsanevi 2"]
        third: ["crates key give %player% efsanevi 1"]   # yarı finalde kaybedenler
        participation: ["crates key give %player% normal 1"]
```

Komutlar konsoldan çalışır; oyuncu çevrimdışıysa `rewards.yml`'ye kaydedilir ve girişte verilir.

## İzleyici bahisi

Turnuva maçlarının geri sayımı (`tournament.countdown`) boyunca sohbette tıklanabilir bahis mesajı çıkar.
Kazanan tarafa bahis yapanlar havuzu (vergi düşülerek) yatırdıkları oranda paylaşır. Turnuva oyuncuları
bahis yapamaz. Para emanette tutulur, iptal/berabere/çökme durumunda iade edilir.

## PlaceholderAPI

| Placeholder | |
|---|---|
| `%foolduels_elo%`, `%foolduels_wins%`, `%foolduels_losses%`, `%foolduels_winrate%`, `%foolduels_rank%` | Oyuncu |
| `%foolduels_top_name_<n>%`, `%foolduels_top_elo_<n>%`, `%foolduels_top_wins_<n>%`, `%foolduels_top_losses_<n>%` | Liderlik tablosu (n = 1, 2, 3...) |
| `%foolduels_in_match%`, `%foolduels_queue%`, `%foolduels_queue_size%` | Durum |
| `%foolduels_tournament_state%`, `%foolduels_tournament_players%`, `%foolduels_tournament_pot%` | Turnuva |

Sıralama 10 saniyede bir yeniden hesaplanır (hologramlar sunucuyu yormaz).
