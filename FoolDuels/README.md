# FoolDuels

Bahisli düello, parti düellosu (2v2, 3v3, 4v4), turnuva ve ELO eklentisi. Paper 1.21+ / Java 21.
Duels ve BattleArena'nın sağlam taraflarından (tek zamanlayıcı, zorla ışınlama, kit eşyası koruması,
binary envanter yedekleri) esinlenerek yazıldı.

## Derleme

```bash
cd FoolDuels
mvn package
# target/FoolDuels-1.0.0.jar
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
| `/turnuva katil/ayril/bilgi` | Turnuva |

## Yönetici komutları

| Komut | Açıklama |
|---|---|
| `/turnuva olustur <ücret> <kit\|kendi>` | Kayıtları açar (ücret havuza girer) |
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
