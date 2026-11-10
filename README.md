# Smart Panel

Panel aplikasi melayang di tepi layar untuk Android 7 sampai 15 (ARM 32-bit dan 64-bit). Dibuat oleh Nexora.

## Cara dapat APK

### Opsi A: lewat GitHub Actions
1. Upload **seluruh isi folder ini** ke repo GitHub (termasuk folder `.github`).
2. Tab **Actions** > **Build APK** > **Run workflow**.
3. Download artifact **SmartPanel-APK**. Isinya `app-release.apk` dan `app-debug.apk`.

### Opsi B: Android Studio
File > Open > pilih folder ini > Build > Build APK(s).

## Cara pakai
1. Buka aplikasi dan beri izin (wajib: Tampil di atas aplikasi lain), lalu nyalakan panel.
2. Garis kecil muncul di tepi layar. Geser naik-turun untuk pindah posisi, geser ke dalam untuk buka panel.
3. Di panel, tekan dan geser garis paling atas untuk pindah ke kiri atau kanan.
4. Tombol **+** di panel membuka daftar aplikasi mengambang di samping panel, tanpa keluar dari aplikasi yang sedang dipakai. Scroll atas-bawah, ikon di tengah paling besar. Ketuk ikon untuk menambah atau melepas. Ketuk centang atau di luar daftar untuk menutup.
5. Lebar panel bisa diisi angka (dp). Kosong berarti otomatis mengikuti DPI.

## Mode buka aplikasi
- **Auto**: ketuk ikon = layar penuh, ketuk logo freeform kecil di ikon = mengambang.
- **Layar penuh**: ketuk ikon = layar penuh.
- **Freeform**: ketuk ikon = langsung mengambang. Hanya aktif kalau HP mendukung freeform.

## Kill seperti Recents (butuh Shizuku)
Android tidak mengizinkan aplikasi biasa menutup aplikasi lain yang masih tampil. Tombol x di panel memakai **Shizuku** (gratis) untuk menghapus task aplikasi dari Recents lalu mematikan prosesnya.
1. Pasang Shizuku dari Play Store.
2. Jalankan lewat Debugging nirkabel (Android 11 ke atas). Android 7 sampai 10 harus lewat komputer (adb).
3. Di Smart Panel, bagian "Tutup aplikasi", tekan Izinkan.

Tanpa Shizuku, tombol x hanya bisa mematikan aplikasi yang sudah di latar belakang.

## Otomatis
- **Jalan saat HP menyala**: panel hidup sendiri setelah restart.
- **Hidupkan lagi kalau dimatikan**: kalau panel ke-kill atau dihapus dari Recents, nyala lagi sendiri.
- Di HP Tecno, Infinix, itel, Xiaomi, Oppo, Vivo, izinkan juga "Mulai otomatis" / "Autostart" di pengaturan HP.

## Aktifkan freeform (HP yang belum support)
1. Unduh Brevent dari Play Store.
2. Hubungkan lewat debugging nirkabel.
3. Di Brevent, buka menu titik tiga, cari menu untuk menjalankan perintah, lalu masukkan:
```
settings put global enable_freeform_support 1
settings put global force_resizable_activities 1
```
4. Restart HP.

Kalau setelah itu freeform tetap tidak aktif, HP tidak mendukungnya dan mode Freeform tidak bisa dipakai.

## Catatan teknis
- minSdk 24 (Android 7.0), targetSdk 35 (Android 15). Aplikasi murni Java/Kotlin tanpa library native, jadi jalan di ABI ARM mana pun (`armeabi-v7a`, `arm64-v8a`). `armeb` bukan ABI Android.
- Tema hanya hitam dan putih, bisa ikut mode gelap HP atau dikunci.
